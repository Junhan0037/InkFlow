package com.inkflow.metadata.infra.jpa

import com.inkflow.metadata.domain.EpisodeMetadata
import com.inkflow.metadata.infra.MetadataTagCodec
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 승인된 에피소드 메타데이터를 JPA로 영속화하기 위한 엔티티.
 */
@Entity
@Table(name = "episode_metadata")
class EpisodeMetadataEntity(
    @Id
    @Column(name = "episode_id", nullable = false)
    var episodeId: Long = 0L,

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    var summary: String = "",

    @Column(name = "tags", nullable = false, columnDefinition = "jsonb")
    var tags: String = "[]",

    @Column(name = "approved_by", nullable = false)
    var approvedBy: String = "",

    @Column(name = "approved_at", nullable = false)
    var approvedAt: Instant = Instant.EPOCH,

    @Column(name = "version", nullable = false)
    var version: Int = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH
) {
    /**
     * 엔티티를 도메인 모델로 변환한다.
     */
    fun toDomain(tagCodec: MetadataTagCodec): EpisodeMetadata {
        return EpisodeMetadata(
            episodeId = episodeId,
            summary = summary,
            tags = tagCodec.decode(tags),
            approvedBy = approvedBy,
            approvedAt = approvedAt,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        /**
         * 도메인 모델을 엔티티로 변환한다.
         */
        fun fromDomain(metadata: EpisodeMetadata, tagCodec: MetadataTagCodec): EpisodeMetadataEntity {
            return EpisodeMetadataEntity(
                episodeId = metadata.episodeId,
                summary = metadata.summary,
                tags = tagCodec.encode(metadata.tags),
                approvedBy = metadata.approvedBy,
                approvedAt = metadata.approvedAt,
                version = metadata.version,
                createdAt = metadata.createdAt,
                updatedAt = metadata.updatedAt
            )
        }
    }
}
