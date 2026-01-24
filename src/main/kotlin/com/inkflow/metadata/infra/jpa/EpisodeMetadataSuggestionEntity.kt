package com.inkflow.metadata.infra.jpa

import com.inkflow.metadata.domain.EpisodeMetadataSuggestion
import com.inkflow.metadata.domain.MetadataSuggestionStatus
import com.inkflow.metadata.infra.MetadataTagCodec
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
 * 메타 자동 생성 제안을 JPA로 영속화하기 위한 엔티티.
 */
@Entity
@Table(name = "episode_metadata_suggestion")
class EpisodeMetadataSuggestionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null,

    @Column(name = "episode_id", nullable = false)
    var episodeId: Long = 0L,

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    var summary: String = "",

    @Column(name = "tags", nullable = false, columnDefinition = "jsonb")
    var tags: String = "[]",

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: MetadataSuggestionStatus = MetadataSuggestionStatus.PENDING,

    @Column(name = "requested_by", nullable = false)
    var requestedBy: String = "",

    @Column(name = "reviewed_by")
    var reviewedBy: String? = null,

    @Column(name = "reviewed_at")
    var reviewedAt: Instant? = null,

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    var rejectionReason: String? = null,

    @Column(name = "generator", nullable = false)
    var generator: String = "",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH
) {
    /**
     * 엔티티를 도메인 모델로 변환한다.
     */
    fun toDomain(tagCodec: MetadataTagCodec): EpisodeMetadataSuggestion {
        return EpisodeMetadataSuggestion(
            id = id,
            episodeId = episodeId,
            summary = summary,
            tags = tagCodec.decode(tags),
            status = status,
            requestedBy = requestedBy,
            reviewedBy = reviewedBy,
            reviewedAt = reviewedAt,
            rejectionReason = rejectionReason,
            generator = generator,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        /**
         * 도메인 모델을 엔티티로 변환한다.
         */
        fun fromDomain(
            suggestion: EpisodeMetadataSuggestion,
            tagCodec: MetadataTagCodec
        ): EpisodeMetadataSuggestionEntity {
            return EpisodeMetadataSuggestionEntity(
                id = suggestion.id,
                episodeId = suggestion.episodeId,
                summary = suggestion.summary,
                tags = tagCodec.encode(suggestion.tags),
                status = suggestion.status,
                requestedBy = suggestion.requestedBy,
                reviewedBy = suggestion.reviewedBy,
                reviewedAt = suggestion.reviewedAt,
                rejectionReason = suggestion.rejectionReason,
                generator = suggestion.generator,
                createdAt = suggestion.createdAt,
                updatedAt = suggestion.updatedAt
            )
        }
    }
}
