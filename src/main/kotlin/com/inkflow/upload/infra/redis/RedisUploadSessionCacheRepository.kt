package com.inkflow.upload.infra.redis

import com.inkflow.upload.domain.UploadSession
import com.inkflow.upload.domain.UploadSessionCacheRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Redis TTL을 활용한 업로드 세션 캐시 저장소 구현체.
 */
@Repository
class RedisUploadSessionCacheRepository(
    private val redisTemplate: RedisTemplate<String, UploadSessionCache>,
    private val properties: UploadSessionRedisProperties,
    private val clock: Clock
) : UploadSessionCacheRepository {
    /**
     * 업로드 세션을 저장하고 만료 TTL을 갱신한다.
     */
    override fun save(session: UploadSession): UploadSession {
        val key = buildKey(session.uploadId)
        val ttl = calculateTtl(session.expiresAt)

        if (ttl.isZero || ttl.isNegative) {
            // 만료된 세션은 Redis에 남기지 않고 정리한다.
            redisTemplate.delete(key)
            return session
        }

        val cache = UploadSessionCache.fromDomain(session)
        redisTemplate.opsForValue().set(key, cache, ttl)
        return session
    }

    /**
     * 업로드 ID로 세션을 조회한다.
     */
    override fun findByUploadId(uploadId: String): UploadSession? {
        val key = buildKey(uploadId)
        val cached = redisTemplate.opsForValue().get(key) ?: return null
        return cached.toDomain()
    }

    /**
     * 업로드 ID로 세션을 제거한다.
     */
    override fun deleteByUploadId(uploadId: String): Boolean {
        val key = buildKey(uploadId)
        return redisTemplate.delete(key) == true
    }

    /**
     * Redis 키 규칙을 통일하기 위해 prefix를 적용한다.
     */
    private fun buildKey(uploadId: String): String {
        return "${properties.keyPrefix}$uploadId"
    }

    /**
     * 현재 시각 기준 만료까지 남은 TTL을 계산한다.
     */
    private fun calculateTtl(expiresAt: Instant): Duration {
        val now = Instant.now(clock)
        val ttl = Duration.between(now, expiresAt)
        // 음수 TTL은 저장 즉시 만료로 처리한다.
        return if (ttl.isNegative || ttl.isZero) Duration.ZERO else ttl
    }
}
