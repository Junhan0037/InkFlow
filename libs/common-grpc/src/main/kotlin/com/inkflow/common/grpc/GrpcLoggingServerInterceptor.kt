package com.inkflow.common.grpc

import com.inkflow.common.observability.TraceContext
import com.inkflow.common.observability.TraceMdcBinder
import io.grpc.ForwardingServerCall
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * gRPC 서버 요청 로그를 표준화하는 인터셉터.
 */
class GrpcLoggingServerInterceptor(
    private val serviceName: String? = null,
    private val requestIdGenerator: () -> String = { "req-${UUID.randomUUID()}" },
    private val logger: Logger = LoggerFactory.getLogger(GrpcLoggingServerInterceptor::class.java)
) : ServerInterceptor {
    /**
     * 호출 종료 시 로깅을 수행하도록 ServerCall을 래핑한다.
     */
    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> {
        val startNanos = System.nanoTime()
        val requestId = GrpcMetadataUtils.get(headers, GrpcMetadataKeys.REQUEST_ID) ?: requestIdGenerator()
        val traceId = GrpcMetadataUtils.get(headers, GrpcMetadataKeys.TRACE_ID)
        val actorId = GrpcMetadataUtils.get(headers, GrpcMetadataKeys.ACTOR_ID)

        val wrappedCall = object : ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
            /**
             * 응답 종료 시점에 로그와 MDC 바인딩을 처리한다.
             */
            override fun close(status: Status, trailers: Metadata) {
                val latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
                val traceContext = traceId
                    ?.takeIf { TraceContext.isValidTraceId(it) }
                    ?.let { TraceContext(traceId = it) }

                val scope = TraceMdcBinder.bind(traceContext, requestId, serviceName)
                try {
                    if (status.isOk) {
                        logger.info(
                            "grpc.server.completed method={} status={} latencyMs={} requestId={} traceId={} actorId={}",
                            call.methodDescriptor.fullMethodName,
                            status.code,
                            latencyMs,
                            requestId,
                            traceId,
                            actorId
                        )
                    } else {
                        logger.warn(
                            "grpc.server.failed method={} status={} latencyMs={} requestId={} traceId={} actorId={} detail={}",
                            call.methodDescriptor.fullMethodName,
                            status.code,
                            latencyMs,
                            requestId,
                            traceId,
                            actorId,
                            status.description,
                            status.cause
                        )
                    }
                } finally {
                    scope.close()
                }
                super.close(status, trailers)
            }
        }

        return next.startCall(wrappedCall, headers)
    }
}
