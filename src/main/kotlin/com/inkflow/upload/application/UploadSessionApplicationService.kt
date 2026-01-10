package com.inkflow.upload.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.common.events.EventEnvelope
import com.inkflow.common.outbox.domain.OutboxEvent
import com.inkflow.common.outbox.domain.OutboxEventRepository
import com.inkflow.upload.domain.AssetMetadata
import com.inkflow.upload.domain.AssetMetadataRepository
import com.inkflow.upload.domain.EpisodeAccessRepository
import com.inkflow.upload.domain.UploadSession
import com.inkflow.upload.domain.UploadSessionCacheRepository
import com.inkflow.upload.domain.UploadSessionRepository
import com.inkflow.upload.domain.UploadSessionStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * 업로드 세션 생성/완료 로직을 담당하는 애플리케이션 서비스.
 */
@Service
class UploadSessionApplicationService(
    private val uploadSessionRepository: UploadSessionRepository,
    private val uploadSessionCacheRepository: UploadSessionCacheRepository,
    private val presignedUrlProvider: MultipartPresignedUrlProvider,
    private val multipartUploadCompleter: MultipartUploadCompleter,
    private val assetMetadataRepository: AssetMetadataRepository,
    private val episodeAccessRepository: EpisodeAccessRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper,
    private val properties: UploadSessionProperties,
    private val validationProperties: UploadValidationProperties,
    private val clock: Clock
) {
    companion object {
        private const val EVENT_PRODUCER = "upload-api" // Outbox 이벤트에 기록할 producer 식별자.
        private const val AGGREGATE_TYPE_ASSET = "ASSET" // Asset 집합을 나타내는 Outbox aggregate 타입.
    }

    /**
     * 업로드 세션을 생성하고 presigned URL을 발급한다.
     */
    @Transactional
    fun createSession(command: CreateUploadSessionCommand): UploadSessionCreationResult {
        validate(command)

        val now = Instant.now(clock)
        val expiresAt = now.plus(properties.ttl)
        val uploadId = generateUploadId()
        val chunkSize = calculateChunkSize(command.size, command.totalParts)
        val sanitizedFileName = sanitizeFileName(command.fileName)
        val storageKey = buildStorageKey(command.creatorId, command.episodeId, uploadId, sanitizedFileName)

        // presigned URL 발급과 멀티파트 업로드 식별자 생성을 위임한다.
        val presignedUpload = presignedUrlProvider.createPresignedMultipart(
            bucket = properties.storageBucket,
            key = storageKey,
            totalParts = command.totalParts,
            expiresAt = expiresAt
        )

        val session = UploadSession.create(
            uploadId = uploadId,
            episodeId = command.episodeId,
            creatorId = command.creatorId,
            fileName = sanitizedFileName,
            contentType = command.contentType,
            totalSize = command.size,
            checksum = command.checksum,
            totalParts = command.totalParts,
            chunkSize = chunkSize,
            storageBucket = properties.storageBucket,
            storageKey = storageKey,
            multipartUploadId = presignedUpload.multipartUploadId,
            expiresAt = expiresAt,
            now = now
        )

        // DB 저장 후 Redis TTL 캐시에 적재해 일관성을 유지한다.
        uploadSessionRepository.save(session)
        uploadSessionCacheRepository.save(session)

        return UploadSessionCreationResult(
            uploadId = uploadId,
            chunkSize = chunkSize,
            presignedUrls = presignedUpload.presignedUrls,
            expiresAt = expiresAt
        )
    }

    /**
     * 업로드 완료를 처리하고 Asset 메타데이터를 저장한다.
     */
    @Transactional
    fun completeSession(command: CompleteUploadSessionCommand): UploadSessionCompletionResult {
        validateComplete(command)

        val now = Instant.now(clock)
        val session = loadSession(command.uploadId)
            ?: throw BusinessException(
                errorCode = ErrorCode.NOT_FOUND,
                details = mapOf("uploadId" to command.uploadId),
                message = "업로드 세션을 찾을 수 없습니다."
            )

        if (session.creatorId != command.creatorId) {
            throw BusinessException(
                errorCode = ErrorCode.FORBIDDEN,
                details = mapOf("uploadId" to command.uploadId),
                message = "업로드 완료 권한이 없습니다."
            )
        }

        if (command.checksum != session.checksum) {
            throw invalid("checksum", "checksum이 세션 값과 일치하지 않습니다.")
        }

        validateUploadedParts(command.uploadedParts, session.totalParts)

        // 이미 완료된 세션은 기존 Asset을 반환한다.
        if (session.status == UploadSessionStatus.COMPLETED) {
            val existingAsset = assetMetadataRepository.findByUploadId(session.uploadId)
                ?: throw invalidState("이미 완료된 세션이지만 Asset 메타데이터가 없습니다.")
            return UploadSessionCompletionResult(
                assetId = existingAsset.id ?: throw missingAssetId(existingAsset.uploadId),
                status = existingAsset.status
            )
        }

        if (session.isExpired(now)) {
            throw invalidState("만료된 업로드 세션은 완료할 수 없습니다.")
        }

        if (session.status == UploadSessionStatus.ABORTED || session.status == UploadSessionStatus.EXPIRED) {
            throw invalidState("업로드 세션 상태가 완료 처리에 적합하지 않습니다.")
        }

        // 멀티파트 업로드 완료 처리와 ETag 생성을 위임한다.
        val completedUpload = multipartUploadCompleter.completeMultipartUpload(
            bucket = session.storageBucket,
            key = session.storageKey,
            uploadId = session.multipartUploadId,
            parts = normalizeParts(command.uploadedParts)
        )

        if (completedUpload.etag.isBlank()) {
            throw SystemException(
                errorCode = ErrorCode.DEPENDENCY_FAILURE,
                details = mapOf("uploadId" to command.uploadId),
                message = "스토리지에서 유효한 ETag를 반환하지 않았습니다."
            )
        }

        val completedSession = completeSessionState(session, now)
        uploadSessionRepository.save(completedSession)
        uploadSessionCacheRepository.save(completedSession)

        val existingAsset = assetMetadataRepository.findByUploadId(session.uploadId)
        val storedAsset = existingAsset ?: createStoredAsset(session, now)

        // 신규 Asset 저장 시 Outbox 이벤트를 함께 기록한다.
        if (existingAsset == null) {
            recordAssetStoredEvent(storedAsset, session, now)
        }

        return UploadSessionCompletionResult(
            assetId = storedAsset.id ?: throw missingAssetId(storedAsset.uploadId),
            status = storedAsset.status
        )
    }

    /**
     * 요청 값의 기본 유효성을 검증한다.
     */
    private fun validate(command: CreateUploadSessionCommand) {
        if (command.episodeId <= 0) {
            throw invalid("episodeId", "episodeId는 1 이상이어야 합니다.")
        }
        if (command.creatorId.isBlank()) {
            throw invalid("creatorId", "creatorId는 비어 있을 수 없습니다.")
        }
        if (command.fileName.isBlank()) {
            throw invalid("filename", "filename은 비어 있을 수 없습니다.")
        }
        if (command.contentType.isBlank()) {
            throw invalid("contentType", "contentType은 비어 있을 수 없습니다.")
        }
        if (command.size <= 0) {
            throw invalid("size", "size는 0보다 커야 합니다.")
        }
        if (command.checksum.isBlank()) {
            throw invalid("checksum", "checksum은 비어 있을 수 없습니다.")
        }
        if (command.totalParts <= 0) {
            throw invalid("totalParts", "totalParts는 1 이상이어야 합니다.")
        }
        if (command.totalParts > properties.maxPartCount) {
            throw invalid("totalParts", "totalParts는 ${properties.maxPartCount} 이하여야 합니다.")
        }

        validateFileSize(command.size)
        validateFileExtension(command.fileName)
        verifyUploadPermission(command.episodeId, command.creatorId)

        val chunkSize = calculateChunkSize(command.size, command.totalParts)
        if (command.totalParts > 1 && chunkSize < properties.minChunkSize) {
            // 멀티파트 업로드에서 최소 파트 크기 미만은 거부한다.
            throw invalid("totalParts", "계산된 chunkSize가 최소 크기(${properties.minChunkSize})보다 작습니다.")
        }
    }

    /**
     * 업로드 완료 요청 값을 검증한다.
     */
    private fun validateComplete(command: CompleteUploadSessionCommand) {
        if (command.uploadId.isBlank()) {
            throw invalid("uploadId", "uploadId는 비어 있을 수 없습니다.")
        }
        if (command.creatorId.isBlank()) {
            throw invalid("creatorId", "creatorId는 비어 있을 수 없습니다.")
        }
        if (command.checksum.isBlank()) {
            throw invalid("checksum", "checksum은 비어 있을 수 없습니다.")
        }
        if (command.uploadedParts.isEmpty()) {
            throw invalid("uploadedParts", "uploadedParts는 비어 있을 수 없습니다.")
        }
    }

    /**
     * 업로드 파트 목록을 검증한다.
     */
    private fun validateUploadedParts(parts: List<CompletedPart>, totalParts: Int) {
        if (totalParts <= 0) {
            throw invalid("totalParts", "totalParts는 1 이상이어야 합니다.")
        }
        if (parts.size != totalParts) {
            throw invalid("uploadedParts", "업로드 파트 수가 totalParts와 일치해야 합니다.")
        }

        val numbers = mutableSetOf<Int>()
        for (part in parts) {
            if (part.partNumber !in 1..totalParts) {
                throw invalid("uploadedParts", "partNumber는 1~$totalParts 범위여야 합니다.")
            }
            if (part.etag.isBlank()) {
                throw invalid("uploadedParts", "etag는 비어 있을 수 없습니다.")
            }
            if (!numbers.add(part.partNumber)) {
                throw invalid("uploadedParts", "partNumber가 중복되었습니다.")
            }
        }
    }

    /**
     * 캐시→DB 순서로 업로드 세션을 조회하고 캐시를 갱신한다.
     */
    private fun loadSession(uploadId: String): UploadSession? {
        val cached = uploadSessionCacheRepository.findByUploadId(uploadId)
        if (cached != null) {
            return cached
        }

        val session = uploadSessionRepository.findByUploadId(uploadId) ?: return null
        // DB 조회 시 Redis TTL 캐시를 갱신한다.
        uploadSessionCacheRepository.save(session)
        return session
    }

    /**
     * 업로드 세션을 완료 상태로 전이한다.
     */
    private fun completeSessionState(session: UploadSession, now: Instant): UploadSession {
        val uploadingSession = when (session.status) {
            UploadSessionStatus.CREATED -> session.markUploading(now)
            UploadSessionStatus.UPLOADING -> session
            else -> throw invalidState("업로드 세션 상태가 완료 처리에 적합하지 않습니다.")
        }

        val withSize = uploadingSession.recordUploadedSize(session.totalSize, now)
        return withSize.markCompleted(now)
    }

    /**
     * 업로드 완료 시 Asset 메타데이터를 생성하고 STORED 상태로 저장한다.
     */
    private fun createStoredAsset(session: UploadSession, now: Instant): AssetMetadata {
        val asset = AssetMetadata.create(
            episodeId = session.episodeId,
            creatorId = session.creatorId,
            uploadId = session.uploadId,
            fileName = session.fileName,
            contentType = session.contentType,
            size = session.totalSize,
            checksum = session.checksum,
            storageBucket = session.storageBucket,
            storageKey = session.storageKey,
            now = now
        )

        val storedAsset = asset.markStored(now)
        return assetMetadataRepository.save(storedAsset)
    }

    /**
     * Asset 저장 완료 이벤트를 Outbox에 기록한다.
     */
    private fun recordAssetStoredEvent(
        asset: AssetMetadata,
        session: UploadSession,
        occurredAt: Instant
    ) {
        val assetId = asset.id ?: throw missingAssetId(session.uploadId)
        val payload = AssetStoredEventPayload(
            assetId = assetId,
            uploadId = session.uploadId,
            episodeId = session.episodeId,
            creatorId = session.creatorId,
            fileName = session.fileName,
            contentType = session.contentType,
            size = session.totalSize,
            checksum = session.checksum,
            storageBucket = session.storageBucket,
            storageKey = session.storageKey,
            status = asset.status,
            occurredAt = occurredAt
        )

        val envelope = EventEnvelope.create(
            eventType = UploadEventTypes.ASSET_STORED,
            producer = EVENT_PRODUCER,
            payload = payload,
            idempotencyKey = session.uploadId,
            occurredAt = occurredAt
        )

        val serializedPayload = try {
            objectMapper.writeValueAsString(envelope)
        } catch (exception: Exception) {
            throw SystemException(
                errorCode = ErrorCode.INTERNAL_ERROR,
                details = mapOf("uploadId" to session.uploadId),
                message = "Outbox 이벤트 직렬화에 실패했습니다.",
                cause = exception
            )
        }

        val outboxEvent = OutboxEvent.pending(
            aggregateType = AGGREGATE_TYPE_ASSET,
            aggregateId = assetId.toString(),
            eventType = envelope.eventType.asString(),
            payload = serializedPayload,
            createdAt = occurredAt
        )

        outboxEventRepository.save(outboxEvent)
    }

    /**
     * 멀티파트 완료 처리를 위해 파트 목록을 오름차순으로 정렬한다.
     */
    private fun normalizeParts(parts: List<CompletedPart>): List<CompletedPart> {
        return parts.map { part ->
            part.copy(etag = part.etag.trim())
        }.sortedBy { it.partNumber }
    }

    /**
     * 총 업로드 크기와 파트 수를 기준으로 chunkSize를 계산한다.
     */
    private fun calculateChunkSize(totalSize: Long, totalParts: Int): Long {
        val divisor = totalParts.toLong()
        // 부동소수점 오차를 피하기 위해 정수 계산으로 올림 처리한다.
        return (totalSize + divisor - 1) / divisor
    }

    /**
     * 업로드 ID를 생성한다.
     */
    private fun generateUploadId(): String {
        return "upl_${UUID.randomUUID()}"
    }

    /**
     * 스토리지 키 규칙을 통일한다.
     */
    private fun buildStorageKey(
        creatorId: String,
        episodeId: Long,
        uploadId: String,
        fileName: String
    ): String {
        val normalizedPrefix = properties.storageKeyPrefix.trim().trimStart('/')
        val prefix = if (normalizedPrefix.isBlank()) "" else "${normalizedPrefix.trimEnd('/')}/"
        return "${prefix}${creatorId}/${episodeId}/${uploadId}/${fileName}"
    }

    /**
     * 파일명 경로 이탈을 방지하기 위해 마지막 세그먼트만 사용한다.
     */
    private fun sanitizeFileName(fileName: String): String {
        val normalized = fileName.replace("\\", "/")
        return normalized.substringAfterLast('/')
    }

    /**
     * 업로드 파일 크기를 정책 범위 내로 검증한다.
     */
    private fun validateFileSize(size: Long) {
        if (size < validationProperties.minFileSize) {
            throw invalid("size", "size는 ${validationProperties.minFileSize} 이상이어야 합니다.")
        }
        if (size > validationProperties.maxFileSize) {
            throw invalid("size", "size는 ${validationProperties.maxFileSize} 이하여야 합니다.")
        }
    }

    /**
     * 업로드 파일 확장자를 검증한다.
     */
    private fun validateFileExtension(fileName: String) {
        val sanitizedFileName = sanitizeFileName(fileName)
        if (sanitizedFileName.isBlank()) {
            throw invalid("filename", "filename은 비어 있을 수 없습니다.")
        }
        val extension = extractFileExtension(sanitizedFileName)
        if (extension.isBlank()) {
            throw invalid("filename", "파일 확장자가 필요합니다.")
        }
        val normalized = extension.lowercase()
        val allowedExtensions = validationProperties.allowedExtensions.map { it.lowercase() }.toSet()
        if (allowedExtensions.isNotEmpty() && normalized !in allowedExtensions) {
            throw invalid("filename", "허용되지 않는 파일 확장자입니다.")
        }
    }

    /**
     * 에피소드 소유자 정보를 기반으로 업로드 권한을 확인한다.
     */
    private fun verifyUploadPermission(episodeId: Long, creatorId: String) {
        val ownerId = episodeAccessRepository.findCreatorIdByEpisodeId(episodeId)
            ?: throw BusinessException(
                errorCode = ErrorCode.NOT_FOUND,
                details = mapOf("episodeId" to episodeId.toString()),
                message = "에피소드를 찾을 수 없습니다."
            )
        if (ownerId != creatorId) {
            throw BusinessException(
                errorCode = ErrorCode.FORBIDDEN,
                details = mapOf("episodeId" to episodeId.toString()),
                message = "해당 에피소드에 대한 업로드 권한이 없습니다."
            )
        }
    }

    /**
     * 파일명에서 확장자를 추출한다.
     */
    private fun extractFileExtension(fileName: String): String {
        val lastDotIndex = fileName.lastIndexOf('.')
        if (lastDotIndex <= 0 || lastDotIndex == fileName.length - 1) {
            return ""
        }
        return fileName.substring(lastDotIndex + 1)
    }

    /**
     * 요청 오류를 표준 예외로 변환한다.
     */
    private fun invalid(field: String, message: String): BusinessException {
        return BusinessException(
            errorCode = ErrorCode.INVALID_REQUEST,
            details = mapOf("field" to field),
            message = message
        )
    }

    /**
     * 상태 오류를 표준 예외로 변환한다.
     */
    private fun invalidState(message: String): BusinessException {
        return BusinessException(
            errorCode = ErrorCode.INVALID_STATE,
            message = message
        )
    }

    /**
     * Asset 식별자가 누락된 경우 시스템 예외를 생성한다.
     */
    private fun missingAssetId(uploadId: String): SystemException {
        return SystemException(
            errorCode = ErrorCode.INTERNAL_ERROR,
            details = mapOf("uploadId" to uploadId),
            message = "Asset 식별자를 확인할 수 없습니다."
        )
    }
}
