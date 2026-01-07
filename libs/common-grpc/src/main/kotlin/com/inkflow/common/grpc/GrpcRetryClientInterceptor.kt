package com.inkflow.common.grpc

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCallListener
import io.grpc.MethodDescriptor
import io.grpc.Metadata
import io.grpc.Status
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * gRPC 호출 재시도를 수행하는 클라이언트 인터셉터.
 */
class GrpcRetryClientInterceptor(
    private val policyProvider: GrpcRetryPolicyProvider,
    private val scheduler: ScheduledExecutorService = defaultScheduler()
) : ClientInterceptor, AutoCloseable {
    /**
     * 재시도 정책에 따라 래핑된 ClientCall을 반환한다.
     */
    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel
    ): ClientCall<ReqT, RespT> {
        val policy = policyProvider.resolve(method)
            ?: return next.newCall(method, callOptions)
        if (policy.maxAttempts <= 1) {
            return next.newCall(method, callOptions)
        }
        return RetryingClientCall(method, callOptions, next, policy, scheduler)
    }

    /**
     * 내부 스케줄러를 종료한다.
     */
    override fun close() {
        scheduler.shutdown()
    }

    private class RetryingClientCall<ReqT, RespT>(
        private val method: MethodDescriptor<ReqT, RespT>,
        private val callOptions: CallOptions,
        private val channel: Channel,
        private val policy: GrpcRetryPolicy,
        private val scheduler: ScheduledExecutorService
    ) : ClientCall<ReqT, RespT>() {
        private val lock = Any()
        private var delegate: ClientCall<ReqT, RespT>? = null
        private var responseListener: Listener<RespT>? = null
        private var headers: Metadata? = null
        private var attempt = 0
        private var pendingRequests = 0
        private var halfClosed = false
        private var cancelled = false
        private var retryDisabled = false
        private var responseReceived = false
        private var messageCount = 0
        private var message: ReqT? = null
        private var messageCompressionEnabled: Boolean? = null

        /**
         * 최초 호출을 시작하고 내부 상태를 초기화한다.
         */
        override fun start(responseListener: Listener<RespT>, headers: Metadata) {
            synchronized(lock) {
                if (this.responseListener != null) {
                    throw IllegalStateException("gRPC 재시도 호출은 한 번만 시작할 수 있습니다.")
                }
                this.responseListener = responseListener
                this.headers = headers
                startAttemptLocked()
            }
        }

        /**
         * 다음 응답 요청 수를 누적하고 delegate에 전달한다.
         */
        override fun request(numMessages: Int) {
            synchronized(lock) {
                pendingRequests += numMessages
                delegate?.request(numMessages)
            }
        }

        /**
         * 호출을 취소한다.
         */
        override fun cancel(message: String?, cause: Throwable?) {
            synchronized(lock) {
                cancelled = true
                delegate?.cancel(message, cause)
            }
        }

        /**
         * 메시지 전송을 종료한다.
         */
        override fun halfClose() {
            synchronized(lock) {
                halfClosed = true
                delegate?.halfClose()
            }
        }

        /**
         * 메시지를 전송하고 재시도 가능 여부를 판단한다.
         */
        override fun sendMessage(message: ReqT) {
            synchronized(lock) {
                messageCount += 1
                if (messageCount == 1) {
                    this.message = message
                } else {
                    // 스트리밍 요청은 재전송이 불가능하므로 재시도를 비활성화한다.
                    retryDisabled = true
                }
                delegate?.sendMessage(message)
            }
        }

        /**
         * 메시지 압축 여부를 설정한다.
         */
        override fun setMessageCompression(enabled: Boolean) {
            synchronized(lock) {
                messageCompressionEnabled = enabled
                delegate?.setMessageCompression(enabled)
            }
        }

        /**
         * delegate 준비 상태를 반환한다.
         */
        override fun isReady(): Boolean {
            synchronized(lock) {
                return delegate?.isReady ?: false
            }
        }

        /**
         * 재시도용 신규 ClientCall을 생성하고 시작한다.
         */
        private fun startAttemptLocked() {
            if (cancelled) {
                return
            }
            attempt += 1
            responseReceived = false

            val call = channel.newCall(method, callOptions)
            delegate = call
            messageCompressionEnabled?.let { call.setMessageCompression(it) }

            val listener = responseListener
                ?: throw IllegalStateException("responseListener가 초기화되지 않았습니다.")
            val metadata = headers ?: Metadata()

            call.start(RetryingClientCallListener(listener), metadata)

            if (pendingRequests > 0) {
                call.request(pendingRequests)
            }
            message?.let { call.sendMessage(it) }
            if (halfClosed) {
                call.halfClose()
            }
        }

        /**
         * 재시도 대상인지 확인한다.
         */
        private fun shouldRetry(status: Status): Boolean {
            if (cancelled) {
                return false
            }
            if (retryDisabled) {
                return false
            }
            if (responseReceived) {
                return false
            }
            if (!policy.isRetryable(status)) {
                return false
            }
            if (attempt >= policy.maxAttempts) {
                return false
            }
            return !isDeadlineExpired()
        }

        /**
         * 호출 데드라인이 만료됐는지 확인한다.
         */
        private fun isDeadlineExpired(): Boolean {
            val deadline = callOptions.deadline ?: return false
            return deadline.timeRemaining(TimeUnit.NANOSECONDS) <= 0
        }

        /**
         * 재시도를 스케줄링한다.
         */
        private fun scheduleRetry(status: Status, trailers: Metadata) {
            val retryCount = attempt
            val backoff = policy.backoffForRetry(retryCount)
            try {
                scheduler.schedule(
                    {
                        synchronized(lock) {
                            if (!cancelled) {
                                startAttemptLocked()
                            }
                        }
                    },
                    backoff.toMillis(),
                    TimeUnit.MILLISECONDS
                )
            } catch (ex: RejectedExecutionException) {
                // 스케줄러가 종료된 경우에는 실패를 즉시 반환한다.
                responseListener?.onClose(status, trailers)
            }
        }

        /**
         * 응답을 수신하고 재시도 여부를 판단하는 리스너다.
         */
        private inner class RetryingClientCallListener(
            private val delegateListener: Listener<RespT>
        ) : ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(delegateListener) {
            /**
             * 응답 헤더 수신 여부를 기록한다.
             */
            override fun onHeaders(headers: Metadata) {
                synchronized(lock) {
                    responseReceived = true
                }
                super.onHeaders(headers)
            }

            /**
             * 응답 메시지 수신 여부를 기록한다.
             */
            override fun onMessage(message: RespT) {
                synchronized(lock) {
                    responseReceived = true
                }
                super.onMessage(message)
            }

            /**
             * 종료 시 재시도 여부를 판단한다.
             */
            override fun onClose(status: Status, trailers: Metadata) {
                val shouldRetry = synchronized(lock) { shouldRetry(status) }
                if (shouldRetry) {
                    scheduleRetry(status, trailers)
                    return
                }
                delegateListener.onClose(status, trailers)
            }
        }
    }

    companion object {
        /**
         * 기본 스케줄러를 생성한다.
         */
        private fun defaultScheduler(): ScheduledExecutorService {
            val threadFactory = ThreadFactory { runnable ->
                Thread(runnable, "grpc-retry-scheduler").apply { isDaemon = true }
            }
            return ScheduledThreadPoolExecutor(1, threadFactory).apply {
                setRemoveOnCancelPolicy(true)
            }
        }
    }
}
