package com.inkflow.upload.infra.redis

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Idempotency 키 Redis 저장소 설정을 관리한다.
 */
@ConfigurationProperties("inkflow.upload.idempotency.redis")
data class IdempotencyRedisProperties(
    val keyPrefix: String = "upload:idempotency:"
)
