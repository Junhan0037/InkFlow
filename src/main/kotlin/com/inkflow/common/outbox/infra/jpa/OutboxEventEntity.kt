package com.inkflow.common.outbox.infra.jpa

import com.inkflow.common.outbox.domain.OutboxEvent
import com.inkflow.common.outbox.domain.OutboxEventStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Outbox 이벤트를 JPA로 영속화하기 위한 엔티티.
 */
@Entity
@Table(name = "outbox_event")
class OutboxEventEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "aggregate_type", nullable = false)
    var aggregateType: String = "",

    @Column(name = "aggregate_id", nullable = false)
    var aggregateId: String = "",

    @Column(name = "event_type", nullable = false)
    var eventType: String = "",

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    var payload: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OutboxEventStatus = OutboxEventStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,

    @Column(name = "sent_at")
    var sentAt: Instant? = null
) {
    /**
     * 엔티티를 도메인 모델로 변환한다.
     */
    fun toDomain(): OutboxEvent {
        return OutboxEvent(
            id = id,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            payload = payload,
            status = status,
            createdAt = createdAt,
            sentAt = sentAt
        )
    }

    companion object {
        /**
         * 도메인 모델을 엔티티로 변환한다.
         */
        fun fromDomain(event: OutboxEvent): OutboxEventEntity {
            return OutboxEventEntity(
                id = event.id,
                aggregateType = event.aggregateType,
                aggregateId = event.aggregateId,
                eventType = event.eventType,
                payload = event.payload,
                status = event.status,
                createdAt = event.createdAt,
                sentAt = event.sentAt
            )
        }
    }
}
