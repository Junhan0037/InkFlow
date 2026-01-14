package com.inkflow.upload.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.outbox.domain.OutboxEvent
import com.inkflow.common.outbox.domain.OutboxEventRepository
import com.inkflow.upload.domain.AssetMetadata
import com.inkflow.upload.domain.AssetMetadataRepository
import com.inkflow.upload.domain.AssetStatus
import com.inkflow.upload.domain.EpisodeAccessRepository
import com.inkflow.upload.domain.UploadSession
import com.inkflow.upload.domain.UploadSessionCacheRepository
import com.inkflow.upload.domain.UploadSessionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong

/**
 * 업로드 세션 애플리케이션 서비스의 핵심 동작을 검증한다.
 */
class UploadSessionApplicationServiceTest {
    /**
     * 정상 요청이면 업로드 세션이 저장되고 presigned URL이 반환된다.
     */
    @Test
    fun createSession_savesSessionAndReturnsUrls() {
        // 준비: 업로드 세션 저장소와 presigned 발급기를 구성한다.
        val uploadSessionRepository = InMemoryUploadSessionRepository()
        val cacheRepository = InMemoryUploadSessionCacheRepository()
        val assetRepository = InMemoryAssetMetadataRepository()
        val outboxRepository = InMemoryOutboxEventRepository()
        val episodeAccessRepository = InMemoryEpisodeAccessRepository(mapOf(1L to "creator-1"))
        val presignedProvider = StubMultipartPresignedUrlProvider()
        val completer = CountingMultipartUploadCompleter()
        val service = buildService(
            uploadSessionRepository = uploadSessionRepository,
            cacheRepository = cacheRepository,
            assetRepository = assetRepository,
            outboxRepository = outboxRepository,
            episodeAccessRepository = episodeAccessRepository,
            presignedProvider = presignedProvider,
            completer = completer
        )

        // 실행: 업로드 세션 생성 요청을 수행한다.
        val command = CreateUploadSessionCommand(
            episodeId = 1L,
            creatorId = "creator-1",
            fileName = "uploads/image.png",
            contentType = "image/png",
            size = 64L,
            checksum = "checksum-1",
            totalParts = 1
        )
        val result = service.createSession(command)

        // 검증: 세션 저장과 반환값을 확인한다.
        val stored = uploadSessionRepository.findByUploadId(result.uploadId)
        assertNotNull(stored)
        assertEquals("image.png", stored!!.fileName)
        assertTrue(stored.storageKey.endsWith("/image.png"))
        assertEquals(1, result.presignedUrls.size)
        assertNotNull(cacheRepository.findByUploadId(result.uploadId))
    }

    /**
     * 허용되지 않은 확장자는 업로드 세션 생성이 거부된다.
     */
    @Test
    fun createSession_rejectsInvalidExtension() {
        // 준비: 확장자 정책을 좁혀 검증한다.
        val service = buildService(
            validationProperties = UploadValidationProperties(
                minFileSize = 1L,
                maxFileSize = 100L,
                allowedExtensions = setOf("png")
            ),
            episodeAccessRepository = InMemoryEpisodeAccessRepository(mapOf(1L to "creator-1"))
        )

        // 실행/검증: 허용되지 않은 확장자를 전달하면 예외가 발생한다.
        val command = CreateUploadSessionCommand(
            episodeId = 1L,
            creatorId = "creator-1",
            fileName = "image.exe",
            contentType = "application/octet-stream",
            size = 10L,
            checksum = "checksum-2",
            totalParts = 1
        )
        val exception = assertThrows(BusinessException::class.java) {
            service.createSession(command)
        }
        assertEquals(ErrorCode.INVALID_REQUEST, exception.errorCode)
    }

    /**
     * 파일 크기가 제한을 초과하면 업로드 세션 생성이 거부된다.
     */
    @Test
    fun createSession_rejectsOversizeFile() {
        // 준비: 최대 파일 크기를 50으로 설정한다.
        val service = buildService(
            validationProperties = UploadValidationProperties(
                minFileSize = 1L,
                maxFileSize = 50L,
                allowedExtensions = setOf("png")
            ),
            episodeAccessRepository = InMemoryEpisodeAccessRepository(mapOf(1L to "creator-1"))
        )

        // 실행/검증: 최대 크기를 초과하면 예외가 발생한다.
        val command = CreateUploadSessionCommand(
            episodeId = 1L,
            creatorId = "creator-1",
            fileName = "image.png",
            contentType = "image/png",
            size = 51L,
            checksum = "checksum-3",
            totalParts = 1
        )
        val exception = assertThrows(BusinessException::class.java) {
            service.createSession(command)
        }
        assertEquals(ErrorCode.INVALID_REQUEST, exception.errorCode)
    }

    /**
     * 에피소드 소유자가 다르면 업로드 권한이 거부된다.
     */
    @Test
    fun createSession_deniesWhenEpisodeOwnerMismatch() {
        // 준비: 에피소드 소유자를 다른 사용자로 설정한다.
        val service = buildService(
            episodeAccessRepository = InMemoryEpisodeAccessRepository(mapOf(1L to "creator-2"))
        )

        // 실행/검증: 소유자가 다르면 FORBIDDEN 예외가 발생한다.
        val command = CreateUploadSessionCommand(
            episodeId = 1L,
            creatorId = "creator-1",
            fileName = "image.png",
            contentType = "image/png",
            size = 10L,
            checksum = "checksum-4",
            totalParts = 1
        )
        val exception = assertThrows(BusinessException::class.java) {
            service.createSession(command)
        }
        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode)
    }

    /**
     * 업로드 완료 시 Asset 저장과 Outbox 이벤트 기록이 수행된다.
     */
    @Test
    fun completeSession_createsAssetAndOutbox() {
        // 준비: 저장소에 업로드 세션을 미리 등록한다.
        val uploadSessionRepository = InMemoryUploadSessionRepository()
        val cacheRepository = InMemoryUploadSessionCacheRepository()
        val assetRepository = InMemoryAssetMetadataRepository()
        val outboxRepository = InMemoryOutboxEventRepository()
        val completer = CountingMultipartUploadCompleter()
        val service = buildService(
            uploadSessionRepository = uploadSessionRepository,
            cacheRepository = cacheRepository,
            assetRepository = assetRepository,
            outboxRepository = outboxRepository,
            completer = completer
        )
        val session = UploadSession.create(
            uploadId = "upl-1",
            episodeId = 1L,
            creatorId = "creator-1",
            fileName = "image.png",
            contentType = "image/png",
            totalSize = 100L,
            checksum = "checksum-5",
            totalParts = 2,
            chunkSize = 50L,
            storageBucket = "bucket",
            storageKey = "uploads/creator-1/1/upl-1/image.png",
            multipartUploadId = "mpu-1",
            expiresAt = Instant.parse("2024-01-02T00:00:00Z"),
            now = Instant.parse("2024-01-01T00:00:00Z")
        )
        uploadSessionRepository.save(session)

        // 실행: 업로드 완료 요청을 수행한다.
        val command = CompleteUploadSessionCommand(
            uploadId = "upl-1",
            creatorId = "creator-1",
            uploadedParts = listOf(
                CompletedPart(partNumber = 1, etag = "etag-1"),
                CompletedPart(partNumber = 2, etag = "etag-2")
            ),
            checksum = "checksum-5"
        )
        val result = service.completeSession(command)

        // 검증: Asset 저장과 Outbox 이벤트 생성 결과를 확인한다.
        val storedAsset = assetRepository.findById(result.assetId)
        assertNotNull(storedAsset)
        assertEquals(AssetStatus.STORED, storedAsset!!.status)
        val updatedSession = uploadSessionRepository.findByUploadId("upl-1")
        assertEquals("COMPLETED", updatedSession!!.status.name)
        assertEquals(1, outboxRepository.events.size)
        assertEquals(1, completer.callCount)
    }

    /**
     * 동일 업로드 완료 요청은 기존 Asset을 반환하고 Outbox 이벤트를 중복 생성하지 않는다.
     */
    @Test
    fun completeSession_isIdempotentWhenAssetExists() {
        // 준비: 업로드 세션과 저장소를 초기화한다.
        val uploadSessionRepository = InMemoryUploadSessionRepository()
        val cacheRepository = InMemoryUploadSessionCacheRepository()
        val assetRepository = InMemoryAssetMetadataRepository()
        val outboxRepository = InMemoryOutboxEventRepository()
        val completer = CountingMultipartUploadCompleter()
        val service = buildService(
            uploadSessionRepository = uploadSessionRepository,
            cacheRepository = cacheRepository,
            assetRepository = assetRepository,
            outboxRepository = outboxRepository,
            completer = completer
        )
        val session = UploadSession.create(
            uploadId = "upl-2",
            episodeId = 1L,
            creatorId = "creator-1",
            fileName = "image.png",
            contentType = "image/png",
            totalSize = 100L,
            checksum = "checksum-6",
            totalParts = 2,
            chunkSize = 50L,
            storageBucket = "bucket",
            storageKey = "uploads/creator-1/1/upl-2/image.png",
            multipartUploadId = "mpu-2",
            expiresAt = Instant.parse("2024-01-02T00:00:00Z"),
            now = Instant.parse("2024-01-01T00:00:00Z")
        )
        uploadSessionRepository.save(session)

        // 실행: 동일한 업로드 완료 요청을 두 번 수행한다.
        val command = CompleteUploadSessionCommand(
            uploadId = "upl-2",
            creatorId = "creator-1",
            uploadedParts = listOf(
                CompletedPart(partNumber = 1, etag = "etag-1"),
                CompletedPart(partNumber = 2, etag = "etag-2")
            ),
            checksum = "checksum-6"
        )
        val first = service.completeSession(command)
        val second = service.completeSession(command)

        // 검증: Asset ID가 동일하며 Outbox 이벤트가 중복 생성되지 않는다.
        assertEquals(first.assetId, second.assetId)
        assertEquals(1, outboxRepository.events.size)
        assertEquals(1, completer.callCount)
    }

    /**
     * checksum 불일치 시 업로드 완료가 거부된다.
     */
    @Test
    fun completeSession_rejectsChecksumMismatch() {
        // 준비: 업로드 세션을 저장한다.
        val uploadSessionRepository = InMemoryUploadSessionRepository()
        val cacheRepository = InMemoryUploadSessionCacheRepository()
        val service = buildService(
            uploadSessionRepository = uploadSessionRepository,
            cacheRepository = cacheRepository
        )
        val session = UploadSession.create(
            uploadId = "upl-3",
            episodeId = 1L,
            creatorId = "creator-1",
            fileName = "image.png",
            contentType = "image/png",
            totalSize = 100L,
            checksum = "checksum-7",
            totalParts = 1,
            chunkSize = 100L,
            storageBucket = "bucket",
            storageKey = "uploads/creator-1/1/upl-3/image.png",
            multipartUploadId = "mpu-3",
            expiresAt = Instant.parse("2024-01-02T00:00:00Z"),
            now = Instant.parse("2024-01-01T00:00:00Z")
        )
        uploadSessionRepository.save(session)

        // 실행/검증: checksum이 다르면 예외가 발생한다.
        val command = CompleteUploadSessionCommand(
            uploadId = "upl-3",
            creatorId = "creator-1",
            uploadedParts = listOf(CompletedPart(partNumber = 1, etag = "etag-1")),
            checksum = "checksum-wrong"
        )
        val exception = assertThrows(BusinessException::class.java) {
            service.completeSession(command)
        }
        assertEquals(ErrorCode.INVALID_REQUEST, exception.errorCode)
    }

    /**
     * 테스트용 서비스 구성을 생성한다.
     */
    private fun buildService(
        uploadSessionRepository: UploadSessionRepository = InMemoryUploadSessionRepository(),
        cacheRepository: UploadSessionCacheRepository = InMemoryUploadSessionCacheRepository(),
        presignedProvider: MultipartPresignedUrlProvider = StubMultipartPresignedUrlProvider(),
        completer: MultipartUploadCompleter = CountingMultipartUploadCompleter(),
        assetRepository: AssetMetadataRepository = InMemoryAssetMetadataRepository(),
        episodeAccessRepository: EpisodeAccessRepository = InMemoryEpisodeAccessRepository(mapOf(1L to "creator-1")),
        outboxRepository: OutboxEventRepository = InMemoryOutboxEventRepository(),
        sessionProperties: UploadSessionProperties = UploadSessionProperties(
            ttl = Duration.ofMinutes(30),
            minChunkSize = 1,
            maxPartCount = 10_000,
            storageBucket = "bucket",
            storageKeyPrefix = "uploads/"
        ),
        validationProperties: UploadValidationProperties = UploadValidationProperties(
            minFileSize = 1L,
            maxFileSize = 1_000L,
            allowedExtensions = setOf("png")
        ),
        clock: Clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC)
    ): UploadSessionApplicationService {
        return UploadSessionApplicationService(
            uploadSessionRepository = uploadSessionRepository,
            uploadSessionCacheRepository = cacheRepository,
            presignedUrlProvider = presignedProvider,
            multipartUploadCompleter = completer,
            assetMetadataRepository = assetRepository,
            episodeAccessRepository = episodeAccessRepository,
            outboxEventRepository = outboxRepository,
            // Instant 직렬화를 위해 JavaTime 모듈을 등록한다.
            objectMapper = jacksonObjectMapper().findAndRegisterModules(),
            properties = sessionProperties,
            validationProperties = validationProperties,
            clock = clock
        )
    }

    /**
     * 인메모리 업로드 세션 저장소.
     */
    private class InMemoryUploadSessionRepository : UploadSessionRepository {
        private val store = mutableMapOf<String, UploadSession>()

        /**
         * 업로드 세션을 저장한다.
         */
        override fun save(session: UploadSession): UploadSession {
            store[session.uploadId] = session
            return session
        }

        /**
         * 업로드 ID로 세션을 조회한다.
         */
        override fun findByUploadId(uploadId: String): UploadSession? {
            return store[uploadId]
        }
    }

    /**
     * 인메모리 업로드 세션 캐시 저장소.
     */
    private class InMemoryUploadSessionCacheRepository : UploadSessionCacheRepository {
        private val store = mutableMapOf<String, UploadSession>()

        /**
         * 업로드 세션을 저장한다.
         */
        override fun save(session: UploadSession): UploadSession {
            store[session.uploadId] = session
            return session
        }

        /**
         * 업로드 ID로 세션을 조회한다.
         */
        override fun findByUploadId(uploadId: String): UploadSession? {
            return store[uploadId]
        }

        /**
         * 업로드 세션을 삭제한다.
         */
        override fun deleteByUploadId(uploadId: String): Boolean {
            return store.remove(uploadId) != null
        }
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
     * 인메모리 Outbox 이벤트 저장소.
     */
    private class InMemoryOutboxEventRepository : OutboxEventRepository {
        val events = mutableListOf<OutboxEvent>()

        /**
         * Outbox 이벤트를 저장한다.
         */
        override fun save(event: OutboxEvent): OutboxEvent {
            events.add(event)
            return event
        }

        /**
         * 테스트에서는 Relay 처리를 하지 않으므로 빈 목록을 반환한다.
         */
        override fun findPendingEventsForUpdate(
            limit: Int,
            now: Instant,
            lockExpiredBefore: Instant
        ): List<OutboxEvent> {
            return emptyList()
        }

        /**
         * 테스트 범위에서 사용하지 않는 전송 중 갱신 동작.
         */
        override fun markSending(eventId: java.util.UUID, lockedAt: Instant) {
            // 업로드 서비스 테스트에서는 전송 상태를 다루지 않는다.
        }

        /**
         * 테스트 범위에서 사용하지 않는 전송 완료 갱신 동작.
         */
        override fun markSent(eventId: java.util.UUID, sentAt: Instant) {
            // 업로드 서비스 테스트에서는 전송 상태를 다루지 않는다.
        }

        /**
         * 테스트 범위에서 사용하지 않는 재시도 갱신 동작.
         */
        override fun markRetry(
            eventId: java.util.UUID,
            retryCount: Int,
            nextRetryAt: Instant,
            lastError: String?
        ) {
            // 업로드 서비스 테스트에서는 재시도 상태를 다루지 않는다.
        }

        /**
         * 테스트 범위에서 사용하지 않는 실패 갱신 동작.
         */
        override fun markFailed(eventId: java.util.UUID, lastError: String?) {
            // 업로드 서비스 테스트에서는 실패 상태를 다루지 않는다.
        }
    }

    /**
     * 에피소드 소유자 정보를 반환하는 인메모리 저장소.
     */
    private class InMemoryEpisodeAccessRepository(
        private val owners: Map<Long, String>
    ) : EpisodeAccessRepository {
        /**
         * 에피소드 ID로 creatorId를 조회한다.
         */
        override fun findCreatorIdByEpisodeId(episodeId: Long): String? {
            return owners[episodeId]
        }
    }

    /**
     * 고정된 presigned URL을 반환하는 테스트 더블.
     */
    private class StubMultipartPresignedUrlProvider : MultipartPresignedUrlProvider {
        /**
         * presigned URL과 업로드 ID를 반환한다.
         */
        override fun createPresignedMultipart(
            bucket: String,
            key: String,
            totalParts: Int,
            expiresAt: Instant
        ): MultipartPresignedUpload {
            return MultipartPresignedUpload(
                multipartUploadId = "mpu-test",
                presignedUrls = (1..totalParts).map { part ->
                    PresignedPartUrl(partNumber = part, url = "https://example.com/$part")
                }
            )
        }
    }

    /**
     * 호출 횟수를 기록하는 멀티파트 완료 처리 테스트 더블.
     */
    private class CountingMultipartUploadCompleter : MultipartUploadCompleter {
        var callCount: Int = 0
            private set

        /**
         * 멀티파트 완료 요청을 처리하고 고정 ETag를 반환한다.
         */
        override fun completeMultipartUpload(
            bucket: String,
            key: String,
            uploadId: String,
            parts: List<CompletedPart>
        ): CompletedMultipartUpload {
            callCount += 1
            return CompletedMultipartUpload(etag = "etag-final")
        }
    }
}
