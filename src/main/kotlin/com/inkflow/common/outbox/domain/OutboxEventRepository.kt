package com.inkflow.common.outbox.domain

/**
 * Outbox 이벤트를 저장/조회하기 위한 저장소 계약.
 */
interface OutboxEventRepository {
    /**
     * Outbox 이벤트를 저장한다.
     */
    fun save(event: OutboxEvent): OutboxEvent
}
