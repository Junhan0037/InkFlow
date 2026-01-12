package com.inkflow.common.outbox.infra.publisher

import com.inkflow.common.outbox.application.OutboxEventPublisher
import com.inkflow.common.outbox.domain.OutboxEvent
import org.slf4j.LoggerFactory

/**
 * Kafka 연동 전까지 로깅으로 이벤트 발행을 대체하는 퍼블리셔.
 */
class LoggingOutboxEventPublisher : OutboxEventPublisher {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Outbox 이벤트를 로그로 기록해 발행 동작을 대신한다.
     */
    override fun publish(event: OutboxEvent) {
        logger.info(
            "Outbox 이벤트 발행(로깅 대체). eventId={}, eventType={}, aggregateType={}, aggregateId={}",
            event.id,
            event.eventType,
            event.aggregateType,
            event.aggregateId
        )
    }
}
