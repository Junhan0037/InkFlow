package com.inkflow.media.domain

import java.time.Instant

/**
 * Media 작업 실패 로그 도메인 모델.
 */
data class MediaJobFailureLog(
    val id: String? = null,
    val jobId: String,
    val assetId: Long,
    val derivativeType: String,
    val errorCode: String,
    val errorMessage: String,
    val errorDetails: Map<String, String> = emptyMap(),
    val exceptionType: String,
    val shouldRetry: Boolean,
    val retryReason: String,
    val retryCount: Int,
    val traceId: String?,
    val eventId: String?,
    val idempotencyKey: String?,
    val occurredAt: Instant
) {
    init {
        require(jobId.isNotBlank()) { "jobId는 비어 있을 수 없습니다." }
        require(assetId > 0) { "assetId는 0보다 커야 합니다." }
        require(derivativeType.isNotBlank()) { "derivativeType은 비어 있을 수 없습니다." }
        require(errorCode.isNotBlank()) { "errorCode는 비어 있을 수 없습니다." }
        require(errorMessage.isNotBlank()) { "errorMessage는 비어 있을 수 없습니다." }
        require(exceptionType.isNotBlank()) { "exceptionType은 비어 있을 수 없습니다." }
        require(retryReason.isNotBlank()) { "retryReason은 비어 있을 수 없습니다." }
        require(retryCount > 0) { "retryCount는 0보다 커야 합니다." }
    }
}
