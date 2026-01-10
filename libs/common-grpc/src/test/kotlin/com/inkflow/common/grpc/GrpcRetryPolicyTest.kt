package com.inkflow.common.grpc

import io.grpc.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * gRPC 재시도 정책 계산 로직을 검증한다.
 */
class GrpcRetryPolicyTest {
    /**
     * 재시도 횟수에 따라 백오프가 증가하고 최대값에 제한되는지 확인한다.
     */
    @Test
    fun backoffForRetry_increasesAndClamps() {
        val policy = GrpcRetryPolicy(
            initialBackoff = Duration.ofMillis(100),
            maxBackoff = Duration.ofMillis(250),
            backoffMultiplier = 2.0
        )

        assertEquals(Duration.ofMillis(100), policy.backoffForRetry(1))
        assertEquals(Duration.ofMillis(200), policy.backoffForRetry(2))
        assertEquals(Duration.ofMillis(250), policy.backoffForRetry(3))
    }

    /**
     * 재시도 횟수는 1 이상이어야 한다.
     */
    @Test
    fun backoffForRetry_rejectsInvalidRetryCount() {
        val policy = GrpcRetryPolicy()

        assertThrows(IllegalArgumentException::class.java) {
            policy.backoffForRetry(0)
        }
    }

    /**
     * 재시도 정책 설정 값이 잘못되면 예외가 발생한다.
     */
    @Test
    fun retryPolicy_rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException::class.java) {
            GrpcRetryPolicy(maxAttempts = 0)
        }
    }

    /**
     * 재시도 가능한 상태 코드 여부를 판단한다.
     */
    @Test
    fun isRetryable_returnsTrueForConfiguredStatus() {
        val policy = GrpcRetryPolicy()

        assertTrue(policy.isRetryable(Status.UNAVAILABLE))
    }

    /**
     * 재시도 대상이 아닌 상태 코드는 false를 반환한다.
     */
    @Test
    fun isRetryable_returnsFalseForNonRetryableStatus() {
        val policy = GrpcRetryPolicy()

        assertEquals(false, policy.isRetryable(Status.INVALID_ARGUMENT))
    }
}
