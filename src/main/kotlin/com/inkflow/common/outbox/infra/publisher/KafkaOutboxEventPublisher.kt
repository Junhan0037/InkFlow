package com.inkflow.common.outbox.infra.publisher

import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.common.outbox.application.OutboxEventPublisher
import com.inkflow.common.outbox.domain.OutboxEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate

/**
 * Outbox 이벤트를 Kafka로 발행하는 퍼블리셔.
 */
class KafkaOutboxEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val topicResolver: OutboxEventTopicResolver
) : OutboxEventPublisher {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Kafka 발행 결과를 동기적으로 확인해 Outbox 상태 갱신의 정합성을 보장한다.
     */
    override fun publish(event: OutboxEvent) {
        val topic = topicResolver.resolveTopic(event)
        val key = topicResolver.resolveKey(event)

        try {
            val result = kafkaTemplate.send(topic, key, event.payload).get()
            logger.debug(
                "Outbox 이벤트 Kafka 발행 완료. eventId={}, topic={}, partition={}, offset={}",
                event.id,
                topic,
                result.recordMetadata.partition(),
                result.recordMetadata.offset()
            )
        } catch (exception: InterruptedException) {
            // 인터럽트 상태를 복구해 상위 로직이 인지하도록 한다.
            Thread.currentThread().interrupt()
            throw buildPublishException(event, topic, exception, "Kafka 발행이 중단되었습니다.")
        } catch (exception: Exception) {
            throw buildPublishException(event, topic, exception, "Kafka 발행에 실패했습니다.")
        }
    }

    /**
     * Kafka 발행 실패에 대한 표준 SystemException을 생성한다.
     */
    private fun buildPublishException(
        event: OutboxEvent,
        topic: String,
        cause: Exception,
        message: String
    ): SystemException {
        return SystemException(
            errorCode = ErrorCode.DEPENDENCY_FAILURE,
            details = mapOf(
                "eventId" to event.id.toString(),
                "eventType" to event.eventType,
                "topic" to topic
            ),
            message = message,
            cause = cause
        )
    }
}
