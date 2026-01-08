package com.inkflow.upload.infra.jpa

import com.inkflow.upload.domain.UploadSession
import com.inkflow.upload.domain.UploadSessionStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * 업로드 세션을 JPA로 영속화하기 위한 엔티티.
 */
@Entity
@Table(name = "upload_session")
class UploadSessionEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "upload_id", nullable = false, unique = true)
    var uploadId: String = "",

    @Column(name = "episode_id", nullable = false)
    var episodeId: Long = 0L,

    @Column(name = "creator_id", nullable = false)
    var creatorId: String = "",

    @Column(name = "filename", nullable = false)
    var fileName: String = "",

    @Column(name = "content_type", nullable = false)
    var contentType: String = "",

    @Column(name = "total_size", nullable = false)
    var totalSize: Long = 0L,

    @Column(name = "uploaded_size", nullable = false)
    var uploadedSize: Long = 0L,

    @Column(name = "checksum", nullable = false)
    var checksum: String = "",

    @Column(name = "total_parts", nullable = false)
    var totalParts: Int = 0,

    @Column(name = "chunk_size", nullable = false)
    var chunkSize: Long = 0L,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: UploadSessionStatus = UploadSessionStatus.CREATED,

    @Column(name = "storage_bucket", nullable = false)
    var storageBucket: String = "",

    @Column(name = "storage_key", nullable = false)
    var storageKey: String = "",

    @Column(name = "multipart_upload_id", nullable = false)
    var multipartUploadId: String = "",

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.EPOCH,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH
) {
    /**
     * 엔티티를 도메인 모델로 변환한다.
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
         * 도메인 모델을 엔티티로 변환한다.
         */
        fun fromDomain(session: UploadSession): UploadSessionEntity {
            return UploadSessionEntity(
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
