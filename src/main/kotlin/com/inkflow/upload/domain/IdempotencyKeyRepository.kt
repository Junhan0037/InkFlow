package com.inkflow.upload.domain

import java.time.Duration

/**
 * Idempotency 키 저장소 계약.
 */
interface IdempotencyKeyRepository {
    /**
     * Idempotency 키로 저장된 레코드를 조회한다.
     */
    fun find(key: String): IdempotencyRecord?

    /**
     * Idempotency 레코드를 저장한다.
     */
    fun save(record: IdempotencyRecord, ttl: Duration)

    /**
     * 키가 없을 때만 레코드를 저장한다.
     */
    fun saveIfAbsent(record: IdempotencyRecord, ttl: Duration): Boolean

    /**
     * Idempotency 레코드를 삭제한다.
     */
    fun delete(key: String)
}
