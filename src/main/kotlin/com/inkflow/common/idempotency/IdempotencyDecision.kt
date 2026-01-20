package com.inkflow.common.idempotency

/**
 * Idempotency 키 처리 판단 결과를 정의.
 */
enum class IdempotencyDecision {
    STARTED, // 신규 처리 시작 가능 상태.
    ALREADY_COMPLETED, // 이미 완료된 요청이므로 처리를 건너뛴다.
    IN_PROGRESS // 현재 처리 중이므로 중복 처리를 방지한다.
}
