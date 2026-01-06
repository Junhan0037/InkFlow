package com.inkflow.common.events

import java.time.Instant
import java.util.UUID

/**
 * 이벤트 전송에 사용하는 표준 envelope를 정의한다.
 */
data class EventEnvelope<T : Any>(
    val eventId: UUID,
    val eventType: EventType,
    val occurredAt: Instant,
    val producer: String,
    val traceId: String?,
    val idempotencyKey: String?,
    val payload: T
) {
    init {
        require(producer.isNotBlank()) { "producer는 비어 있을 수 없습니다." }
        if (traceId != null) {
            require(traceId.isNotBlank()) { "traceId는 빈 문자열일 수 없습니다." }
        }
        if (idempotencyKey != null) {
            require(idempotencyKey.isNotBlank()) { "idempotencyKey는 빈 문자열일 수 없습니다." }
        }
    }

    companion object {
        /**
         * 기본 메타데이터를 자동으로 채워 이벤트 envelope를 생성한다.
         */
        fun <T : Any> create(
            eventType: EventType,
            producer: String,
            payload: T,
            traceId: String? = null,
            idempotencyKey: String? = null,
            eventId: UUID = UUID.randomUUID(),
            occurredAt: Instant = Instant.now()
        ): EventEnvelope<T> {
            return EventEnvelope(
                eventId = eventId,
                eventType = eventType,
                occurredAt = occurredAt,
                producer = producer,
                traceId = traceId,
                idempotencyKey = idempotencyKey,
                payload = payload
            )
        }
    }
}
