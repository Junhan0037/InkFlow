package com.inkflow.common.outbox.infra.config

import com.inkflow.common.kafka.config.InkflowKafkaProperties
import com.inkflow.common.outbox.application.OutboxEventPublisher
import com.inkflow.common.outbox.infra.publisher.KafkaOutboxEventPublisher
import com.inkflow.common.outbox.infra.publisher.OutboxEventTopicResolver
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate

/**
 * Kafka 기반 Outbox 퍼블리셔를 등록한다.
 */
@Configuration
@EnableConfigurationProperties(InkflowKafkaProperties::class)
class KafkaOutboxPublisherConfig {
    /**
     * Outbox 이벤트 토픽 라우팅 규칙을 빈으로 등록한다.
     */
    @Bean
    fun outboxEventTopicResolver(properties: InkflowKafkaProperties): OutboxEventTopicResolver {
        return OutboxEventTopicResolver(properties)
    }

    /**
     * Kafka 설정이 활성화된 경우 OutboxEventPublisher를 Kafka 구현체로 교체한다.
     */
    @Bean
    @ConditionalOnProperty(prefix = "inkflow.kafka", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean(KafkaTemplate::class)
    fun kafkaOutboxEventPublisher(
        kafkaTemplate: KafkaTemplate<String, String>,
        topicResolver: OutboxEventTopicResolver
    ): OutboxEventPublisher {
        return KafkaOutboxEventPublisher(kafkaTemplate, topicResolver)
    }
}
