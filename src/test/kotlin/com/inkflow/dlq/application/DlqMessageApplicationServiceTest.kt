package com.inkflow.dlq.application

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.common.events.EventObjectMapperFactory
import com.inkflow.common.kafka.config.InkflowKafkaProperties
import com.inkflow.dlq.domain.DlqMessage
import com.inkflow.dlq.domain.DlqMessageRepository
import com.inkflow.dlq.domain.DlqMessageStatus
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.kafka.support.SendResult
import java.nio.ByteBuffer
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture

/**
 * DLQ 메시지 적재/재처리 애플리케이션 서비스의 핵심 흐름을 검증.
 */
class DlqMessageApplicationServiceTest {
    private val baseTime: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val clock: Clock = Clock.fixed(baseTime, ZoneOffset.UTC)
    private val objectMapper = EventObjectMapperFactory.defaultObjectMapper()
    private val kafkaProperties = InkflowKafkaProperties()

    /**
     * DLQ 레코드를 적재하면 원본 정보와 이벤트 메타데이터가 저장되는지 확인한다.
     */
    @Test
    fun capture_storesMessageAndParsesMetadata() {
        // 준비: DLQ 레코드와 서비스를 구성한다.
        val repository = InMemoryDlqMessageRepository()
        val kafkaTemplate = buildKafkaTemplate()
        val service = buildService(repository, kafkaTemplate)
        val payload = buildPayload(eventId = "evt-1", eventType = "MEDIA_JOB_CREATED.v1")
        val record = buildRecord(payload)

        // 실행: DLQ 메시지를 적재한다.
        val saved = service.capture(record)

        // 검증: 원본 토픽/오프셋/에러 정보가 정상 저장된다.
        assertEquals("media.jobs", saved.originalTopic)
        assertEquals("media.jobs:2:42", saved.sourceKey)
        assertEquals("MEDIA_JOB_CREATED.v1", saved.eventType)
        assertEquals("evt-1", saved.eventId)
        assertEquals(DlqMessageStatus.PENDING, saved.status)
        assertEquals("java.lang.RuntimeException", saved.errorType)
        assertEquals("boom", saved.errorMessage)
        assertEquals(baseTime, saved.storedAt)
        assertNotNull(saved.id)
    }

    /**
     * 동일한 sourceKey가 존재하면 중복 적재 없이 기존 값을 반환하는지 확인한다.
     */
    @Test
    fun capture_returnsExistingWhenSourceKeyDuplicated() {
        // 준비: 동일한 sourceKey를 가진 메시지를 저장한다.
        val repository = InMemoryDlqMessageRepository()
        val existing = repository.save(buildDlqMessage(sourceKey = "media.jobs:2:42"))
        val service = buildService(repository, buildKafkaTemplate())
        val record = buildRecord(buildPayload(eventId = "evt-dup", eventType = "MEDIA_JOB_CREATED.v1"))

        // 실행: 동일한 sourceKey로 적재를 시도한다.
        val captured = service.capture(record)

        // 검증: 기존 메시지가 반환되고 저장 건수가 증가하지 않는다.
        assertEquals(existing.id, captured.id)
        assertEquals(1, repository.items.size)
    }

    /**
     * 재처리 성공 시 상태가 REPROCESSED로 갱신되는지 확인한다.
     */
    @Test
    fun reprocess_publishesAndUpdatesStatus() {
        // 준비: 재처리 대상 메시지를 저장하고 Kafka 전송을 성공으로 설정한다.
        val repository = InMemoryDlqMessageRepository()
        val saved = repository.save(buildDlqMessage(messageKey = "key-1"))
        val kafkaTemplate = buildKafkaTemplate()
        val sendResult = Mockito.mock(SendResult::class.java) as SendResult<String, String>
        val future = CompletableFuture.completedFuture(sendResult)
        // 메시지 키가 있는 재처리 경로를 검증하기 위해 null을 허용하지 않는다.
        val messageKey = requireNotNull(saved.messageKey) { "테스트 픽스처는 메시지 키가 필요합니다." }
        Mockito.doReturn(future)
            .`when`(kafkaTemplate)
            .send(saved.originalTopic, messageKey, saved.payload)
        val service = buildService(repository, kafkaTemplate)

        // 실행: 재처리 요청을 수행한다.
        val result = service.reprocess(
            DlqReprocessCommand(
                messageId = saved.id.orEmpty(),
                requestedBy = "ops-1",
                reason = "retry"
            )
        )

        // 검증: 메시지 상태와 재처리 횟수가 갱신된다.
        val updated = repository.findById(saved.id.orEmpty())!!
        assertEquals(DlqMessageStatus.REPROCESSED, updated.status)
        assertEquals(1, updated.reprocessCount)
        assertEquals(DlqMessageStatus.REPROCESSED, result.status)
        assertEquals(1, result.reprocessCount)
    }

    /**
     * 재처리 실패 시 FAILED 상태로 변경되고 예외가 전파되는지 확인한다.
     */
    @Test
    fun reprocess_marksFailedAndThrows_whenPublishFails() {
        // 준비: 메시지를 저장하고 Kafka 전송 실패를 시뮬레이션한다.
        val repository = InMemoryDlqMessageRepository()
        val saved = repository.save(buildDlqMessage(messageKey = null))
        val kafkaTemplate = buildKafkaTemplate()
        val future = CompletableFuture<SendResult<String, String>>()
        future.completeExceptionally(RuntimeException("broker down"))
        Mockito.doReturn(future)
            .`when`(kafkaTemplate)
            .send(saved.originalTopic, saved.payload)
        val service = buildService(repository, kafkaTemplate)

        // 실행 및 검증: SystemException이 발생하고 상태가 FAILED로 바뀐다.
        val exception = assertThrows(SystemException::class.java) {
            service.reprocess(
                DlqReprocessCommand(
                    messageId = saved.id.orEmpty(),
                    requestedBy = "ops-2",
                    reason = "retry"
                )
            )
        }
        assertEquals(ErrorCode.DEPENDENCY_FAILURE, exception.errorCode)
        val updated = repository.findById(saved.id.orEmpty())!!
        assertEquals(DlqMessageStatus.FAILED, updated.status)
        // 비동기 전송 실패 메시지는 래핑될 수 있으므로 핵심 원인을 포함하는지 확인한다.
        assertTrue(updated.lastReprocessError?.contains("broker down") == true)
    }

    /**
     * 검색 파라미터가 잘못되면 예외를 반환하는지 확인한다.
     */
    @Test
    fun search_throwsWhenPageSizeOutOfRange() {
        // 준비: 서비스와 잘못된 검색 조건을 준비한다.
        val service = buildService(InMemoryDlqMessageRepository(), buildKafkaTemplate())
        val criteria = DlqSearchCriteria(
            status = null,
            originalTopic = null,
            page = 0,
            size = 500
        )

        // 실행 및 검증: INVALID_REQUEST 예외가 발생한다.
        val exception = assertThrows(BusinessException::class.java) {
            service.search(criteria)
        }
        assertEquals(ErrorCode.INVALID_REQUEST, exception.errorCode)
    }

    /**
     * 테스트 대상 서비스를 생성한다.
     */
    private fun buildService(
        repository: DlqMessageRepository,
        kafkaTemplate: KafkaTemplate<String, String>
    ): DlqMessageApplicationService {
        return DlqMessageApplicationService(
            repository = repository,
            payloadParser = DlqMessagePayloadParser(objectMapper),
            kafkaTemplate = kafkaTemplate,
            kafkaProperties = kafkaProperties,
            clock = clock
        )
    }

    /**
     * 테스트용 KafkaTemplate 목을 생성한다.
     */
    private fun buildKafkaTemplate(): KafkaTemplate<String, String> {
        return Mockito.mock(KafkaTemplate::class.java) as KafkaTemplate<String, String>
    }

    /**
     * DLQ 이벤트 페이로드를 구성한다.
     */
    private fun buildPayload(
        eventId: String,
        eventType: String,
        producer: String = "media-worker",
        traceId: String = "trace-1",
        idempotencyKey: String = "idem-1",
        occurredAt: Instant = baseTime
    ): String {
        return """{"eventId":"$eventId","eventType":"$eventType","producer":"$producer","traceId":"$traceId","idempotencyKey":"$idempotencyKey","occurredAt":"$occurredAt"}"""
    }

    /**
     * DLQ 헤더가 포함된 Kafka 레코드를 생성한다.
     */
    private fun buildRecord(
        payload: String,
        dlqTopic: String = "dlq.media.jobs",
        originalTopic: String = "media.jobs",
        originalPartition: Int = 2,
        originalOffset: Long = 42,
        originalTimestamp: Instant = baseTime,
        messageKey: String = "key-1"
    ): ConsumerRecord<String, String> {
        val record = ConsumerRecord<String, String>(dlqTopic, 0, 10L, messageKey, payload)
        record.headers().add(KafkaHeaders.DLT_ORIGINAL_TOPIC, originalTopic.toByteArray(Charsets.UTF_8))
        record.headers().add(
            KafkaHeaders.DLT_ORIGINAL_PARTITION,
            ByteBuffer.allocate(4).putInt(originalPartition).array()
        )
        record.headers().add(
            KafkaHeaders.DLT_ORIGINAL_OFFSET,
            ByteBuffer.allocate(8).putLong(originalOffset).array()
        )
        record.headers().add(
            KafkaHeaders.DLT_ORIGINAL_TIMESTAMP,
            ByteBuffer.allocate(8).putLong(originalTimestamp.toEpochMilli()).array()
        )
        record.headers().add(KafkaHeaders.DLT_EXCEPTION_FQCN, "java.lang.RuntimeException".toByteArray(Charsets.UTF_8))
        record.headers().add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, "boom".toByteArray(Charsets.UTF_8))
        record.headers().add(KafkaHeaders.DLT_EXCEPTION_STACKTRACE, "stack".toByteArray(Charsets.UTF_8))
        return record
    }

    /**
     * 기본 DLQ 메시지 도메인 객체를 생성한다.
     */
    private fun buildDlqMessage(
        sourceKey: String = "media.jobs:2:42",
        messageKey: String? = "key-1",
        status: DlqMessageStatus = DlqMessageStatus.PENDING,
        storedAt: Instant = baseTime
    ): DlqMessage {
        return DlqMessage(
            id = null,
            sourceKey = sourceKey,
            dlqTopic = "dlq.media.jobs",
            originalTopic = "media.jobs",
            originalPartition = 2,
            originalOffset = 42,
            originalTimestamp = baseTime,
            messageKey = messageKey,
            payload = "{\"eventId\":\"evt-1\"}",
            headers = emptyMap(),
            eventId = "evt-1",
            eventType = "MEDIA_JOB_CREATED.v1",
            producer = "media-worker",
            traceId = "trace-1",
            idempotencyKey = "idem-1",
            occurredAt = baseTime,
            errorType = "java.lang.RuntimeException",
            errorMessage = "boom",
            errorStacktrace = "stack",
            status = status,
            reprocessCount = 0,
            lastReprocessedAt = null,
            lastReprocessBy = null,
            lastReprocessReason = null,
            lastReprocessError = null,
            storedAt = storedAt
        )
    }

    /**
     * 테스트 전용 인메모리 DLQ 저장소.
     */
    private class InMemoryDlqMessageRepository : DlqMessageRepository {
        val items: MutableList<DlqMessage> = mutableListOf()
        private var sequence: Long = 0L

        /**
         * 메시지를 저장하거나 업데이트한다.
         */
        override fun save(message: DlqMessage): DlqMessage {
            val id = message.id ?: "dlq-${++sequence}"
            val stored = message.copy(id = id)
            items.removeIf { it.id == id || it.sourceKey == stored.sourceKey }
            items.add(stored)
            return stored
        }

        /**
         * ID로 메시지를 조회한다.
         */
        override fun findById(id: String): DlqMessage? {
            return items.firstOrNull { it.id == id }
        }

        /**
         * sourceKey로 메시지를 조회한다.
         */
        override fun findBySourceKey(sourceKey: String): DlqMessage? {
            return items.firstOrNull { it.sourceKey == sourceKey }
        }

        /**
         * 조건에 맞는 메시지를 페이지 형태로 조회한다.
         */
        override fun search(
            status: DlqMessageStatus?,
            originalTopic: String?,
            pageable: Pageable
        ): Page<DlqMessage> {
            val filtered = items.filter { message ->
                val statusMatch = status == null || message.status == status
                val topicMatch = originalTopic.isNullOrBlank() || message.originalTopic == originalTopic
                statusMatch && topicMatch
            }
            val sorted = filtered.sortedByDescending { it.storedAt }
            val start = pageable.offset.toInt()
            val end = (start + pageable.pageSize).coerceAtMost(sorted.size)
            val content = if (start >= sorted.size) emptyList() else sorted.subList(start, end)
            return PageImpl(content, pageable, filtered.size.toLong())
        }
    }
}
