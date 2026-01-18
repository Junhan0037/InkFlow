package com.inkflow.media.application

import com.inkflow.common.error.InkFlowException
import com.inkflow.media.domain.MediaJobFailureLog
import com.inkflow.media.domain.MediaJobFailureLogRepository
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * Media 작업 실패 로그 기록과 재시도 판단을 담당.
 */
@Service
class MediaJobFailureHandler(
    private val failureLogRepository: MediaJobFailureLogRepository,
    private val retryPolicy: MediaJobRetryPolicy,
    private val clock: Clock
) {
    /**
     * 실패 로그를 저장하고 재시도 여부를 반환한다.
     */
    fun handleFailure(
        command: MediaJobCommand,
        metadata: MediaJobMessageMetadata,
        exception: Throwable
    ): MediaJobRetryDecision {
        val attempt = calculateAttemptCount(command.jobId)
        val decision = retryPolicy.decide(exception, attempt)
        val now = Instant.now(clock)
        val errorCode = retryPolicy.resolveErrorCode(exception)
        val errorDetails = (exception as? InkFlowException)?.details ?: emptyMap()
        val errorMessage = exception.message?.takeIf { it.isNotBlank() } ?: errorCode.message

        val failureLog = MediaJobFailureLog(
            jobId = command.jobId,
            assetId = command.assetId,
            derivativeType = command.derivativeType,
            errorCode = errorCode.code,
            errorMessage = errorMessage,
            errorDetails = errorDetails,
            exceptionType = exception::class.java.name,
            shouldRetry = decision.shouldRetry,
            retryReason = decision.reason,
            retryCount = attempt,
            traceId = metadata.traceId,
            eventId = metadata.eventId.toString(),
            idempotencyKey = metadata.idempotencyKey,
            occurredAt = now
        )

        failureLogRepository.save(failureLog)
        return decision
    }

    /**
     * 기존 실패 로그 건수를 기준으로 이번 시도 횟수를 계산한다.
     */
    private fun calculateAttemptCount(jobId: String): Int {
        val previousFailures = failureLogRepository.countByJobId(jobId)
        return previousFailures.toInt() + 1
    }
}
