package com.inkflow.common.kafka.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Kafka 컨슈머 재시도/백오프 설정을 바인딩.
 */
@ConfigurationProperties("inkflow.kafka.retry")
data class KafkaRetryProperties(
    val maxRetries: Int = 3,
    val initialInterval: Duration = Duration.ofSeconds(1),
    val maxInterval: Duration = Duration.ofSeconds(30),
    val multiplier: Double = 2.0
) {
    init {
        require(maxRetries >= 0) { "maxRetries는 0 이상이어야 합니다." }
        require(initialInterval.toMillis() > 0) { "initialInterval은 0보다 커야 합니다." }
        require(maxInterval >= initialInterval) { "maxInterval은 initialInterval 이상이어야 합니다." }
        require(multiplier >= 1.0) { "multiplier는 1.0 이상이어야 합니다." }
    }
}
