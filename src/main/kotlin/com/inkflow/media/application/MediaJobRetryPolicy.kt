package com.inkflow.media.application

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.InkFlowException
import com.inkflow.common.error.SystemException
import org.springframework.stereotype.Component

/**
 * Media 작업 재시도 여부를 판단하는 정책 로직.
 */
@Component
class MediaJobRetryPolicy(
    private val properties: MediaJobRetryProperties
) {
    /**
     * 예외와 시도 횟수를 기준으로 재시도 여부를 결정한다.
     */
    fun decide(exception: Throwable, attempt: Int): MediaJobRetryDecision {
        val retryableByError = isRetryableException(exception)
        if (!retryableByError) {
            return MediaJobRetryDecision(
                shouldRetry = false,
                reason = "비재시도 오류로 분류됨"
            )
        }

        // maxAttempts는 초기 시도를 포함한 총 처리 시도 횟수 기준이다.
        if (properties.maxAttempts > 0 && attempt > properties.maxAttempts) {
            return MediaJobRetryDecision(
                shouldRetry = false,
                reason = "최대 시도 횟수 초과"
            )
        }

        return MediaJobRetryDecision(
            shouldRetry = true,
            reason = "재시도 가능"
        )
    }

    /**
     * 예외 타입/에러 코드 기반으로 재시도 가능 여부를 계산한다.
     */
    private fun isRetryableException(exception: Throwable): Boolean {
        return when (exception) {
            is BusinessException -> false
            is SystemException -> exception.errorCode.retryable
            is InkFlowException -> exception.errorCode.retryable
            else -> true
        }
    }

    /**
     * 예외에서 공통 에러 코드를 추출한다.
     */
    fun resolveErrorCode(exception: Throwable): ErrorCode {
        return if (exception is InkFlowException) {
            exception.errorCode
        } else {
            ErrorCode.INTERNAL_ERROR
        }
    }
}

/**
 * Media 작업 재시도 판단 결과를 전달한다.
 */
data class MediaJobRetryDecision(
    val shouldRetry: Boolean,
    val reason: String
) {
    init {
        require(reason.isNotBlank()) { "reason은 비어 있을 수 없습니다." }
    }
}
