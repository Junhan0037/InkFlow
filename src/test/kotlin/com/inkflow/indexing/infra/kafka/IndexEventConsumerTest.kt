package com.inkflow.indexing.infra.kafka

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.common.events.EventEnvelope
import com.inkflow.common.events.EventObjectMapperFactory
import com.inkflow.common.events.EventType
import com.inkflow.common.idempotency.ConsumerIdempotencyProperties
import com.inkflow.common.idempotency.ConsumerIdempotencyService
import com.inkflow.common.idempotency.InMemoryIdempotencyKeyRepository
import com.inkflow.indexing.application.IndexEventTypes
import com.inkflow.indexing.application.IndexRequestedEventPayload
import com.inkflow.indexing.application.IndexingApplicationService
import com.inkflow.indexing.application.IndexingCommand
import com.inkflow.indexing.application.IndexingMessageMetadata
import com.inkflow.indexing.domain.IndexEntityType
import com.inkflow.indexing.domain.IndexOperation
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * IndexEventConsumer의 이벤트 분기와 예외 처리 흐름을 검증한다.
 */
class IndexEventConsumerTest {
    private val baseTime: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val objectMapper = EventObjectMapperFactory.defaultObjectMapper()

    /**
     * 처리 대상이 아닌 이벤트 타입은 무시되는지 확인한다.
     */
    @Test
    fun consume_ignoresNonTargetEventType() {
        val service = Mockito.mock(IndexingApplicationService::class.java)
        val consumer = IndexEventConsumer(objectMapper, service, buildIdempotencyService())
        val message = buildMessage(eventType = EventType.of("WORK_UPDATED", 1))

        consumer.consume(message)

        Mockito.verifyNoInteractions(service)
    }

    /**
     * 색인 요청 이벤트가 서비스로 전달되는지 확인한다.
     */
    @Test
    fun consume_dispatchesIndexRequest() {
        val service = Mockito.mock(IndexingApplicationService::class.java)
        val consumer = IndexEventConsumer(objectMapper, service, buildIdempotencyService())
        val payload = IndexRequestedEventPayload(
            entityType = IndexEntityType.ASSET,
            entityId = 10L,
            operation = IndexOperation.UPSERT
        )
        val eventId = UUID.fromString("00000000-0000-0000-0000-000000000201")
        val message = buildMessage(
            eventType = IndexEventTypes.INDEX_REQUESTED,
            payload = payload,
            eventId = eventId
        )

        consumer.consume(message)

        val expectedCommand = IndexingCommand.from(payload)
        val expectedMetadata = IndexingMessageMetadata(
            eventId = eventId,
            traceId = "trace-1",
            idempotencyKey = "idem-1"
        )
        Mockito.verify(service).handleIndexRequest(expectedCommand, expectedMetadata)
    }

    /**
     * 비즈니스 예외는 DLQ 처리 흐름을 위해 전파되는지 확인한다.
     */
    @Test
    fun consume_rethrowsBusinessException() {
        val service = Mockito.mock(IndexingApplicationService::class.java)
        val consumer = IndexEventConsumer(objectMapper, service, buildIdempotencyService())
        val payload = buildPayload()
        val eventId = UUID.fromString("00000000-0000-0000-0000-000000000202")
        val message = buildMessage(IndexEventTypes.INDEX_REQUESTED, payload, eventId)
        val expectedCommand = IndexingCommand.from(payload)
        val expectedMetadata = IndexingMessageMetadata(
            eventId = eventId,
            traceId = "trace-1",
            idempotencyKey = "idem-1"
        )

        Mockito.doThrow(BusinessException(errorCode = ErrorCode.NOT_FOUND, message = "대상 없음"))
            .`when`(service)
            .handleIndexRequest(expectedCommand, expectedMetadata)

        // 비즈니스 오류는 에러 핸들러를 통해 DLQ로 이동해야 하므로 예외를 기대한다.
        assertThrows(BusinessException::class.java) {
            consumer.consume(message)
        }
    }

    /**
     * 시스템 예외는 재시도를 위해 전파되는지 확인한다.
     */
    @Test
    fun consume_rethrowsSystemException() {
        val service = Mockito.mock(IndexingApplicationService::class.java)
        val consumer = IndexEventConsumer(objectMapper, service, buildIdempotencyService())
        val payload = buildPayload()
        val eventId = UUID.fromString("00000000-0000-0000-0000-000000000203")
        val message = buildMessage(IndexEventTypes.INDEX_REQUESTED, payload, eventId)
        val expectedCommand = IndexingCommand.from(payload)
        val expectedMetadata = IndexingMessageMetadata(
            eventId = eventId,
            traceId = "trace-1",
            idempotencyKey = "idem-1"
        )

        Mockito.doThrow(SystemException(errorCode = ErrorCode.INTERNAL_ERROR, message = "시스템 장애"))
            .`when`(service)
            .handleIndexRequest(expectedCommand, expectedMetadata)

        assertThrows(SystemException::class.java) {
            consumer.consume(message)
        }
    }

    /**
     * 테스트용 이벤트 payload를 생성한다.
     */
    private fun buildPayload(): IndexRequestedEventPayload {
        return IndexRequestedEventPayload(
            entityType = IndexEntityType.WORK,
            entityId = 1L,
            operation = IndexOperation.UPSERT
        )
    }

    /**
     * 테스트용 Kafka 메시지를 생성한다.
     */
    private fun buildMessage(
        eventType: EventType,
        payload: IndexRequestedEventPayload = buildPayload(),
        eventId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000200")
    ): ConsumerRecord<String, String> {
        val envelope = EventEnvelope.create(
            eventType = eventType,
            producer = "indexing-test",
            payload = payload,
            traceId = "trace-1",
            idempotencyKey = "idem-1",
            eventId = eventId,
            occurredAt = baseTime
        )
        val message = objectMapper.writeValueAsString(envelope)
        return ConsumerRecord("index.events", 0, 0L, "key-1", message)
    }

    /**
     * 테스트용 컨슈머 멱등성 서비스를 생성한다.
     */
    private fun buildIdempotencyService(): ConsumerIdempotencyService {
        return ConsumerIdempotencyService(
            idempotencyKeyRepository = InMemoryIdempotencyKeyRepository(),
            properties = ConsumerIdempotencyProperties(),
            clock = Clock.systemUTC()
        )
    }
}
