package com.inkflow.common.outbox.infra.config

import com.inkflow.common.outbox.application.OutboxEventPublisher
import com.inkflow.common.outbox.application.OutboxRelayProperties
import com.inkflow.common.outbox.infra.publisher.LoggingOutboxEventPublisher
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Outbox Relay 구성 요소를 등록.
 */
@Configuration
@EnableConfigurationProperties(OutboxRelayProperties::class)
class OutboxRelayConfig {
    /**
     * Kafka 퍼블리셔가 없으면 로깅 기반 퍼블리셔를 기본으로 사용한다.
     */
    @Bean
    @ConditionalOnMissingBean(OutboxEventPublisher::class)
    fun outboxEventPublisher(): OutboxEventPublisher {
        return LoggingOutboxEventPublisher()
    }
}
