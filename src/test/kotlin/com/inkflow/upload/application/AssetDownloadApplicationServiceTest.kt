package com.inkflow.upload.application

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.upload.domain.AssetMetadata
import com.inkflow.upload.domain.AssetMetadataRepository
import com.inkflow.upload.domain.AssetStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong

/**
 * Asset 다운로드 presigned URL 발급 로직을 검증한다.
 */
class AssetDownloadApplicationServiceTest {
    /**
     * 권한과 상태가 정상이라면 presigned URL을 반환한다.
     */
    @Test
    fun issueDownloadUrl_returnsPresignedUrl() {
        // 준비: 저장된 Asset 메타데이터를 구성한다.
        val assetRepository = InMemoryAssetMetadataRepository()
        val asset = AssetMetadata(
            id = 1L,
            episodeId = 1L,
            creatorId = "creator-1",
            uploadId = "upl-1",
            fileName = "image.png",
            contentType = "image/png",
            size = 10L,
            checksum = "checksum-1",
            storageBucket = "bucket",
            storageKey = "uploads/creator-1/1/upl-1/image.png",
            status = AssetStatus.STORED,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-01T00:00:00Z")
        )
        assetRepository.save(asset)
        val service = buildService(assetRepository = assetRepository)

        // 실행: 다운로드 URL 발급을 요청한다.
        val command = AssetDownloadCommand(assetId = 1L, requesterId = "creator-1")
        val result = service.issueDownloadUrl(command)

        // 검증: 반환된 URL과 메타 정보를 확인한다.
        assertEquals(1L, result.assetId)
        assertEquals("image.png", result.fileName)
        assertEquals("image/png", result.contentType)
        assertEquals(10L, result.size)
        assertNotNull(result.url)
    }

    /**
     * 요청자가 다른 경우 다운로드 권한이 거부된다.
     */
    @Test
    fun issueDownloadUrl_deniesWhenRequesterIsDifferent() {
        // 준비: 저장된 Asset 메타데이터를 구성한다.
        val assetRepository = InMemoryAssetMetadataRepository()
        val asset = AssetMetadata(
            id = 1L,
            episodeId = 1L,
            creatorId = "creator-1",
            uploadId = "upl-1",
            fileName = "image.png",
            contentType = "image/png",
            size = 10L,
            checksum = "checksum-1",
            storageBucket = "bucket",
            storageKey = "uploads/creator-1/1/upl-1/image.png",
            status = AssetStatus.STORED,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-01T00:00:00Z")
        )
        assetRepository.save(asset)
        val service = buildService(assetRepository = assetRepository)

        // 실행/검증: 다른 사용자가 요청하면 FORBIDDEN 예외가 발생한다.
        val command = AssetDownloadCommand(assetId = 1L, requesterId = "creator-2")
        val exception = assertThrows(BusinessException::class.java) {
            service.issueDownloadUrl(command)
        }
        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode)
    }

    /**
     * 저장 상태가 아니라면 다운로드가 거부된다.
     */
    @Test
    fun issueDownloadUrl_rejectsWhenStatusIsNotStored() {
        // 준비: 저장 상태가 아닌 Asset 메타데이터를 구성한다.
        val assetRepository = InMemoryAssetMetadataRepository()
        val asset = AssetMetadata(
            id = 1L,
            episodeId = 1L,
            creatorId = "creator-1",
            uploadId = "upl-1",
            fileName = "image.png",
            contentType = "image/png",
            size = 10L,
            checksum = "checksum-1",
            storageBucket = "bucket",
            storageKey = "uploads/creator-1/1/upl-1/image.png",
            status = AssetStatus.FAILED,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-01T00:00:00Z")
        )
        assetRepository.save(asset)
        val service = buildService(assetRepository = assetRepository)

        // 실행/검증: 저장 상태가 아니면 INVALID_STATE 예외가 발생한다.
        val command = AssetDownloadCommand(assetId = 1L, requesterId = "creator-1")
        val exception = assertThrows(BusinessException::class.java) {
            service.issueDownloadUrl(command)
        }
        assertEquals(ErrorCode.INVALID_STATE, exception.errorCode)
    }

    /**
     * 테스트용 서비스 구성을 생성한다.
     */
    private fun buildService(
        assetRepository: AssetMetadataRepository = InMemoryAssetMetadataRepository(),
        presignedProvider: PresignedDownloadUrlProvider = StubPresignedDownloadUrlProvider(),
        properties: AssetDownloadProperties = AssetDownloadProperties(ttl = Duration.ofMinutes(10)),
        clock: Clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
    ): AssetDownloadApplicationService {
        return AssetDownloadApplicationService(
            assetMetadataRepository = assetRepository,
            presignedDownloadUrlProvider = presignedProvider,
            properties = properties,
            clock = clock
        )
    }

    /**
     * 인메모리 Asset 메타데이터 저장소.
     */
    private class InMemoryAssetMetadataRepository : AssetMetadataRepository {
        private val sequence = AtomicLong(1)
        private val byId = mutableMapOf<Long, AssetMetadata>()
        private val byUploadId = mutableMapOf<String, Long>()

        /**
         * Asset 메타데이터를 저장한다.
         */
        override fun save(asset: AssetMetadata): AssetMetadata {
            val id = asset.id ?: sequence.getAndIncrement()
            val stored = asset.copy(id = id)
            byId[id] = stored
            byUploadId[stored.uploadId] = id
            return stored
        }

        /**
         * 업로드 ID로 Asset 메타데이터를 조회한다.
         */
        override fun findByUploadId(uploadId: String): AssetMetadata? {
            return byUploadId[uploadId]?.let { byId[it] }
        }

        /**
         * Asset ID로 메타데이터를 조회한다.
         */
        override fun findById(assetId: Long): AssetMetadata? {
            return byId[assetId]
        }
    }

    /**
     * 고정된 URL을 반환하는 presigned URL 테스트 더블.
     */
    private class StubPresignedDownloadUrlProvider : PresignedDownloadUrlProvider {
        /**
         * 다운로드 presigned URL을 생성한다.
         */
        override fun createPresignedDownload(
            bucket: String,
            key: String,
            contentType: String,
            expiresAt: Instant
        ): PresignedDownloadUrl {
            return PresignedDownloadUrl(url = "https://example.com/download")
        }
    }
}
