package com.inkflow.common.idempotency

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Kafka 컨슈머의 멱등성 처리 정책을 정의한다.
 */
@ConfigurationProperties("inkflow.kafka.consumer.idempotency")
data class ConsumerIdempotencyProperties(
    val processingTtl: Duration = Duration.ofMinutes(10),
    val completedTtl: Duration = Duration.ofHours(24),
    val keyPrefix: String = "consumer:"
)
