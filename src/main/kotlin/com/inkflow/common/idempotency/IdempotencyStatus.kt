package com.inkflow.common.idempotency

/**
 * Idempotency 키 처리 상태를 정의.
 */
enum class IdempotencyStatus {
    IN_PROGRESS, // 처리 중인 요청 상태.
    COMPLETED // 처리 완료된 요청 상태.
}
