package com.inkflow.common.outbox.domain

import java.time.Instant
import java.util.UUID

/**
 * Outbox 이벤트를 저장/조회하기 위한 저장소 계약.
 */
interface OutboxEventRepository {
    /**
     * Outbox 이벤트를 저장한다.
     */
    fun save(event: OutboxEvent): OutboxEvent

    /**
     * 전송 대기 중인 이벤트를 배치 단위로 조회하며, 중복 처리를 막기 위해 잠금을 획득한다.
     */
    fun findPendingEventsForUpdate(limit: Int, now: Instant): List<OutboxEvent>

    /**
     * Outbox 이벤트를 전송 완료 상태로 갱신한다.
     */
    fun markSent(eventId: UUID, sentAt: Instant)

    /**
     * Outbox 이벤트를 재시도 대상 상태로 갱신한다.
     */
    fun markRetry(eventId: UUID, retryCount: Int, nextRetryAt: Instant, lastError: String?)

    /**
     * Outbox 이벤트를 전송 실패 상태로 갱신한다.
     */
    fun markFailed(eventId: UUID, lastError: String?)
}
