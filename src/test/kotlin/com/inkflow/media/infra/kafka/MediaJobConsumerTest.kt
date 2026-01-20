package com.inkflow.media.infra.kafka

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.common.events.EventEnvelope
import com.inkflow.common.events.EventObjectMapperFactory
import com.inkflow.common.events.EventType
import com.inkflow.common.idempotency.ConsumerIdempotencyProperties
import com.inkflow.common.idempotency.ConsumerIdempotencyService
import com.inkflow.common.idempotency.InMemoryIdempotencyKeyRepository
import com.inkflow.common.kafka.dlq.DlqPublisher
import com.inkflow.common.outbox.domain.OutboxEvent
import com.inkflow.common.outbox.domain.OutboxEventRepository
import com.inkflow.media.application.MediaDerivativeResultService
import com.inkflow.media.application.MediaImageProcessor
import com.inkflow.media.application.MediaJobApplicationService
import com.inkflow.media.application.MediaJobCreatedEventPayload
import com.inkflow.media.application.MediaJobEventTypes
import com.inkflow.media.application.MediaJobFailureHandler
import com.inkflow.media.application.MediaJobRetryPolicy
import com.inkflow.media.application.MediaJobRetryProperties
import com.inkflow.media.application.MediaJobIdempotencyProperties
import com.inkflow.media.application.MediaJobIdempotencyService
import com.inkflow.media.application.MediaJobSpec
import com.inkflow.media.application.MediaStorageClient
import com.inkflow.media.application.MediaStorageLocation
import com.inkflow.media.application.MediaStorageObject
import com.inkflow.media.application.MediaThumbnailProperties
import com.inkflow.media.application.MediaThumbnailResult
import com.inkflow.media.domain.DerivativeMetadata
import com.inkflow.media.domain.DerivativeMetadataRepository
import com.inkflow.media.domain.MediaJobFailureLog
import com.inkflow.media.domain.MediaJobFailureLogRepository
import com.inkflow.upload.domain.AssetMetadata
import com.inkflow.upload.domain.AssetMetadataRepository
import com.inkflow.upload.domain.AssetStatus
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.mockito.Mockito
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer

/**
 * MediaJobConsumer의 이벤트 분기와 실패 처리 흐름을 검증한다.
 */
class MediaJobConsumerTest {
    private val baseTime: Instant = Instant.parse("2024-01-01T00:00:00Z")
    private val clock: Clock = Clock.fixed(baseTime, ZoneOffset.UTC)
    private val objectMapper = EventObjectMapperFactory.defaultObjectMapper()

    /**
     * 대상 이벤트 타입이 아니면 처리 없이 종료되는지 확인한다.
     */
    @Test
    fun consume_ignoresNonTargetEventType() {
        // 준비: 정상 처리 가능한 픽스처와 다른 이벤트 타입 메시지를 구성한다.
        val fixture = buildFixture(NoopMediaImageProcessor())
        val message = buildMessage(MediaJobEventTypes.MEDIA_JOB_COMPLETED)

        // 실행: 컨슈머가 메시지를 처리한다.
        fixture.consumer.consume(message)

        // 검증: Asset 조회가 발생하지 않으며 실패 로그도 기록되지 않는다.
        assertFalse(fixture.assetRepository.findByIdCalled)
        assertEquals(0, fixture.failureLogRepository.saved.size)
    }

    /**
     * 비재시도 오류는 실패 로그만 기록하고 예외를 전파하지 않는지 확인한다.
     */
    @Test
    fun consume_recordsFailureAndSkipsRetry_whenNonRetryable() {
        // 준비: 비즈니스 예외를 던지는 프로세서를 구성한다.
        val throwingProcessor = ThrowingMediaImageProcessor(
            BusinessException(errorCode = ErrorCode.INVALID_REQUEST, message = "잘못된 요청")
        )
        val fixture = buildFixture(throwingProcessor)
        val message = buildMessage(MediaJobEventTypes.MEDIA_JOB_CREATED)

        // 실행 및 검증: 예외가 전파되지 않는다.
        assertDoesNotThrow {
            fixture.consumer.consume(message)
        }
        assertEquals(1, fixture.failureLogRepository.saved.size)
        assertFalse(fixture.failureLogRepository.saved.first().shouldRetry)
    }

    /**
     * 재시도 가능한 오류는 예외가 전파되는지 확인한다.
     */
    @Test
    fun consume_rethrowsWhenRetryableException() {
        // 준비: 시스템 예외를 던지는 프로세서를 구성한다.
        val throwingProcessor = ThrowingMediaImageProcessor(
            SystemException(errorCode = ErrorCode.INTERNAL_ERROR, message = "처리 실패")
        )
        val fixture = buildFixture(throwingProcessor)
        val message = buildMessage(MediaJobEventTypes.MEDIA_JOB_CREATED)

        // 실행 및 검증: 예외가 전파되고 실패 로그는 기록된다.
        val exception = assertThrows(SystemException::class.java) {
            fixture.consumer.consume(message)
        }
        assertEquals(ErrorCode.INTERNAL_ERROR, exception.errorCode)
        assertEquals(1, fixture.failureLogRepository.saved.size)
        assertTrue(fixture.failureLogRepository.saved.first().shouldRetry)
    }

    /**
     * 테스트용 컨슈머 픽스처를 구성한다.
     */
    private fun buildFixture(mediaImageProcessor: MediaImageProcessor): ConsumerFixture {
        val assetRepository = TrackingAssetMetadataRepository(listOf(buildStoredAsset()))
        val storageClient = CapturingMediaStorageClient(
            downloadObject = MediaStorageObject(
                contentType = "image/png",
                bytes = byteArrayOf(1, 2, 3)
            )
        )
        val derivativeRepository = InMemoryDerivativeMetadataRepository()
        val outboxRepository = InMemoryOutboxEventRepository()
        val derivativeResultService = MediaDerivativeResultService(
            derivativeMetadataRepository = derivativeRepository,
            outboxEventRepository = outboxRepository,
            objectMapper = objectMapper,
            clock = clock
        )
        val mediaJobIdempotencyService = MediaJobIdempotencyService(
            idempotencyKeyRepository = InMemoryIdempotencyKeyRepository(),
            properties = MediaJobIdempotencyProperties(),
            clock = clock
        )
        val mediaJobService = MediaJobApplicationService(
            assetMetadataRepository = assetRepository,
            derivativeMetadataRepository = derivativeRepository,
            mediaStorageClient = storageClient,
            mediaImageProcessor = mediaImageProcessor,
            thumbnailProperties = MediaThumbnailProperties(),
            mediaDerivativeResultService = derivativeResultService,
            mediaJobIdempotencyService = mediaJobIdempotencyService
        )
        val failureLogRepository = InMemoryMediaJobFailureLogRepository()
        val retryPolicy = MediaJobRetryPolicy(MediaJobRetryProperties(maxAttempts = 3))
        val failureHandler = MediaJobFailureHandler(
            failureLogRepository = failureLogRepository,
            retryPolicy = retryPolicy,
            clock = clock
        )
        val consumerIdempotencyService = ConsumerIdempotencyService(
            idempotencyKeyRepository = InMemoryIdempotencyKeyRepository(),
            properties = ConsumerIdempotencyProperties(),
            clock = clock
        )
        val dlqPublisher = buildDlqPublisher()
        val consumer = MediaJobConsumer(
            objectMapper = objectMapper,
            mediaJobApplicationService = mediaJobService,
            mediaJobFailureHandler = failureHandler,
            dlqPublisher = dlqPublisher,
            consumerIdempotencyService = consumerIdempotencyService
        )
        return ConsumerFixture(
            consumer = consumer,
            assetRepository = assetRepository,
            storageClient = storageClient,
            failureLogRepository = failureLogRepository
        )
    }

    /**
     * Kafka 메시지를 생성한다.
     */
    private fun buildMessage(eventType: EventType): ConsumerRecord<String, String> {
        val payload = MediaJobCreatedEventPayload(
            jobId = "job-1",
            assetId = 10L,
            derivativeType = "THUMBNAIL",
            spec = MediaJobSpec(width = 120, height = 90, format = "jpg")
        )
        val envelope = EventEnvelope.create(
            eventType = eventType,
            producer = "media-worker-test",
            payload = payload,
            traceId = "trace-1",
            idempotencyKey = "idem-1",
            eventId = UUID.fromString("00000000-0000-0000-0000-000000000444"),
            occurredAt = baseTime
        )
        val message = objectMapper.writeValueAsString(envelope)
        return ConsumerRecord("media.jobs", 0, 0L, "key-1", message)
    }

    /**
     * 저장 완료된 Asset 테스트 데이터를 생성한다.
     */
    private fun buildStoredAsset(): AssetMetadata {
        return AssetMetadata(
            id = 10L,
            episodeId = 100L,
            creatorId = "creator-1",
            uploadId = "upload-1",
            fileName = "origin.png",
            contentType = "image/png",
            size = 1024,
            checksum = "checksum",
            storageBucket = "source-bucket",
            storageKey = "origin/key.png",
            status = AssetStatus.STORED,
            createdAt = baseTime,
            updatedAt = baseTime
        )
    }

    /**
     * 테스트에서 사용하는 DLQ 퍼블리셔를 생성한다.
     */
    private fun buildDlqPublisher(): DlqPublisher {
        @Suppress("UNCHECKED_CAST")
        val kafkaTemplate = Mockito.mock(KafkaTemplate::class.java) as KafkaTemplate<String, String>
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { _, _ ->
            TopicPartition("dlq.media.jobs", 0)
        }
        return DlqPublisher(recoverer)
    }

    /**
     * 컨슈머 테스트에 필요한 구성 요소를 묶은 DTO.
     */
    private data class ConsumerFixture(
        val consumer: MediaJobConsumer,
        val assetRepository: TrackingAssetMetadataRepository,
        val storageClient: CapturingMediaStorageClient,
        val failureLogRepository: InMemoryMediaJobFailureLogRepository
    )

    /**
     * 테스트용 Asset 저장소로 호출 여부를 추적한다.
     */
    private class TrackingAssetMetadataRepository(
        assets: List<AssetMetadata>
    ) : AssetMetadataRepository {
        private val assetStore = assets.associateBy { it.id!! }.toMutableMap()
        private val uploadIndex = assets.associate { it.uploadId to it.id!! }.toMutableMap()
        var findByIdCalled: Boolean = false
            private set

        /**
         * Asset을 저장하고 식별자를 보장한다.
         */
        override fun save(asset: AssetMetadata): AssetMetadata {
            val resolvedId = asset.id ?: (assetStore.size + 1).toLong()
            val stored = asset.copy(id = resolvedId)
            assetStore[resolvedId] = stored
            uploadIndex[stored.uploadId] = resolvedId
            return stored
        }

        /**
         * 업로드 ID로 Asset을 조회한다.
         */
        override fun findByUploadId(uploadId: String): AssetMetadata? {
            val assetId = uploadIndex[uploadId] ?: return null
            return assetStore[assetId]
        }

        /**
         * Asset ID로 Asset을 조회하고 호출 여부를 기록한다.
         */
        override fun findById(assetId: Long): AssetMetadata? {
            findByIdCalled = true
            return assetStore[assetId]
        }
    }

    /**
     * Media 스토리지 호출 정보를 추적하는 테스트용 클라이언트.
     */
    private class CapturingMediaStorageClient(
        private val downloadObject: MediaStorageObject
    ) : MediaStorageClient {
        var downloadLocation: MediaStorageLocation? = null
            private set
        var uploadLocation: MediaStorageLocation? = null
            private set

        /**
         * 다운로드 요청 정보를 기록하고 고정된 객체를 반환한다.
         */
        override fun download(location: MediaStorageLocation): MediaStorageObject {
            downloadLocation = location
            return downloadObject
        }

        /**
         * 업로드 요청 정보를 기록한다.
         */
        override fun upload(location: MediaStorageLocation, contentType: String, bytes: ByteArray) {
            uploadLocation = location
        }
    }

    /**
     * 호출 시 예외를 던지는 테스트용 프로세서.
     */
    private class ThrowingMediaImageProcessor(
        private val exception: RuntimeException
    ) : MediaImageProcessor {
        /**
         * 생성 요청을 즉시 실패시킨다.
         */
        override fun createThumbnail(originalBytes: ByteArray, spec: MediaJobSpec): MediaThumbnailResult {
            throw exception
        }
    }

    /**
     * 정상 동작을 위한 테스트용 이미지 프로세서.
     */
    private class NoopMediaImageProcessor : MediaImageProcessor {
        /**
         * 고정된 썸네일 결과를 반환한다.
         */
        override fun createThumbnail(originalBytes: ByteArray, spec: MediaJobSpec): MediaThumbnailResult {
            return MediaThumbnailResult(
                bytes = byteArrayOf(1),
                contentType = "image/jpeg",
                format = "jpg",
                width = spec.width,
                height = spec.height
            )
        }
    }

    /**
     * 파생 메타 저장을 기록하는 테스트용 저장소.
     */
    private class InMemoryDerivativeMetadataRepository : DerivativeMetadataRepository {
        val saved = mutableListOf<DerivativeMetadata>()

        /**
         * 저장된 파생 메타를 반환한다.
         */
        override fun save(derivative: DerivativeMetadata): DerivativeMetadata {
            val stored = derivative.copy(id = (saved.size + 1).toLong())
            saved.add(stored)
            return stored
        }

        /**
         * 동일 스펙의 파생 메타를 조회한다.
         */
        override fun findBySpec(
            assetId: Long,
            type: com.inkflow.media.domain.DerivativeType,
            width: Int?,
            height: Int?,
            format: String
        ): DerivativeMetadata? {
            return saved.firstOrNull {
                it.assetId == assetId &&
                    it.type == type &&
                    it.width == width &&
                    it.height == height &&
                    it.format == format
            }
        }
    }

    /**
     * Outbox 이벤트 저장을 기록하는 테스트용 저장소.
     */
    private class InMemoryOutboxEventRepository : OutboxEventRepository {
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

    /**
     * 실패 로그 저장을 기록하는 테스트용 저장소.
     */
    private class InMemoryMediaJobFailureLogRepository : MediaJobFailureLogRepository {
        val saved = mutableListOf<MediaJobFailureLog>()
        private val counts = mutableMapOf<String, Long>()

        /**
         * 실패 로그를 저장하고 건수를 갱신한다.
         */
        override fun save(log: MediaJobFailureLog): MediaJobFailureLog {
            saved.add(log)
            counts[log.jobId] = (counts[log.jobId] ?: 0L) + 1L
            return log
        }

        /**
         * jobId 기준 실패 로그 건수를 반환한다.
         */
        override fun countByJobId(jobId: String): Long {
            return counts[jobId] ?: 0L
        }
    }
}
