package com.inkflow.publish.infra.jpa

import com.inkflow.publish.domain.PublishVersion
import com.inkflow.publish.domain.PublishVersionStatus
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
 * 퍼블리시 버전 메타데이터 JPA 엔티티.
 */
@Entity
@Table(name = "publish_version")
class PublishVersionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null,

    @Column(name = "episode_id", nullable = false)
    var episodeId: Long = 0L,

    @Column(name = "version", nullable = false)
    var version: Long = 0L,

    @Column(name = "snapshot_id", nullable = false)
    var snapshotId: String = "",

    @Column(name = "region", nullable = false)
    var region: String = "",

    @Column(name = "language", nullable = false)
    var language: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: PublishVersionStatus = PublishVersionStatus.ACTIVE,

    @Column(name = "request_id")
    var requestId: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,

    @Column(name = "rolled_back_at")
    var rolledBackAt: Instant? = null
) {
    /**
     * 엔티티를 도메인 모델로 변환한다.
     */
    fun toDomain(): PublishVersion {
        return PublishVersion(
            id = id,
            episodeId = episodeId,
            version = version,
            snapshotId = snapshotId,
            region = region,
            language = language,
            status = status,
            requestId = requestId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            rolledBackAt = rolledBackAt
        )
    }

    companion object {
        /**
         * 도메인 모델을 엔티티로 변환한다.
         */
        fun fromDomain(version: PublishVersion): PublishVersionEntity {
            return PublishVersionEntity(
                id = version.id,
                episodeId = version.episodeId,
                version = version.version,
                snapshotId = version.snapshotId,
                region = version.region,
                language = version.language,
                status = version.status,
                requestId = version.requestId,
                createdAt = version.createdAt,
                updatedAt = version.updatedAt,
                rolledBackAt = version.rolledBackAt
            )
        }
    }
}
