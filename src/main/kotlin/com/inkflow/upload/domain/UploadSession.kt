package com.inkflow.upload.domain

import java.time.Instant
import java.util.UUID

/**
 * 업로드 세션의 핵심 메타데이터.
 */
data class UploadSession(
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
    init {
        require(uploadId.isNotBlank()) { "uploadId는 비어 있을 수 없습니다." }
        require(creatorId.isNotBlank()) { "creatorId는 비어 있을 수 없습니다." }
        require(fileName.isNotBlank()) { "fileName은 비어 있을 수 없습니다." }
        require(contentType.isNotBlank()) { "contentType은 비어 있을 수 없습니다." }
        require(totalSize > 0) { "totalSize는 0보다 커야 합니다." }
        require(uploadedSize in 0..totalSize) { "uploadedSize는 0 이상 totalSize 이하이어야 합니다." }
        require(checksum.isNotBlank()) { "checksum은 비어 있을 수 없습니다." }
        require(totalParts > 0) { "totalParts는 0보다 커야 합니다." }
        require(chunkSize > 0) { "chunkSize는 0보다 커야 합니다." }
        require(storageBucket.isNotBlank()) { "storageBucket은 비어 있을 수 없습니다." }
        require(storageKey.isNotBlank()) { "storageKey는 비어 있을 수 없습니다." }
        require(multipartUploadId.isNotBlank()) { "multipartUploadId는 비어 있을 수 없습니다." }
        require(!expiresAt.isBefore(createdAt)) { "expiresAt은 createdAt 이전일 수 없습니다." }
    }

    /**
     * 업로드 진행 상태로 전이한다.
     */
    fun markUploading(now: Instant): UploadSession {
        require(status == UploadSessionStatus.CREATED) { "CREATED 상태에서만 UPLOADING으로 전이할 수 있습니다." }
        return copy(status = UploadSessionStatus.UPLOADING, updatedAt = now)
    }

    /**
     * 업로드 진행 바이트를 갱신한다.
     */
    fun recordUploadedSize(newUploadedSize: Long, now: Instant): UploadSession {
        require(newUploadedSize in 0..totalSize) { "uploadedSize는 0 이상 totalSize 이하이어야 합니다." }
        require(newUploadedSize >= uploadedSize) { "uploadedSize는 감소할 수 없습니다." }
        return copy(uploadedSize = newUploadedSize, updatedAt = now)
    }

    /**
     * 업로드 완료 상태로 전이한다.
     */
    fun markCompleted(now: Instant): UploadSession {
        require(status == UploadSessionStatus.UPLOADING) { "UPLOADING 상태에서만 COMPLETED로 전이할 수 있습니다." }
        require(uploadedSize == totalSize) { "uploadedSize가 totalSize와 일치해야 완료 처리할 수 있습니다." }
        return copy(status = UploadSessionStatus.COMPLETED, updatedAt = now)
    }

    /**
     * 업로드 만료 상태로 전이한다.
     */
    fun markExpired(now: Instant): UploadSession {
        require(status != UploadSessionStatus.COMPLETED) { "COMPLETED 상태는 만료 처리할 수 없습니다." }
        return copy(status = UploadSessionStatus.EXPIRED, updatedAt = now)
    }

    /**
     * 업로드 중단 상태로 전이한다.
     */
    fun markAborted(now: Instant): UploadSession {
        require(status != UploadSessionStatus.COMPLETED) { "COMPLETED 상태는 중단 처리할 수 없습니다." }
        return copy(status = UploadSessionStatus.ABORTED, updatedAt = now)
    }

    /**
     * 현재 시각 기준 만료 여부를 판단한다.
     */
    fun isExpired(at: Instant): Boolean {
        return expiresAt.isBefore(at)
    }

    companion object {
        /**
         * 업로드 세션을 생성하는 팩토리 메서드.
         */
        fun create(
            uploadId: String,
            episodeId: Long,
            creatorId: String,
            fileName: String,
            contentType: String,
            totalSize: Long,
            checksum: String,
            totalParts: Int,
            chunkSize: Long,
            storageBucket: String,
            storageKey: String,
            multipartUploadId: String,
            expiresAt: Instant,
            now: Instant = Instant.now(),
            id: UUID = UUID.randomUUID()
        ): UploadSession {
            return UploadSession(
                id = id,
                uploadId = uploadId,
                episodeId = episodeId,
                creatorId = creatorId,
                fileName = fileName,
                contentType = contentType,
                totalSize = totalSize,
                uploadedSize = 0,
                checksum = checksum,
                totalParts = totalParts,
                chunkSize = chunkSize,
                status = UploadSessionStatus.CREATED,
                storageBucket = storageBucket,
                storageKey = storageKey,
                multipartUploadId = multipartUploadId,
                expiresAt = expiresAt,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}
