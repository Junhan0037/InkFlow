package com.inkflow.indexing.infra.kafka

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.common.events.EventEnvelope
import com.inkflow.common.events.EventObjectMapperFactory
import com.inkflow.common.events.EventType
import com.inkflow.indexing.application.IndexEventTypes
import com.inkflow.indexing.application.IndexRequestedEventPayload
import com.inkflow.indexing.application.IndexingApplicationService
import com.inkflow.indexing.application.IndexingCommand
import com.inkflow.indexing.application.IndexingMessageMetadata
import com.inkflow.indexing.domain.IndexEntityType
import com.inkflow.indexing.domain.IndexOperation
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.Instant
import java.util.UUID

/**
 * IndexEventConsumer의 이벤트 분기와 예외 처리 흐름을 검증한다.
 */
class IndexEventConsumerTest {
    private val baseTime: Instant = Instant.parse("2024-01-01T00:00:00Z")
    private val objectMapper = EventObjectMapperFactory.defaultObjectMapper()

    /**
     * 처리 대상이 아닌 이벤트 타입은 무시되는지 확인한다.
     */
    @Test
    fun consume_ignoresNonTargetEventType() {
        val service = Mockito.mock(IndexingApplicationService::class.java)
        val consumer = IndexEventConsumer(objectMapper, service)
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
        val consumer = IndexEventConsumer(objectMapper, service)
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
     * 비즈니스 예외는 컨슈머에서 전파하지 않는지 확인한다.
     */
    @Test
    fun consume_swallowBusinessException() {
        val service = Mockito.mock(IndexingApplicationService::class.java)
        val consumer = IndexEventConsumer(objectMapper, service)
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

        assertDoesNotThrow {
            consumer.consume(message)
        }
    }

    /**
     * 시스템 예외는 재시도를 위해 전파되는지 확인한다.
     */
    @Test
    fun consume_rethrowsSystemException() {
        val service = Mockito.mock(IndexingApplicationService::class.java)
        val consumer = IndexEventConsumer(objectMapper, service)
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
     * 테스트용 Kafka 메시지 JSON을 생성한다.
     */
    private fun buildMessage(
        eventType: EventType,
        payload: IndexRequestedEventPayload = buildPayload(),
        eventId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000200")
    ): String {
        val envelope = EventEnvelope.create(
            eventType = eventType,
            producer = "indexing-test",
            payload = payload,
            traceId = "trace-1",
            idempotencyKey = "idem-1",
            eventId = eventId,
            occurredAt = baseTime
        )
        return objectMapper.writeValueAsString(envelope)
    }
}
