package com.inkflow.upload.infra.jpa

import com.inkflow.upload.domain.AssetMetadata
import com.inkflow.upload.domain.AssetStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Asset 메타데이터를 JPA로 영속화하기 위한 엔티티.
 */
@Entity
@Table(name = "asset")
class AssetMetadataEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null,

    @Column(name = "episode_id", nullable = false)
    var episodeId: Long = 0L,

    @Column(name = "creator_id", nullable = false)
    var creatorId: String = "",

    @Column(name = "upload_id", nullable = false)
    var uploadId: String = "",

    @Column(name = "filename", nullable = false)
    var fileName: String = "",

    @Column(name = "content_type", nullable = false)
    var contentType: String = "",

    @Column(name = "size", nullable = false)
    var size: Long = 0L,

    @Column(name = "checksum", nullable = false)
    var checksum: String = "",

    @Column(name = "storage_bucket", nullable = false)
    var storageBucket: String = "",

    @Column(name = "storage_key", nullable = false)
    var storageKey: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: AssetStatus = AssetStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH
) {
    /**
     * 엔티티를 도메인 모델로 변환한다.
     */
    fun toDomain(): AssetMetadata {
        return AssetMetadata(
            id = id,
            episodeId = episodeId,
            creatorId = creatorId,
            uploadId = uploadId,
            fileName = fileName,
            contentType = contentType,
            size = size,
            checksum = checksum,
            storageBucket = storageBucket,
            storageKey = storageKey,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        /**
         * 도메인 모델을 엔티티로 변환한다.
         */
        fun fromDomain(asset: AssetMetadata): AssetMetadataEntity {
            return AssetMetadataEntity(
                id = asset.id,
                episodeId = asset.episodeId,
                creatorId = asset.creatorId,
                uploadId = asset.uploadId,
                fileName = asset.fileName,
                contentType = asset.contentType,
                size = asset.size,
                checksum = asset.checksum,
                storageBucket = asset.storageBucket,
                storageKey = asset.storageKey,
                status = asset.status,
                createdAt = asset.createdAt,
                updatedAt = asset.updatedAt
            )
        }
    }
}
