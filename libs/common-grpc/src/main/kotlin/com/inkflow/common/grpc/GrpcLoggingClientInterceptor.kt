package com.inkflow.common.grpc

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.ForwardingClientCallListener
import io.grpc.MethodDescriptor
import io.grpc.Metadata
import io.grpc.Status
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * gRPC 클라이언트 호출 로깅을 담당하는 인터셉터.
 */
class GrpcLoggingClientInterceptor(
    private val logger: Logger = LoggerFactory.getLogger(GrpcLoggingClientInterceptor::class.java)
) : ClientInterceptor {
    /**
     * 호출 시작/종료 시점을 기록하도록 ClientCall을 래핑한다.
     */
    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel
    ): ClientCall<ReqT, RespT> {
        val startNanos = System.nanoTime()
        val delegate = next.newCall(method, callOptions)
        return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(delegate) {
            private var requestId: String? = null
            private var traceId: String? = null
            private var actorId: String? = null

            /**
             * 메타데이터 정보를 추출한 뒤 리스너를 감싼다.
             */
            override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                requestId = GrpcMetadataUtils.get(headers, GrpcMetadataKeys.REQUEST_ID)
                traceId = GrpcMetadataUtils.get(headers, GrpcMetadataKeys.TRACE_ID)
                actorId = GrpcMetadataUtils.get(headers, GrpcMetadataKeys.ACTOR_ID)

                val loggingListener = object : ForwardingClientCallListener
                    .SimpleForwardingClientCallListener<RespT>(responseListener) {
                    /**
                     * 호출 종료 로그를 남긴다.
                     */
                    override fun onClose(status: Status, trailers: Metadata) {
                        val latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
                        if (status.isOk) {
                            logger.info(
                                "grpc.client.completed method={} status={} latencyMs={} requestId={} traceId={} actorId={}",
                                method.fullMethodName,
                                status.code,
                                latencyMs,
                                requestId,
                                traceId,
                                actorId
                            )
                        } else {
                            logger.warn(
                                "grpc.client.failed method={} status={} latencyMs={} requestId={} traceId={} actorId={} detail={}",
                                method.fullMethodName,
                                status.code,
                                latencyMs,
                                requestId,
                                traceId,
                                actorId,
                                status.description,
                                status.cause
                            )
                        }
                        super.onClose(status, trailers)
                    }
                }

                super.start(loggingListener, headers)
            }
        }
    }
}
