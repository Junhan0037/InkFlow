package com.inkflow.upload.application

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.upload.domain.UploadSession
import com.inkflow.upload.domain.UploadSessionCacheRepository
import com.inkflow.upload.domain.UploadSessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * 업로드 세션 생성 로직을 담당하는 애플리케이션 서비스.
 */
@Service
class UploadSessionApplicationService(
    private val uploadSessionRepository: UploadSessionRepository,
    private val uploadSessionCacheRepository: UploadSessionCacheRepository,
    private val presignedUrlProvider: MultipartPresignedUrlProvider,
    private val properties: UploadSessionProperties,
    private val clock: Clock
) {
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

        val chunkSize = calculateChunkSize(command.size, command.totalParts)
        if (command.totalParts > 1 && chunkSize < properties.minChunkSize) {
            // 멀티파트 업로드에서 최소 파트 크기 미만은 거부한다.
            throw invalid("totalParts", "계산된 chunkSize가 최소 크기(${properties.minChunkSize})보다 작습니다.")
        }
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
     * 요청 오류를 표준 예외로 변환한다.
     */
    private fun invalid(field: String, message: String): BusinessException {
        return BusinessException(
            errorCode = ErrorCode.INVALID_REQUEST,
            details = mapOf("field" to field),
            message = message
        )
    }
}
