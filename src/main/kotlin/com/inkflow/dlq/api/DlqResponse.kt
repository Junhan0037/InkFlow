package com.inkflow.dlq.api

import java.time.Instant

/**
 * DLQ 메시지 요약 응답 DTO.
 */
data class DlqMessageSummaryResponse(
    val id: String,
    val status: String,
    val dlqTopic: String,
    val originalTopic: String,
    val eventType: String?,
    val eventId: String?,
    val traceId: String?,
    val idempotencyKey: String?,
    val errorType: String?,
    val errorMessage: String?,
    val reprocessCount: Int,
    val lastReprocessedAt: Instant?,
    val storedAt: Instant
)

/**
 * DLQ 메시지 상세 응답 DTO.
 */
data class DlqMessageDetailResponse(
    val id: String,
    val status: String,
    val dlqTopic: String,
    val originalTopic: String,
    val originalPartition: Int?,
    val originalOffset: Long?,
    val originalTimestamp: Instant?,
    val messageKey: String?,
    val payload: String,
    val headers: Map<String, String>,
    val eventType: String?,
    val eventId: String?,
    val producer: String?,
    val traceId: String?,
    val idempotencyKey: String?,
    val occurredAt: Instant?,
    val errorType: String?,
    val errorMessage: String?,
    val errorStacktrace: String?,
    val reprocessCount: Int,
    val lastReprocessedAt: Instant?,
    val lastReprocessBy: String?,
    val lastReprocessReason: String?,
    val lastReprocessError: String?,
    val storedAt: Instant
)

/**
 * DLQ 메시지 목록 응답 DTO.
 */
data class DlqMessagePageResponse(
    val items: List<DlqMessageSummaryResponse>,
    val total: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int
)

/**
 * DLQ 재처리 결과 응답 DTO.
 */
data class DlqReprocessResponse(
    val messageId: String,
    val status: String,
    val reprocessCount: Int,
    val reprocessedAt: Instant?,
    val errorMessage: String?
)
