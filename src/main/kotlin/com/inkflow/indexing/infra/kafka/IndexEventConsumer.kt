package com.inkflow.indexing.infra.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.common.events.EventEnvelope
import com.inkflow.indexing.application.IndexEventTypes
import com.inkflow.indexing.application.IndexRequestedEventPayload
import com.inkflow.indexing.application.IndexingApplicationService
import com.inkflow.indexing.application.IndexingCommand
import com.inkflow.indexing.application.IndexingMessageMetadata
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * index.events 토픽에서 색인 요청 이벤트를 수신하는 Kafka 컨슈머.
 */
@Component
@ConditionalOnProperty(prefix = "inkflow.kafka", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "inkflow.indexing", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class IndexEventConsumer(
    private val objectMapper: ObjectMapper,
    private val indexingApplicationService: IndexingApplicationService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 색인 요청 이벤트를 수신해 처리 서비스로 전달한다.
     */
    @KafkaListener(
        topics = ["\${inkflow.kafka.topics.index-events}"],
        groupId = "\${inkflow.kafka.consumer.group-id:indexing-service}"
    )
    fun consume(record: ConsumerRecord<String, String>) {
        val envelope = deserializeEnvelope(record.value(), record.topic())
        if (envelope.eventType != IndexEventTypes.INDEX_REQUESTED) {
            // 예상하지 않은 이벤트 타입은 로그만 남기고 무시한다.
            logger.warn(
                "색인 컨슈머가 처리하지 않는 이벤트 타입을 수신했습니다. eventType={}, eventId={}",
                envelope.eventType.asString(),
                envelope.eventId
            )
            return
        }

        val command = IndexingCommand.from(envelope.payload)
        val metadata = IndexingMessageMetadata(
            eventId = envelope.eventId,
            traceId = envelope.traceId,
            idempotencyKey = envelope.idempotencyKey
        )
        try {
            indexingApplicationService.handleIndexRequest(command, metadata)
        } catch (exception: BusinessException) {
            // 비즈니스 오류는 재시도하지 않고 DLQ로 전달한다.
            logger.warn(
                "색인 처리 중 비즈니스 오류가 발생했습니다. entityType={}, entityId={}, eventId={}, errorCode={}",
                command.entityType,
                command.entityId,
                metadata.eventId,
                exception.errorCode.code
            )
            throw exception
        } catch (exception: Exception) {
            // 시스템 오류는 Kafka 재시도를 위해 예외를 전파한다.
            logger.error(
                "색인 처리 중 시스템 오류가 발생했습니다. entityType={}, entityId={}, eventId={}",
                command.entityType,
                command.entityId,
                metadata.eventId,
                exception
            )
            throw exception
        }
    }

    /**
     * Kafka 메시지를 EventEnvelope로 역직렬화한다.
     */
    private fun deserializeEnvelope(message: String, topic: String): EventEnvelope<IndexRequestedEventPayload> {
        return try {
            val javaType = objectMapper.typeFactory
                .constructParametricType(EventEnvelope::class.java, IndexRequestedEventPayload::class.java)
            objectMapper.readValue(message, javaType)
        } catch (exception: Exception) {
            throw SystemException(
                errorCode = ErrorCode.INTERNAL_ERROR,
                details = mapOf("topic" to topic),
                message = "색인 이벤트 역직렬화에 실패했습니다.",
                cause = exception
            )
        }
    }
}
