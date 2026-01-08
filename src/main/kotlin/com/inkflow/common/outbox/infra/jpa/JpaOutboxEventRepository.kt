package com.inkflow.common.outbox.infra.jpa

import com.inkflow.common.outbox.domain.OutboxEvent
import com.inkflow.common.outbox.domain.OutboxEventRepository
import org.springframework.stereotype.Repository

/**
 * JPA 기반 Outbox 이벤트 저장소 구현체.
 */
@Repository
class JpaOutboxEventRepository(
    private val outboxEventJpaRepository: OutboxEventJpaRepository
) : OutboxEventRepository {
    /**
     * Outbox 이벤트를 저장한다.
     */
    override fun save(event: OutboxEvent): OutboxEvent {
        val entity = OutboxEventEntity.fromDomain(event)
        return outboxEventJpaRepository.save(entity).toDomain()
    }
}
