package com.inkflow.indexing.infra.jpa

import com.inkflow.indexing.domain.WorkIndexSource
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Work 색인 원천 데이터를 조회하기 위한 JPA 엔티티.
 */
@Entity
@Table(name = "work")
class WorkIndexEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: Long = 0L,

    @Column(name = "title", nullable = false)
    var title: String = "",

    @Column(name = "creator_id", nullable = false)
    var creatorId: String = "",

    @Column(name = "status", nullable = false)
    var status: String = "",

    @Column(name = "default_language", nullable = false)
    var defaultLanguage: String = "",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH
) {
    /**
     * 엔티티를 도메인 모델로 변환한다.
     */
    fun toDomain(): WorkIndexSource {
        return WorkIndexSource(
            id = id,
            title = title,
            creatorId = creatorId,
            status = status,
            defaultLanguage = defaultLanguage,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
