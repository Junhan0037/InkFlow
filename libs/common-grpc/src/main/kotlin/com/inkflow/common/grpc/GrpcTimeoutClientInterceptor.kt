package com.inkflow.common.grpc

import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.MethodDescriptor
import io.grpc.Channel
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * gRPC 호출에 기본 타임아웃을 적용하는 인터셉터.
 */
class GrpcTimeoutClientInterceptor(
    private val defaultTimeout: Duration,
    private val timeoutPolicyProvider: TimeoutPolicyProvider? = null
) : ClientInterceptor {
    init {
        require(!defaultTimeout.isZero && !defaultTimeout.isNegative) {
            "defaultTimeout은 양수여야 합니다."
        }
    }

    /**
     * 호출에 타임아웃을 적용해 새로운 ClientCall을 생성한다.
     */
    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel
    ): ClientCall<ReqT, RespT> {
        val timeout = timeoutPolicyProvider?.resolve(method) ?: defaultTimeout
        if (timeout.isZero || timeout.isNegative) {
            return next.newCall(method, callOptions)
        }
        if (callOptions.deadline != null) {
            return next.newCall(method, callOptions)
        }
        val updatedOptions = callOptions.withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS)
        return next.newCall(method, updatedOptions)
    }
}

/**
 * 메서드별 타임아웃 정책을 제공하는 계약이다.
 */
fun interface TimeoutPolicyProvider {
    /**
     * 메서드에 적용할 타임아웃을 반환한다.
     */
    fun resolve(method: MethodDescriptor<*, *>): Duration?
}
