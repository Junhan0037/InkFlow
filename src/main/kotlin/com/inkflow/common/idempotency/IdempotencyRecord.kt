package com.inkflow.common.idempotency

import java.time.Instant

/**
 * Idempotency 키의 처리 상태와 결과를 저장하는 모델.
 */
data class IdempotencyRecord(
    val key: String,
    val status: IdempotencyStatus,
    val payload: String?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(key.isNotBlank()) { "key는 비어 있을 수 없습니다." }
    }
}
