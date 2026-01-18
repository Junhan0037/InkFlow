package com.inkflow.media.application

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * MediaJobRetryPolicy의 재시도 판단 규칙을 검증한다.
 */
class MediaJobRetryPolicyTest {
    /**
     * 비즈니스 예외는 재시도하지 않는지 확인한다.
     */
    @Test
    fun decide_returnsFalseForBusinessException() {
        // 준비: 재시도 정책을 구성한다.
        val policy = MediaJobRetryPolicy(MediaJobRetryProperties(maxAttempts = 3))
        val exception = BusinessException(errorCode = ErrorCode.INVALID_REQUEST)

        // 실행: 재시도 여부를 판단한다.
        val decision = policy.decide(exception, attempt = 1)

        // 검증: 재시도하지 않는다.
        assertFalse(decision.shouldRetry)
    }

    /**
     * 최대 시도 횟수를 초과하면 재시도하지 않는지 확인한다.
     */
    @Test
    fun decide_returnsFalseWhenAttemptsExceeded() {
        // 준비: 최대 시도 횟수를 2로 설정한다.
        val policy = MediaJobRetryPolicy(MediaJobRetryProperties(maxAttempts = 2))
        val exception = SystemException(errorCode = ErrorCode.DEPENDENCY_FAILURE)

        // 실행: 시도 횟수를 초과한 경우를 판단한다.
        val decision = policy.decide(exception, attempt = 3)

        // 검증: 재시도하지 않는다.
        assertFalse(decision.shouldRetry)
    }

    /**
     * 재시도 가능한 시스템 예외는 재시도하는지 확인한다.
     */
    @Test
    fun decide_returnsTrueWhenRetryableAndWithinLimit() {
        // 준비: 최대 시도 횟수를 3으로 설정한다.
        val policy = MediaJobRetryPolicy(MediaJobRetryProperties(maxAttempts = 3))
        val exception = SystemException(errorCode = ErrorCode.DEPENDENCY_FAILURE)

        // 실행: 재시도 가능한 예외를 판단한다.
        val decision = policy.decide(exception, attempt = 2)

        // 검증: 재시도한다.
        assertTrue(decision.shouldRetry)
    }

    /**
     * 알 수 없는 예외는 INTERNAL_ERROR로 매핑되는지 확인한다.
     */
    @Test
    fun resolveErrorCode_returnsInternalErrorForUnknownException() {
        // 준비: 재시도 정책을 구성한다.
        val policy = MediaJobRetryPolicy(MediaJobRetryProperties())

        // 실행: 알 수 없는 예외를 전달한다.
        val errorCode = policy.resolveErrorCode(IllegalStateException("boom"))

        // 검증: INTERNAL_ERROR로 매핑된다.
        assertEquals(ErrorCode.INTERNAL_ERROR, errorCode)
    }
}
