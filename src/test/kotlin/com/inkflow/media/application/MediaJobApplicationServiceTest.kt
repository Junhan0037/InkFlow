package com.inkflow.media.application

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.events.EventObjectMapperFactory
import com.inkflow.common.idempotency.InMemoryIdempotencyKeyRepository
import com.inkflow.common.outbox.domain.OutboxEvent
import com.inkflow.common.outbox.domain.OutboxEventRepository
import com.inkflow.media.domain.DerivativeMetadata
import com.inkflow.media.domain.DerivativeMetadataRepository
import com.inkflow.upload.domain.AssetMetadata
import com.inkflow.upload.domain.AssetMetadataRepository
import com.inkflow.upload.domain.AssetStatus
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * MediaJobApplicationService의 썸네일 파이프라인 동작을 검증한다.
 */
class MediaJobApplicationServiceTest {
    private val baseTime: Instant = Instant.parse("2024-01-01T00:00:00Z")
    private val fixedClock: Clock = Clock.fixed(baseTime, ZoneOffset.UTC)

    /**
     * 정상 흐름에서 썸네일 생성과 파생 메타 저장이 수행되는지 확인한다.
     */
    @Test
    fun handleJob_createsThumbnailAndRecordsResult() {
        // 준비: 저장 완료된 Asset과 테스트용 의존성을 구성한다.
        val asset = buildStoredAsset(assetId = 1L, contentType = "image/png")
        val assetRepository = InMemoryAssetMetadataRepository(listOf(asset))
        val storageClient = CapturingMediaStorageClient(
            downloadObject = MediaStorageObject(
                contentType = "image/png",
                bytes = byteArrayOf(1, 2, 3)
            )
        )
        val imageProcessor = CapturingMediaImageProcessor(
            result = MediaThumbnailResult(
                bytes = byteArrayOf(9, 9, 9),
                contentType = "image/jpeg",
                format = "jpg",
                width = 120,
                height = 90
            )
        )
        val derivativeRepository = InMemoryDerivativeMetadataRepository()
        val outboxRepository = InMemoryOutboxEventRepository()
        val objectMapper = EventObjectMapperFactory.defaultObjectMapper()
        val derivativeResultService = MediaDerivativeResultService(
            derivativeMetadataRepository = derivativeRepository,
            outboxEventRepository = outboxRepository,
            objectMapper = objectMapper,
            clock = fixedClock
        )
        val idempotencyService = MediaJobIdempotencyService(
            idempotencyKeyRepository = InMemoryIdempotencyKeyRepository(),
            properties = MediaJobIdempotencyProperties(),
            clock = fixedClock
        )
        val thumbnailProperties = MediaThumbnailProperties(
            storageBucket = "derived-bucket",
            storageKeyPrefix = "thumbs"
        )
        val service = MediaJobApplicationService(
            assetMetadataRepository = assetRepository,
            derivativeMetadataRepository = derivativeRepository,
            mediaStorageClient = storageClient,
            mediaImageProcessor = imageProcessor,
            thumbnailProperties = thumbnailProperties,
            mediaDerivativeResultService = derivativeResultService,
            mediaJobIdempotencyService = idempotencyService
        )
        val command = MediaJobCommand(
            jobId = "job-1",
            assetId = asset.id!!,
            derivativeType = "THUMBNAIL",
            spec = MediaJobSpec(width = 120, height = 90, format = "jpg")
        )
        val metadata = MediaJobMessageMetadata(
            eventId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            traceId = "trace-1",
            idempotencyKey = "idem-1"
        )

        // 실행: Media 작업을 처리한다.
        service.handleJob(command, metadata)

        // 검증: 스토리지에서 원본 다운로드/썸네일 업로드가 호출됐는지 확인한다.
        assertEquals(MediaStorageLocation("source-bucket", "origin/key.png"), storageClient.downloadLocation)
        assertEquals(MediaStorageLocation("derived-bucket", "thumbs/1/job-1_120x90.jpg"), storageClient.uploadLocation)
        assertEquals("image/jpeg", storageClient.uploadContentType)
        assertNotNull(storageClient.uploadBytes)
        assertArrayEquals(byteArrayOf(9, 9, 9), storageClient.uploadBytes)
        assertNotNull(imageProcessor.receivedBytes)
        assertArrayEquals(byteArrayOf(1, 2, 3), imageProcessor.receivedBytes)
        assertEquals(command.spec, imageProcessor.receivedSpec)

        // 검증: 파생 메타/Outbox 기록이 생성됐는지 확인한다.
        assertEquals(1, derivativeRepository.saved.size)
        val savedDerivative = derivativeRepository.saved.first()
        assertEquals("thumbs/1/job-1_120x90.jpg", savedDerivative.storageKey)
        assertEquals(1, outboxRepository.saved.size)
        assertTrue(outboxRepository.saved.first().payload.contains("MEDIA_JOB_COMPLETED"))
    }

    /**
     * Asset이 없으면 NOT_FOUND 오류가 발생하는지 확인한다.
     */
    @Test
    fun handleJob_throwsWhenAssetMissing() {
        // 준비: 비어 있는 저장소와 서비스 인스턴스를 구성한다.
        val assetRepository = InMemoryAssetMetadataRepository(emptyList())
        val service = buildService(assetRepository)
        val command = buildCommand(assetId = 99L)

        // 실행 및 검증: Asset 미존재 오류가 발생한다.
        val exception = assertThrows(BusinessException::class.java) {
            service.handleJob(command, buildMetadata())
        }
        assertEquals(ErrorCode.NOT_FOUND, exception.errorCode)
    }

    /**
     * Asset 상태가 STORED가 아니면 INVALID_STATE 오류가 발생하는지 확인한다.
     */
    @Test
    fun handleJob_throwsWhenAssetStateInvalid() {
        // 준비: PENDING 상태의 Asset을 구성한다.
        val asset = buildStoredAsset(assetId = 2L, contentType = "image/png", status = AssetStatus.PENDING)
        val assetRepository = InMemoryAssetMetadataRepository(listOf(asset))
        val service = buildService(assetRepository)

        // 실행 및 검증: 상태 오류가 발생한다.
        val exception = assertThrows(BusinessException::class.java) {
            service.handleJob(buildCommand(assetId = 2L), buildMetadata())
        }
        assertEquals(ErrorCode.INVALID_STATE, exception.errorCode)
    }

    /**
     * 이미지가 아닌 Asset은 INVALID_REQUEST 오류로 처리되는지 확인한다.
     */
    @Test
    fun handleJob_throwsWhenAssetNotImage() {
        // 준비: 이미지가 아닌 Asset을 구성한다.
        val asset = buildStoredAsset(assetId = 3L, contentType = "application/pdf")
        val assetRepository = InMemoryAssetMetadataRepository(listOf(asset))
        val service = buildService(assetRepository)

        // 실행 및 검증: 요청 오류가 발생한다.
        val exception = assertThrows(BusinessException::class.java) {
            service.handleJob(buildCommand(assetId = 3L), buildMetadata())
        }
        assertEquals(ErrorCode.INVALID_REQUEST, exception.errorCode)
    }

    /**
     * 아직 지원하지 않는 파생 타입은 INVALID_REQUEST로 거절되는지 확인한다.
     */
    @Test
    fun handleJob_throwsWhenUnsupportedDerivativeType() {
        // 준비: RESIZED 파생 타입을 요청한다.
        val asset = buildStoredAsset(assetId = 4L, contentType = "image/png")
        val assetRepository = InMemoryAssetMetadataRepository(listOf(asset))
        val service = buildService(assetRepository)
        val command = buildCommand(assetId = 4L, derivativeType = "RESIZED")

        // 실행 및 검증: 지원하지 않는 타입 오류가 발생한다.
        val exception = assertThrows(BusinessException::class.java) {
            service.handleJob(command, buildMetadata())
        }
        assertEquals(ErrorCode.INVALID_REQUEST, exception.errorCode)
    }

    /**
     * 저장 완료된 Asset 테스트 데이터를 생성한다.
     */
    private fun buildStoredAsset(
        assetId: Long,
        contentType: String,
        status: AssetStatus = AssetStatus.STORED
    ): AssetMetadata {
        return AssetMetadata(
            id = assetId,
            episodeId = 10L,
            creatorId = "creator-1",
            uploadId = "upload-1",
            fileName = "origin.png",
            contentType = contentType,
            size = 1024,
            checksum = "checksum",
            storageBucket = "source-bucket",
            storageKey = "origin/key.png",
            status = status,
            createdAt = baseTime,
            updatedAt = baseTime
        )
    }

    /**
     * Media 작업 커맨드를 구성한다.
     */
    private fun buildCommand(
        assetId: Long,
        derivativeType: String = "THUMBNAIL"
    ): MediaJobCommand {
        return MediaJobCommand(
            jobId = "job-1",
            assetId = assetId,
            derivativeType = derivativeType,
            spec = MediaJobSpec(width = 120, height = 90, format = "jpg")
        )
    }

    /**
     * Media 작업 메시지 메타데이터를 구성한다.
     */
    private fun buildMetadata(): MediaJobMessageMetadata {
        return MediaJobMessageMetadata(
            eventId = UUID.fromString("00000000-0000-0000-0000-000000000010"),
            traceId = "trace-1",
            idempotencyKey = "idem-1"
        )
    }

    /**
     * 기본 MediaJobApplicationService 인스턴스를 생성한다.
     */
    private fun buildService(assetRepository: AssetMetadataRepository): MediaJobApplicationService {
        val storageClient = CapturingMediaStorageClient(
            downloadObject = MediaStorageObject(
                contentType = "image/png",
                bytes = byteArrayOf(1)
            )
        )
        val imageProcessor = CapturingMediaImageProcessor(
            result = MediaThumbnailResult(
                bytes = byteArrayOf(2),
                contentType = "image/jpeg",
                format = "jpg",
                width = 120,
                height = 90
            )
        )
        val derivativeRepository = InMemoryDerivativeMetadataRepository()
        val outboxRepository = InMemoryOutboxEventRepository()
        val derivativeResultService = MediaDerivativeResultService(
            derivativeMetadataRepository = derivativeRepository,
            outboxEventRepository = outboxRepository,
            objectMapper = EventObjectMapperFactory.defaultObjectMapper(),
            clock = fixedClock
        )
        val idempotencyService = MediaJobIdempotencyService(
            idempotencyKeyRepository = InMemoryIdempotencyKeyRepository(),
            properties = MediaJobIdempotencyProperties(),
            clock = fixedClock
        )
        return MediaJobApplicationService(
            assetMetadataRepository = assetRepository,
            derivativeMetadataRepository = derivativeRepository,
            mediaStorageClient = storageClient,
            mediaImageProcessor = imageProcessor,
            thumbnailProperties = MediaThumbnailProperties(),
            mediaDerivativeResultService = derivativeResultService,
            mediaJobIdempotencyService = idempotencyService
        )
    }

    /**
     * 테스트용 Asset 저장소 구현체.
     */
    private class InMemoryAssetMetadataRepository(
        assets: List<AssetMetadata>
    ) : AssetMetadataRepository {
        private val assetStore = assets.associateBy { it.id!! }.toMutableMap()
        private val uploadIndex = assets.associate { it.uploadId to it.id!! }.toMutableMap()

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
         * Asset ID로 Asset을 조회한다.
         */
        override fun findById(assetId: Long): AssetMetadata? {
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
        var uploadContentType: String? = null
            private set
        var uploadBytes: ByteArray? = null
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
            uploadContentType = contentType
            uploadBytes = bytes
        }
    }

    /**
     * 썸네일 생성 입력 값을 추적하는 테스트용 이미지 프로세서.
     */
    private class CapturingMediaImageProcessor(
        private val result: MediaThumbnailResult
    ) : MediaImageProcessor {
        var receivedBytes: ByteArray? = null
            private set
        var receivedSpec: MediaJobSpec? = null
            private set

        /**
         * 호출 정보를 기록하고 미리 정의한 결과를 반환한다.
         */
        override fun createThumbnail(originalBytes: ByteArray, spec: MediaJobSpec): MediaThumbnailResult {
            receivedBytes = originalBytes
            receivedSpec = spec
            return result
        }
    }

    /**
     * 파생 메타 저장 요청을 기록하는 테스트용 저장소.
     */
    private class InMemoryDerivativeMetadataRepository : DerivativeMetadataRepository {
        val saved = mutableListOf<DerivativeMetadata>()

        /**
         * 저장된 파생 메타를 식별자와 함께 반환한다.
         */
        override fun save(derivative: DerivativeMetadata): DerivativeMetadata {
            val stored = derivative.copy(id = (saved.size + 1).toLong())
            saved.add(stored)
            return stored
        }

        /**
         * 테스트 환경에서는 동일 스펙 조회를 지원하지 않는다.
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
         * 테스트에서 사용하지 않는 조회 메서드는 빈 결과를 반환한다.
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
