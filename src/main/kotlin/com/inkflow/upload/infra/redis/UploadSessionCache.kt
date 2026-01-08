package com.inkflow.upload.infra.redis

import com.inkflow.upload.domain.UploadSession
import com.inkflow.upload.domain.UploadSessionStatus
import java.time.Instant
import java.util.UUID

/**
 * Redis에 저장할 업로드 세션 캐시 모델.
 */
data class UploadSessionCache(
    val id: UUID,
    val uploadId: String,
    val episodeId: Long,
    val creatorId: String,
    val fileName: String,
    val contentType: String,
    val totalSize: Long,
    val uploadedSize: Long,
    val checksum: String,
    val totalParts: Int,
    val chunkSize: Long,
    val status: UploadSessionStatus,
    val storageBucket: String,
    val storageKey: String,
    val multipartUploadId: String,
    val expiresAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    /**
     * 캐시 모델을 도메인 모델로 변환한다.
     */
    fun toDomain(): UploadSession {
        return UploadSession(
            id = id,
            uploadId = uploadId,
            episodeId = episodeId,
            creatorId = creatorId,
            fileName = fileName,
            contentType = contentType,
            totalSize = totalSize,
            uploadedSize = uploadedSize,
            checksum = checksum,
            totalParts = totalParts,
            chunkSize = chunkSize,
            status = status,
            storageBucket = storageBucket,
            storageKey = storageKey,
            multipartUploadId = multipartUploadId,
            expiresAt = expiresAt,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        /**
         * 도메인 모델을 캐시 모델로 변환한다.
         */
        fun fromDomain(session: UploadSession): UploadSessionCache {
            return UploadSessionCache(
                id = session.id,
                uploadId = session.uploadId,
                episodeId = session.episodeId,
                creatorId = session.creatorId,
                fileName = session.fileName,
                contentType = session.contentType,
                totalSize = session.totalSize,
                uploadedSize = session.uploadedSize,
                checksum = session.checksum,
                totalParts = session.totalParts,
                chunkSize = session.chunkSize,
                status = session.status,
                storageBucket = session.storageBucket,
                storageKey = session.storageKey,
                multipartUploadId = session.multipartUploadId,
                expiresAt = session.expiresAt,
                createdAt = session.createdAt,
                updatedAt = session.updatedAt
            )
        }
    }
}
