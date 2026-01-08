package com.inkflow.upload.domain

import java.time.Instant

/**
 * 업로드된 원본 파일의 메타데이터를 표현.
 */
data class AssetMetadata(
    val id: Long? = null,
    val episodeId: Long,
    val creatorId: String,
    val uploadId: String,
    val fileName: String,
    val contentType: String,
    val size: Long,
    val checksum: String,
    val storageBucket: String,
    val storageKey: String,
    val status: AssetStatus,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(creatorId.isNotBlank()) { "creatorId는 비어 있을 수 없습니다." }
        require(uploadId.isNotBlank()) { "uploadId는 비어 있을 수 없습니다." }
        require(fileName.isNotBlank()) { "fileName은 비어 있을 수 없습니다." }
        require(contentType.isNotBlank()) { "contentType은 비어 있을 수 없습니다." }
        require(size > 0) { "size는 0보다 커야 합니다." }
        require(checksum.isNotBlank()) { "checksum은 비어 있을 수 없습니다." }
        require(storageBucket.isNotBlank()) { "storageBucket은 비어 있을 수 없습니다." }
        require(storageKey.isNotBlank()) { "storageKey는 비어 있을 수 없습니다." }
    }

    /**
     * 저장 완료 상태로 전이한다.
     */
    fun markStored(now: Instant): AssetMetadata {
        require(status == AssetStatus.PENDING) { "PENDING 상태에서만 STORED로 전이할 수 있습니다." }
        return copy(status = AssetStatus.STORED, updatedAt = now)
    }

    /**
     * 저장 실패 상태로 전이한다.
     */
    fun markFailed(now: Instant): AssetMetadata {
        require(status != AssetStatus.DELETED) { "DELETED 상태는 실패로 전이할 수 없습니다." }
        return copy(status = AssetStatus.FAILED, updatedAt = now)
    }

    /**
     * 삭제 완료 상태로 전이한다.
     */
    fun markDeleted(now: Instant): AssetMetadata {
        require(status != AssetStatus.DELETED) { "DELETED 상태는 다시 삭제할 수 없습니다." }
        return copy(status = AssetStatus.DELETED, updatedAt = now)
    }

    companion object {
        /**
         * 초기 Asset 메타데이터를 생성한다.
         */
        fun create(
            episodeId: Long,
            creatorId: String,
            uploadId: String,
            fileName: String,
            contentType: String,
            size: Long,
            checksum: String,
            storageBucket: String,
            storageKey: String,
            now: Instant = Instant.now()
        ): AssetMetadata {
            return AssetMetadata(
                episodeId = episodeId,
                creatorId = creatorId,
                uploadId = uploadId,
                fileName = fileName,
                contentType = contentType,
                size = size,
                checksum = checksum,
                storageBucket = storageBucket,
                storageKey = storageKey,
                status = AssetStatus.PENDING,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}
