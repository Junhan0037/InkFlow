package com.inkflow.media.infra.mongo

import com.inkflow.media.domain.MediaJobFailureLog
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * MongoDB에 저장하는 Media 작업 실패 로그 문서.
 */
@Document(collection = "media_job_failure_logs")
data class MediaJobFailureLogDocument(
    @Id
    val id: String? = null,
    @Indexed
    val jobId: String,
    @Indexed
    val assetId: Long,
    val derivativeType: String,
    val errorCode: String,
    val errorMessage: String,
    val errorDetails: Map<String, String>,
    val exceptionType: String,
    val shouldRetry: Boolean,
    val retryReason: String,
    val retryCount: Int,
    val traceId: String?,
    val eventId: String?,
    val idempotencyKey: String?,
    @Indexed
    val occurredAt: Instant
) {
    companion object {
        /**
         * 도메인 모델을 MongoDB 문서로 변환한다.
         */
        fun fromDomain(log: MediaJobFailureLog): MediaJobFailureLogDocument {
            return MediaJobFailureLogDocument(
                id = log.id,
                jobId = log.jobId,
                assetId = log.assetId,
                derivativeType = log.derivativeType,
                errorCode = log.errorCode,
                errorMessage = log.errorMessage,
                errorDetails = log.errorDetails,
                exceptionType = log.exceptionType,
                shouldRetry = log.shouldRetry,
                retryReason = log.retryReason,
                retryCount = log.retryCount,
                traceId = log.traceId,
                eventId = log.eventId,
                idempotencyKey = log.idempotencyKey,
                occurredAt = log.occurredAt
            )
        }
    }

    /**
     * MongoDB 문서를 도메인 모델로 변환한다.
     */
    fun toDomain(): MediaJobFailureLog {
        return MediaJobFailureLog(
            id = id,
            jobId = jobId,
            assetId = assetId,
            derivativeType = derivativeType,
            errorCode = errorCode,
            errorMessage = errorMessage,
            errorDetails = errorDetails,
            exceptionType = exceptionType,
            shouldRetry = shouldRetry,
            retryReason = retryReason,
            retryCount = retryCount,
            traceId = traceId,
            eventId = eventId,
            idempotencyKey = idempotencyKey,
            occurredAt = occurredAt
        )
    }
}
