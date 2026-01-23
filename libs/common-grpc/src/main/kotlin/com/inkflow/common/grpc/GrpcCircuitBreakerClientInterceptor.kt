package com.inkflow.common.grpc

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.ForwardingClientCallListener
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * gRPC 호출에 서킷브레이커를 적용하는 클라이언트 인터셉터.
 */
class GrpcCircuitBreakerClientInterceptor(
    private val policyProvider: GrpcCircuitBreakerPolicyProvider,
    private val clock: Clock = Clock.systemUTC()
) : ClientInterceptor {
    private val breakers: MutableMap<String, GrpcCircuitBreaker> = ConcurrentHashMap()

    /**
     * 서킷브레이커 정책에 따라 호출을 제어한다.
     */
    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel
    ): ClientCall<ReqT, RespT> {
        val policy = policyProvider.resolve(method)
            ?: return next.newCall(method, callOptions)
        if (!policy.enabled) {
            return next.newCall(method, callOptions)
        }

        val breaker = breakers.getOrPut(method.fullMethodName) {
            // 메서드별 서킷브레이커 인스턴스를 생성한다.
            GrpcCircuitBreaker(method.fullMethodName, policy, clock)
        }

        if (!breaker.allowCall()) {
            // OPEN 상태이면 즉시 실패를 반환한다.
            return RejectedClientCall(Status.UNAVAILABLE.withDescription("circuit breaker open"))
        }

        val delegate = next.newCall(method, callOptions)
        return CircuitBreakingClientCall(delegate, breaker, policy.failureStatusCodes)
    }

    /**
     * 서킷브레이커 상태를 업데이트하는 ClientCall 래퍼.
     */
    private class CircuitBreakingClientCall<ReqT, RespT>(
        delegate: ClientCall<ReqT, RespT>,
        private val breaker: GrpcCircuitBreaker,
        private val failureStatusCodes: Set<Status.Code>
    ) : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(delegate) {
        /**
         * 호출 종료 시 성공/실패를 기록한다.
         */
        override fun start(responseListener: Listener<RespT>, headers: Metadata) {
            val listener = object :
                ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
                override fun onClose(status: Status, trailers: Metadata) {
                    if (status.code == Status.Code.OK || !failureStatusCodes.contains(status.code)) {
                        breaker.onSuccess()
                    } else {
                        breaker.onFailure(status)
                    }
                    super.onClose(status, trailers)
                }
            }
            super.start(listener, headers)
        }
    }

    /**
     * 서킷브레이커 OPEN 상태에서 즉시 실패를 반환하는 ClientCall.
     */
    private class RejectedClientCall<ReqT, RespT>(
        private val status: Status
    ) : ClientCall<ReqT, RespT>() {
        override fun start(responseListener: Listener<RespT>, headers: Metadata) {
            responseListener.onClose(status, Metadata())
        }

        override fun request(numMessages: Int) {
            // 호출 거부 상태이므로 요청을 무시한다.
        }

        override fun cancel(message: String?, cause: Throwable?) {
            // 호출 거부 상태이므로 취소 처리하지 않는다.
        }

        override fun halfClose() {
            // 호출 거부 상태이므로 전송 종료 처리를 생략한다.
        }

        override fun sendMessage(message: ReqT) {
            // 호출 거부 상태이므로 전송하지 않는다.
        }

        override fun setMessageCompression(enabled: Boolean) {
            // 호출 거부 상태이므로 압축 설정을 무시한다.
        }

        override fun isReady(): Boolean = false
    }
}
