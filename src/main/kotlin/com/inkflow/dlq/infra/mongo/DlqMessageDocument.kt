package com.inkflow.dlq.infra.mongo

import com.inkflow.dlq.domain.DlqMessage
import com.inkflow.dlq.domain.DlqMessageStatus
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * MongoDB에 저장하는 DLQ 메시지 문서.
 */
@Document(collection = "dlq_messages")
data class DlqMessageDocument(
    @Id
    val id: String? = null,
    @Indexed(unique = true)
    val sourceKey: String,
    @Indexed
    val dlqTopic: String,
    @Indexed
    val originalTopic: String,
    val originalPartition: Int?,
    val originalOffset: Long?,
    val originalTimestamp: Instant?,
    val messageKey: String?,
    val payload: String,
    val headers: Map<String, String>,
    val eventId: String?,
    @Indexed
    val eventType: String?,
    val producer: String?,
    val traceId: String?,
    val idempotencyKey: String?,
    val occurredAt: Instant?,
    val errorType: String?,
    val errorMessage: String?,
    val errorStacktrace: String?,
    @Indexed
    val status: DlqMessageStatus,
    val reprocessCount: Int,
    val lastReprocessedAt: Instant?,
    val lastReprocessBy: String?,
    val lastReprocessReason: String?,
    val lastReprocessError: String?,
    @Indexed
    val storedAt: Instant
) {
    companion object {
        /**
         * 도메인 모델을 문서로 변환한다.
         */
        fun fromDomain(message: DlqMessage): DlqMessageDocument {
            return DlqMessageDocument(
                id = message.id,
                sourceKey = message.sourceKey,
                dlqTopic = message.dlqTopic,
                originalTopic = message.originalTopic,
                originalPartition = message.originalPartition,
                originalOffset = message.originalOffset,
                originalTimestamp = message.originalTimestamp,
                messageKey = message.messageKey,
                payload = message.payload,
                headers = message.headers,
                eventId = message.eventId,
                eventType = message.eventType,
                producer = message.producer,
                traceId = message.traceId,
                idempotencyKey = message.idempotencyKey,
                occurredAt = message.occurredAt,
                errorType = message.errorType,
                errorMessage = message.errorMessage,
                errorStacktrace = message.errorStacktrace,
                status = message.status,
                reprocessCount = message.reprocessCount,
                lastReprocessedAt = message.lastReprocessedAt,
                lastReprocessBy = message.lastReprocessBy,
                lastReprocessReason = message.lastReprocessReason,
                lastReprocessError = message.lastReprocessError,
                storedAt = message.storedAt
            )
        }
    }

    /**
     * 문서를 도메인 모델로 변환한다.
     */
    fun toDomain(): DlqMessage {
        return DlqMessage(
            id = id,
            sourceKey = sourceKey,
            dlqTopic = dlqTopic,
            originalTopic = originalTopic,
            originalPartition = originalPartition,
            originalOffset = originalOffset,
            originalTimestamp = originalTimestamp,
            messageKey = messageKey,
            payload = payload,
            headers = headers,
            eventId = eventId,
            eventType = eventType,
            producer = producer,
            traceId = traceId,
            idempotencyKey = idempotencyKey,
            occurredAt = occurredAt,
            errorType = errorType,
            errorMessage = errorMessage,
            errorStacktrace = errorStacktrace,
            status = status,
            reprocessCount = reprocessCount,
            lastReprocessedAt = lastReprocessedAt,
            lastReprocessBy = lastReprocessBy,
            lastReprocessReason = lastReprocessReason,
            lastReprocessError = lastReprocessError,
            storedAt = storedAt
        )
    }
}
