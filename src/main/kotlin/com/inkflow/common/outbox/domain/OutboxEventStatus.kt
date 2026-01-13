package com.inkflow.common.outbox.domain

/**
 * Outbox 이벤트의 전송 상태를 표현한다.
 */
enum class OutboxEventStatus {
    /**
     * 아직 전송되지 않은 상태.
     */
    PENDING,

    /**
     * 전송 중으로 잠금이 획득된 상태.
     */
    SENDING,

    /**
     * 전송 완료 상태.
     */
    SENT,

    /**
     * 전송 실패 상태.
     */
    FAILED
}
