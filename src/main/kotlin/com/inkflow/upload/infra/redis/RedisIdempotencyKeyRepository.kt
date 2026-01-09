package com.inkflow.upload.infra.redis

import com.inkflow.upload.domain.IdempotencyKeyRepository
import com.inkflow.upload.domain.IdempotencyRecord
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

/**
 * Redis 기반 Idempotency 키 저장소 구현체.
 */
@Repository
class RedisIdempotencyKeyRepository(
    private val redisTemplate: RedisTemplate<String, IdempotencyRecord>,
    private val properties: IdempotencyRedisProperties
) : IdempotencyKeyRepository {
    /**
     * Idempotency 키로 저장된 레코드를 조회한다.
     */
    override fun find(key: String): IdempotencyRecord? {
        return redisTemplate.opsForValue().get(resolveKey(key))
    }

    /**
     * Idempotency 레코드를 저장한다.
     */
    override fun save(record: IdempotencyRecord, ttl: Duration) {
        redisTemplate.opsForValue().set(resolveKey(record.key), record, ttl)
    }

    /**
     * 키가 없을 때만 레코드를 저장한다.
     */
    override fun saveIfAbsent(record: IdempotencyRecord, ttl: Duration): Boolean {
        return redisTemplate.opsForValue()
            .setIfAbsent(resolveKey(record.key), record, ttl) == true
    }

    /**
     * Idempotency 레코드를 삭제한다.
     */
    override fun delete(key: String) {
        redisTemplate.delete(resolveKey(key))
    }

    /**
     * Redis 저장용 키를 표준 prefix와 결합한다.
     */
    private fun resolveKey(key: String): String {
        return "${properties.keyPrefix}${key}"
    }
}
