package com.inkflow.common.outbox.application

import com.inkflow.common.outbox.domain.OutboxEvent

/**
 * Outbox 이벤트를 외부 메시지 브로커로 발행하는 계약.
 */
fun interface OutboxEventPublisher {
    /**
     * Outbox 이벤트를 발행한다.
     */
    fun publish(event: OutboxEvent)
}
