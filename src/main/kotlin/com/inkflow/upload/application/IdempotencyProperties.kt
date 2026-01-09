package com.inkflow.upload.application

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Idempotency 키 처리 정책을 정의하는 설정 값.
 */
@ConfigurationProperties("inkflow.upload.idempotency")
data class IdempotencyProperties(
    val ttl: Duration = Duration.ofHours(1)
)
