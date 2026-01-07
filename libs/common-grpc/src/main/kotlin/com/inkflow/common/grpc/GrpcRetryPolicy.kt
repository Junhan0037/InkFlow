package com.inkflow.common.grpc

import io.grpc.Status
import java.time.Duration
import kotlin.math.min
import kotlin.math.pow

/**
 * gRPC 재시도 정책을 정의한다.
 */
data class GrpcRetryPolicy(
    val maxAttempts: Int = 3,
    val initialBackoff: Duration = Duration.ofMillis(100),
    val maxBackoff: Duration = Duration.ofSeconds(1),
    val backoffMultiplier: Double = 2.0,
    val retryableStatusCodes: Set<Status.Code> = setOf(
        Status.Code.UNAVAILABLE,
        Status.Code.DEADLINE_EXCEEDED,
        Status.Code.RESOURCE_EXHAUSTED
    )
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts는 1 이상이어야 합니다." }
        require(!initialBackoff.isNegative && !initialBackoff.isZero) { "initialBackoff은 양수여야 합니다." }
        require(!maxBackoff.isNegative && !maxBackoff.isZero) { "maxBackoff은 양수여야 합니다." }
        require(backoffMultiplier >= 1.0) { "backoffMultiplier는 1.0 이상이어야 합니다." }
    }

    /**
     * 재시도 횟수에 따른 지수 백오프 시간을 계산한다.
     */
    fun backoffForRetry(retryCount: Int): Duration {
        require(retryCount >= 1) { "retryCount는 1 이상이어야 합니다." }
        val multiplier = backoffMultiplier.pow((retryCount - 1).toDouble())
        val candidateMillis = initialBackoff.toMillis().toDouble() * multiplier
        val clampedMillis = min(candidateMillis, maxBackoff.toMillis().toDouble())
        return Duration.ofMillis(clampedMillis.toLong().coerceAtLeast(1L))
    }

    /**
     * 상태 코드가 재시도 대상인지 확인한다.
     */
    fun isRetryable(status: Status): Boolean {
        return retryableStatusCodes.contains(status.code)
    }
}

/**
 * 메서드별 재시도 정책을 제공하는 계약이다.
 */
fun interface GrpcRetryPolicyProvider {
    /**
     * 메서드에 적용할 재시도 정책을 반환한다.
     */
    fun resolve(method: io.grpc.MethodDescriptor<*, *>): GrpcRetryPolicy?
}
