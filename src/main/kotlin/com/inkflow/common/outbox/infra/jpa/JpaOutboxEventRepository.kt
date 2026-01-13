package com.inkflow.common.outbox.infra.jpa

import com.inkflow.common.outbox.domain.OutboxEvent
import com.inkflow.common.outbox.domain.OutboxEventRepository
import com.inkflow.common.outbox.domain.OutboxEventStatus
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

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

    /**
     * 전송 대기 이벤트를 배치로 가져오면서 DB 잠금을 획득한다.
     */
    override fun findPendingEventsForUpdate(
        limit: Int,
        now: Instant,
        lockExpiredBefore: Instant
    ): List<OutboxEvent> {
        return outboxEventJpaRepository
            .findPendingForUpdate(
                pendingStatus = OutboxEventStatus.PENDING.name,
                sendingStatus = OutboxEventStatus.SENDING.name,
                now = now,
                lockExpiredBefore = lockExpiredBefore,
                limit = limit
            )
            .map { it.toDomain() }
    }

    /**
     * 이벤트를 전송 완료로 마킹한다.
     */
    override fun markSent(eventId: UUID, sentAt: Instant) {
        outboxEventJpaRepository.updateSent(eventId, OutboxEventStatus.SENT.name, sentAt)
    }

    /**
     * 이벤트를 전송 중 상태로 잠근다.
     */
    override fun markSending(eventId: UUID, lockedAt: Instant) {
        outboxEventJpaRepository.updateSending(eventId, OutboxEventStatus.SENDING.name, lockedAt)
    }

    /**
     * 이벤트를 재시도 대상으로 마킹한다.
     */
    override fun markRetry(eventId: UUID, retryCount: Int, nextRetryAt: Instant, lastError: String?) {
        outboxEventJpaRepository.updateForRetry(
            id = eventId,
            status = OutboxEventStatus.PENDING.name,
            retryCount = retryCount,
            nextRetryAt = nextRetryAt,
            lastError = lastError
        )
    }

    /**
     * 이벤트를 전송 실패로 마킹한다.
     */
    override fun markFailed(eventId: UUID, lastError: String?) {
        outboxEventJpaRepository.updateFailed(
            id = eventId,
            status = OutboxEventStatus.FAILED.name,
            lastError = lastError
        )
    }
}
