package com.inkflow.common.idempotency

import java.time.Duration
import java.time.Instant

/**
 * 테스트 전용 In-memory Idempotency 키 저장소 구현체.
 */
class InMemoryIdempotencyKeyRepository : IdempotencyKeyRepository {
    private val store = mutableMapOf<String, IdempotencyRecord>()

    /**
     * 키에 해당하는 레코드를 조회한다.
     */
    override fun find(key: String): IdempotencyRecord? {
        return store[key]
    }

    /**
     * TTL 검증 없이 레코드를 저장한다.
     */
    override fun save(record: IdempotencyRecord, ttl: Duration) {
        store[record.key] = record.copy(updatedAt = Instant.now())
    }

    /**
     * 키가 없을 때만 레코드를 저장한다.
     */
    override fun saveIfAbsent(record: IdempotencyRecord, ttl: Duration): Boolean {
        return if (store.containsKey(record.key)) {
            false
        } else {
            store[record.key] = record
            true
        }
    }

    /**
     * 키에 해당하는 레코드를 삭제한다.
     */
    override fun delete(key: String) {
        store.remove(key)
    }
}
