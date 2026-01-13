package com.inkflow.common.outbox.domain

import java.time.Instant
import java.util.UUID

/**
 * Outbox 패턴용 이벤트 도메인 모델.
 */
data class OutboxEvent(
    val id: UUID,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
    val status: OutboxEventStatus,
    val retryCount: Int,
    val nextRetryAt: Instant?,
    val lastError: String?,
    val lockedAt: Instant?,
    val createdAt: Instant,
    val sentAt: Instant?
) {
    init {
        require(aggregateType.isNotBlank()) { "aggregateType은 비어 있을 수 없습니다." }
        require(aggregateId.isNotBlank()) { "aggregateId는 비어 있을 수 없습니다." }
        require(eventType.isNotBlank()) { "eventType은 비어 있을 수 없습니다." }
        require(payload.isNotBlank()) { "payload는 비어 있을 수 없습니다." }
        require(retryCount >= 0) { "retryCount는 0 이상이어야 합니다." }
        if (lastError != null) {
            require(lastError.isNotBlank()) { "lastError는 빈 문자열일 수 없습니다." }
        }
    }

    companion object {
        /**
         * 전송 대기 상태의 Outbox 이벤트를 생성한다.
         */
        fun pending(
            aggregateType: String,
            aggregateId: String,
            eventType: String,
            payload: String,
            createdAt: Instant = Instant.now(),
            id: UUID = UUID.randomUUID()
        ): OutboxEvent {
            return OutboxEvent(
                id = id,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
                eventType = eventType,
                payload = payload,
                status = OutboxEventStatus.PENDING,
                retryCount = 0,
                nextRetryAt = null,
                lastError = null,
                lockedAt = null,
                createdAt = createdAt,
                sentAt = null
            )
        }
    }
}
