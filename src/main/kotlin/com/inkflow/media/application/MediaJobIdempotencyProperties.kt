package com.inkflow.media.application

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Media 작업 멱등성 처리 정책을 정의.
 */
@ConfigurationProperties("inkflow.media.idempotency")
data class MediaJobIdempotencyProperties(
    val processingTtl: Duration = Duration.ofMinutes(30),
    val completedTtl: Duration = Duration.ofDays(7),
    val keyPrefix: String = "media:job:"
)
