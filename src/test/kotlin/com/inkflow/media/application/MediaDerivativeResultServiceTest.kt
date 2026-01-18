package com.inkflow.media.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.common.events.EventEnvelope
import com.inkflow.common.events.EventObjectMapperFactory
import com.inkflow.common.outbox.domain.OutboxEvent
import com.inkflow.common.outbox.domain.OutboxEventRepository
import com.inkflow.media.domain.DerivativeMetadata
import com.inkflow.media.domain.DerivativeMetadataRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * MediaDerivativeResultService의 파생 메타/Outbox 기록 동작을 검증한다.
 */
class MediaDerivativeResultServiceTest {
    private val baseTime: Instant = Instant.parse("2024-01-01T00:00:00Z")
    private val clock: Clock = Clock.fixed(baseTime, ZoneOffset.UTC)

    /**
     * 썸네일 결과 기록 시 파생 메타와 Outbox 이벤트가 저장되는지 확인한다.
     */
    @Test
    fun recordThumbnailResult_savesDerivativeAndOutboxEvent() {
        // 준비: 저장소와 서비스 인스턴스를 구성한다.
        val derivativeRepository = CapturingDerivativeMetadataRepository()
        val outboxRepository = CapturingOutboxEventRepository()
        val objectMapper = EventObjectMapperFactory.defaultObjectMapper()
        val service = MediaDerivativeResultService(
            derivativeMetadataRepository = derivativeRepository,
            outboxEventRepository = outboxRepository,
            objectMapper = objectMapper,
            clock = clock
        )
        val command = MediaJobCommand(
            jobId = "job-1",
            assetId = 10L,
            derivativeType = "THUMBNAIL",
            spec = MediaJobSpec(width = 120, height = 90, format = "jpg")
        )
        val metadata = MediaJobMessageMetadata(
            eventId = UUID.fromString("00000000-0000-0000-0000-000000000111"),
            traceId = "trace-1",
            idempotencyKey = null
        )
        val thumbnailResult = MediaThumbnailResult(
            bytes = byteArrayOf(1, 2, 3),
            contentType = "image/jpeg",
            format = "jpg",
            width = 120,
            height = 90
        )

        // 실행: 결과 기록을 수행한다.
        val saved = service.recordThumbnailResult(
            command = command,
            metadata = metadata,
            storageKey = "thumbs/10/job-1_120x90.jpg",
            thumbnailResult = thumbnailResult,
            durationMs = 150
        )

        // 검증: 파생 메타가 저장되고 식별자가 부여되는지 확인한다.
        assertNotNull(saved.id)
        assertEquals(1, derivativeRepository.saved.size)
        val storedDerivative = derivativeRepository.saved.first()
        assertEquals("thumbs/10/job-1_120x90.jpg", storedDerivative.storageKey)
        assertEquals(120, storedDerivative.width)
        assertEquals(90, storedDerivative.height)

        // 검증: Outbox 이벤트가 저장되고 payload에 완료 이벤트가 포함되는지 확인한다.
        assertEquals(1, outboxRepository.saved.size)
        val outboxEvent = outboxRepository.saved.first()
        assertEquals("DERIVATIVE", outboxEvent.aggregateType)
        assertEquals(saved.id.toString(), outboxEvent.aggregateId)
        assertEquals(MediaJobEventTypes.MEDIA_JOB_COMPLETED.asString(), outboxEvent.eventType)

        val javaType = objectMapper.typeFactory
            .constructParametricType(EventEnvelope::class.java, MediaJobCompletedEventPayload::class.java)
        val envelope = objectMapper.readValue(outboxEvent.payload, javaType) as EventEnvelope<MediaJobCompletedEventPayload>
        assertEquals("media-worker", envelope.producer)
        assertEquals(command.jobId, envelope.idempotencyKey)
        assertEquals(metadata.traceId, envelope.traceId)
        assertEquals(saved.id, envelope.payload.derivativeId)
        assertEquals(150, envelope.payload.durationMs)
    }

    /**
     * 이벤트 직렬화 실패 시 시스템 예외가 발생하는지 확인한다.
     */
    @Test
    fun recordThumbnailResult_throwsSystemException_whenSerializationFails() {
        // 준비: 직렬화 실패를 유발하는 ObjectMapper를 구성한다.
        val derivativeRepository = CapturingDerivativeMetadataRepository()
        val outboxRepository = CapturingOutboxEventRepository()
        val brokenMapper = object : ObjectMapper() {
            /**
             * 직렬화를 강제로 실패시키기 위해 예외를 던진다.
             */
            override fun writeValueAsString(value: Any?): String {
                throw IllegalStateException("serialize failed")
            }
        }
        val service = MediaDerivativeResultService(
            derivativeMetadataRepository = derivativeRepository,
            outboxEventRepository = outboxRepository,
            objectMapper = brokenMapper,
            clock = clock
        )
        val command = MediaJobCommand(
            jobId = "job-2",
            assetId = 20L,
            derivativeType = "THUMBNAIL",
            spec = MediaJobSpec(width = 120, height = 90, format = "jpg")
        )
        val metadata = MediaJobMessageMetadata(
            eventId = UUID.fromString("00000000-0000-0000-0000-000000000222"),
            traceId = "trace-2",
            idempotencyKey = "idem-2"
        )
        val thumbnailResult = MediaThumbnailResult(
            bytes = byteArrayOf(4),
            contentType = "image/jpeg",
            format = "jpg",
            width = 120,
            height = 90
        )

        // 실행 및 검증: 시스템 오류가 발생한다.
        val exception = assertThrows(SystemException::class.java) {
            service.recordThumbnailResult(
                command = command,
                metadata = metadata,
                storageKey = "thumbs/20/job-2_120x90.jpg",
                thumbnailResult = thumbnailResult,
                durationMs = 10
            )
        }
        assertEquals(ErrorCode.INTERNAL_ERROR, exception.errorCode)
    }

    /**
     * 파생 메타 저장 요청을 기록하는 테스트용 저장소.
     */
    private class CapturingDerivativeMetadataRepository : DerivativeMetadataRepository {
        val saved = mutableListOf<DerivativeMetadata>()

        /**
         * 저장 시 식별자를 부여하고 기록한다.
         */
        override fun save(derivative: DerivativeMetadata): DerivativeMetadata {
            val stored = derivative.copy(id = (saved.size + 1).toLong())
            saved.add(stored)
            return stored
        }
    }

    /**
     * Outbox 이벤트 저장을 기록하는 테스트용 저장소.
     */
    private class CapturingOutboxEventRepository : OutboxEventRepository {
        val saved = mutableListOf<OutboxEvent>()

        /**
         * Outbox 이벤트를 저장한다.
         */
        override fun save(event: OutboxEvent): OutboxEvent {
            saved.add(event)
            return event
        }

        /**
         * 테스트 범위에서 사용하지 않는 조회는 빈 리스트로 반환한다.
         */
        override fun findPendingEventsForUpdate(limit: Int, now: Instant, lockExpiredBefore: Instant): List<OutboxEvent> {
            return emptyList()
        }

        /**
         * 테스트 범위에서 사용하지 않는 전송 상태 마킹은 무시한다.
         */
        override fun markSending(eventId: UUID, lockedAt: Instant) {
            // 테스트에서는 사용하지 않는다.
        }

        /**
         * 테스트 범위에서 사용하지 않는 전송 상태 마킹은 무시한다.
         */
        override fun markSent(eventId: UUID, sentAt: Instant) {
            // 테스트에서는 사용하지 않는다.
        }

        /**
         * 테스트 범위에서 사용하지 않는 재시도 마킹은 무시한다.
         */
        override fun markRetry(eventId: UUID, retryCount: Int, nextRetryAt: Instant, lastError: String?) {
            // 테스트에서는 사용하지 않는다.
        }

        /**
         * 테스트 범위에서 사용하지 않는 실패 마킹은 무시한다.
         */
        override fun markFailed(eventId: UUID, lastError: String?) {
            // 테스트에서는 사용하지 않는다.
        }
    }
}
