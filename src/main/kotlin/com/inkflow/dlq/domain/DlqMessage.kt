package com.inkflow.dlq.domain

import java.time.Instant

/**
 * DLQ에 적재된 메시지의 도메인 모델.
 */
data class DlqMessage(
    val id: String? = null,
    val sourceKey: String,
    val dlqTopic: String,
    val originalTopic: String,
    val originalPartition: Int?,
    val originalOffset: Long?,
    val originalTimestamp: Instant?,
    val messageKey: String?,
    val payload: String,
    val headers: Map<String, String> = emptyMap(),
    val eventId: String?,
    val eventType: String?,
    val producer: String?,
    val traceId: String?,
    val idempotencyKey: String?,
    val occurredAt: Instant?,
    val errorType: String?,
    val errorMessage: String?,
    val errorStacktrace: String?,
    val status: DlqMessageStatus,
    val reprocessCount: Int,
    val lastReprocessedAt: Instant?,
    val lastReprocessBy: String?,
    val lastReprocessReason: String?,
    val lastReprocessError: String?,
    val storedAt: Instant
) {
    init {
        require(sourceKey.isNotBlank()) { "sourceKey는 비어 있을 수 없습니다." }
        require(dlqTopic.isNotBlank()) { "dlqTopic은 비어 있을 수 없습니다." }
        require(originalTopic.isNotBlank()) { "originalTopic은 비어 있을 수 없습니다." }
        require(payload.isNotBlank()) { "payload는 비어 있을 수 없습니다." }
        require(reprocessCount >= 0) { "reprocessCount는 0 이상이어야 합니다." }
    }
}
