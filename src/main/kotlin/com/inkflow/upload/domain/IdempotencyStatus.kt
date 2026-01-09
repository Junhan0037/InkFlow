package com.inkflow.upload.domain

/**
 * Idempotency 키 처리 상태를 정의한다.
 */
enum class IdempotencyStatus {
    /**
     * 처리 중인 요청 상태.
     */
    IN_PROGRESS,

    /**
     * 처리 완료된 요청 상태.
     */
    COMPLETED
}
