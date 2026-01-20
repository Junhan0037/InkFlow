package com.inkflow.common.kafka.config

import com.inkflow.common.error.BusinessException
import com.inkflow.common.idempotency.ConsumerIdempotencyProperties
import org.apache.kafka.common.TopicPartition
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.CommonErrorHandler
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler

/**
 * Kafka 컨슈머 공통 에러 처리와 DLQ 발행을 설정.
 */
@Configuration
@EnableConfigurationProperties(
    InkflowKafkaProperties::class,
    KafkaRetryProperties::class,
    ConsumerIdempotencyProperties::class
)
class KafkaListenerConfig(
    private val kafkaProperties: InkflowKafkaProperties,
    private val retryProperties: KafkaRetryProperties
) {
    /**
     * DLQ 토픽 규칙을 적용한 DeadLetterPublishingRecoverer를 구성한다.
     */
    @Bean
    fun deadLetterPublishingRecoverer(kafkaTemplate: KafkaTemplate<String, String>): DeadLetterPublishingRecoverer {
        return DeadLetterPublishingRecoverer(kafkaTemplate) { record, _ ->
            val dlqTopic = "${kafkaProperties.topics.dlqPrefix}.${record.topic()}"
            // 원본 파티션을 유지해 DLQ에서도 순서를 보존한다.
            TopicPartition(dlqTopic, record.partition())
        }
    }

    /**
     * 재시도/비재시도 예외 분류를 적용한 공통 에러 핸들러를 구성한다.
     */
    @Bean
    fun kafkaErrorHandler(recoverer: DeadLetterPublishingRecoverer): CommonErrorHandler {
        // 재시도 횟수와 지수 백오프를 동시에 보장하는 커스텀 백오프를 사용한다.
        val backOff = ExponentialBackOffWithMaxRetries(retryProperties.maxRetries).apply {
            initialInterval = retryProperties.initialInterval.toMillis()
            maxInterval = retryProperties.maxInterval.toMillis()
            multiplier = retryProperties.multiplier
        }

        return DefaultErrorHandler(recoverer, backOff).apply {
            // 비즈니스 오류는 즉시 DLQ로 전송한다.
            addNotRetryableExceptions(BusinessException::class.java)
        }
    }

    /**
     * KafkaListenerContainerFactory에 공통 에러 핸들러를 적용한다.
     */
    @Bean
    fun kafkaListenerContainerFactory(
        configurer: ConcurrentKafkaListenerContainerFactoryConfigurer,
        consumerFactory: ConsumerFactory<Any, Any>,
        errorHandler: CommonErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<Any, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<Any, Any>()
        configurer.configure(factory, consumerFactory)
        factory.setCommonErrorHandler(errorHandler)
        return factory
    }
}
