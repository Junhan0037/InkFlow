package com.inkflow.indexing.infra.jpa

import com.inkflow.indexing.domain.AssetIndexSource
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Asset 색인 원천 데이터를 조회하기 위한 JPA 엔티티.
 */
@Entity
@Table(name = "asset")
class AssetIndexEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: Long = 0L,

    @Column(name = "episode_id", nullable = false)
    var episodeId: Long = 0L,

    @Column(name = "filename", nullable = false)
    var fileName: String = "",

    @Column(name = "content_type", nullable = false)
    var contentType: String = "",

    @Column(name = "size", nullable = false)
    var size: Long = 0L,

    @Column(name = "checksum", nullable = false)
    var checksum: String = "",

    @Column(name = "storage_key", nullable = false)
    var storageKey: String = "",

    @Column(name = "status", nullable = false)
    var status: String = "",

    @Column(name = "creator_id", nullable = false)
    var creatorId: String = "",

    @Column(name = "upload_id", nullable = false)
    var uploadId: String = "",

    @Column(name = "storage_bucket", nullable = false)
    var storageBucket: String = "",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH
) {
    /**
     * 엔티티를 도메인 모델로 변환한다.
     */
    fun toDomain(): AssetIndexSource {
        return AssetIndexSource(
            id = id,
            episodeId = episodeId,
            fileName = fileName,
            contentType = contentType,
            size = size,
            checksum = checksum,
            storageKey = storageKey,
            status = status,
            creatorId = creatorId,
            uploadId = uploadId,
            storageBucket = storageBucket,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
