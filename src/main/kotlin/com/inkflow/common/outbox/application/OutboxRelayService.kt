package com.inkflow.common.outbox.application

import com.inkflow.common.outbox.domain.OutboxEvent
import com.inkflow.common.outbox.domain.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Outbox 이벤트를 조회해 발행하고 상태를 갱신하는 애플리케이션 서비스.
 */
@Service
class OutboxRelayService(
    private val outboxEventRepository: OutboxEventRepository,
    private val outboxEventPublisher: OutboxEventPublisher,
    private val properties: OutboxRelayProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Outbox 이벤트를 배치로 폴링하고 발행한다.
     */
    @Transactional
    fun relayPendingEvents() {
        val pendingEvents = outboxEventRepository.findPendingEventsForUpdate(properties.batchSize)
        if (pendingEvents.isEmpty()) {
            return
        }

        // 동일 배치 내 이벤트는 오래된 순서로 처리해 지연을 줄인다.
        pendingEvents.forEach { event ->
            handleEvent(event)
        }
    }

    /**
     * 이벤트 발행 결과에 따라 상태를 갱신한다.
     */
    private fun handleEvent(event: OutboxEvent) {
        try {
            outboxEventPublisher.publish(event)
            outboxEventRepository.markSent(event.id, Instant.now())
            logger.info(
                "Outbox 이벤트 발행 완료. eventId={}, eventType={}, aggregateType={}, aggregateId={}",
                event.id,
                event.eventType,
                event.aggregateType,
                event.aggregateId
            )
        } catch (exception: Exception) {
            outboxEventRepository.markFailed(event.id)
            logger.error(
                "Outbox 이벤트 발행 실패. eventId={}, eventType={}, aggregateType={}, aggregateId={}",
                event.id,
                event.eventType,
                event.aggregateType,
                event.aggregateId,
                exception
            )
        }
    }
}
