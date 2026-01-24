package com.inkflow.common.outbox.infra.publisher

import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.common.kafka.config.InkflowKafkaProperties
import com.inkflow.common.outbox.domain.OutboxEvent
import com.inkflow.common.outbox.domain.OutboxEventStatus
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import io.micrometer.observation.Observation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Kafka Outbox 퍼블리셔 동작을 검증한다.
 */
class KafkaOutboxEventPublisherTest {
    /**
     * 발행 성공 시 토픽/키/페이로드가 전달되는지 확인한다.
     */
    @Test
    fun publish_sendsEventWithResolvedTopicAndKey() {
        // 준비: 성공 응답을 반환하는 KafkaTemplate을 구성한다.
        val properties = InkflowKafkaProperties()
        val resolver = OutboxEventTopicResolver(properties)
        val template = TestKafkaTemplate { record ->
            val metadata = RecordMetadata(
                TopicPartition(record.topic(), 0),
                0L,
                0,
                System.currentTimeMillis(),
                record.key().toByteArray().size,
                record.value().toByteArray().size
            )
            CompletableFuture.completedFuture(SendResult(record, metadata))
        }
        val publisher = KafkaOutboxEventPublisher(template, resolver)
        val event = buildEvent("asset-1", "ASSET_STORED.v1")

        // 실행: Kafka 발행을 수행한다.
        publisher.publish(event)

        // 검증: 토픽/키/페이로드가 올바르게 전달된다.
        assertEquals(properties.topics.assetEvents, template.lastTopic)
        assertEquals(event.aggregateId, template.lastKey)
        assertEquals(event.payload, template.lastPayload)
    }

    /**
     * 발행 실패 시 SystemException이 발생하는지 확인한다.
     */
    @Test
    fun publish_throwsSystemException_whenSendFails() {
        // 준비: 예외를 발생시키는 KafkaTemplate을 구성한다.
        val properties = InkflowKafkaProperties()
        val resolver = OutboxEventTopicResolver(properties)
        val template = TestKafkaTemplate { _ ->
            val future = CompletableFuture<SendResult<String, String>>()
            future.completeExceptionally(IllegalStateException("boom"))
            future
        }
        val publisher = KafkaOutboxEventPublisher(template, resolver)
        val event = buildEvent("asset-9", "ASSET_STORED.v1")

        // 실행/검증: 의존성 장애 코드가 포함된 예외가 발생한다.
        val exception = assertThrows(SystemException::class.java) {
            publisher.publish(event)
        }
        assertEquals(ErrorCode.DEPENDENCY_FAILURE, exception.errorCode)
        assertEquals(event.id.toString(), exception.details["eventId"])
        assertEquals(event.eventType, exception.details["eventType"])
        assertEquals(properties.topics.assetEvents, exception.details["topic"])
    }

    /**
     * 테스트용 Outbox 이벤트를 생성한다.
     */
    private fun buildEvent(aggregateId: String, eventType: String): OutboxEvent {
        return OutboxEvent(
            id = UUID.randomUUID(),
            aggregateType = "ASSET",
            aggregateId = aggregateId,
            eventType = eventType,
            payload = """{"assetId":"$aggregateId"}""",
            status = OutboxEventStatus.PENDING,
            retryCount = 0,
            nextRetryAt = null,
            lastError = null,
            lockedAt = null,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            sentAt = null
        )
    }

    /**
     * KafkaTemplate의 send 동작을 제어하기 위한 테스트 대역.
     */
    private class TestKafkaTemplate(
        private val behavior: (ProducerRecord<String, String>) -> CompletableFuture<SendResult<String, String>>
    ) : KafkaTemplate<String, String>(DefaultKafkaProducerFactory(emptyMap())) {
        var lastTopic: String? = null
        var lastKey: String? = null
        var lastPayload: String? = null

        /**
         * send 호출 정보를 기록하고 지정된 결과를 반환한다.
         */
        override fun doSend(
            record: ProducerRecord<String, String>,
            observation: Observation
        ): CompletableFuture<SendResult<String, String>> {
            lastTopic = record.topic()
            lastKey = record.key()
            lastPayload = record.value()
            return behavior(record)
        }
    }
}
