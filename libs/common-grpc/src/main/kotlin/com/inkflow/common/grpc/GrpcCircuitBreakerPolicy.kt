package com.inkflow.common.grpc

import io.grpc.Status
import java.time.Duration

/**
 * gRPC 서킷브레이커 정책을 정의.
 */
data class GrpcCircuitBreakerPolicy(
    val enabled: Boolean = true,
    val maxFailures: Int = 5,
    val openDuration: Duration = Duration.ofSeconds(30),
    val halfOpenMaxCalls: Int = 1,
    val failureStatusCodes: Set<Status.Code> = setOf(
        Status.Code.UNAVAILABLE,
        Status.Code.DEADLINE_EXCEEDED,
        Status.Code.RESOURCE_EXHAUSTED,
        Status.Code.INTERNAL
    )
) {
    init {
        require(maxFailures >= 1) { "maxFailures는 1 이상이어야 합니다." }
        require(!openDuration.isNegative && !openDuration.isZero) { "openDuration은 양수여야 합니다." }
        require(halfOpenMaxCalls >= 1) { "halfOpenMaxCalls는 1 이상이어야 합니다." }
    }
}

/**
 * 메서드별 서킷브레이커 정책을 제공하는 계약이다.
 */
fun interface GrpcCircuitBreakerPolicyProvider {
    /**
     * 메서드에 적용할 서킷브레이커 정책을 반환한다.
     */
    fun resolve(method: io.grpc.MethodDescriptor<*, *>): GrpcCircuitBreakerPolicy?
}
