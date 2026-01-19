package com.inkflow.indexing.infra.jpa

import com.inkflow.indexing.domain.EpisodeIndexSource
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Episode 색인 원천 데이터를 조회하기 위한 JPA 엔티티.
 */
@Entity
@Table(name = "episode")
class EpisodeIndexEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: Long = 0L,

    @Column(name = "work_id", nullable = false)
    var workId: Long = 0L,

    @Column(name = "title", nullable = false)
    var title: String = "",

    @Column(name = "seq", nullable = false)
    var seq: Int = 0,

    @Column(name = "published_at")
    var publishedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH
) {
    /**
     * 엔티티를 도메인 모델로 변환한다.
     */
    fun toDomain(): EpisodeIndexSource {
        return EpisodeIndexSource(
            id = id,
            workId = workId,
            title = title,
            seq = seq,
            publishedAt = publishedAt,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
