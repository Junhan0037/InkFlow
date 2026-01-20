package com.inkflow.publish.infra.mongo

import com.inkflow.publish.domain.PublishSnapshot
import com.inkflow.publish.domain.PublishSnapshotStatus
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * MongoDB에 저장하는 퍼블리시 스냅샷 문서.
 */
@Document(collection = "publish_snapshots")
data class PublishSnapshotDocument(
    @Id
    val id: String? = null,
    @Indexed(unique = true)
    val snapshotId: String,
    @Indexed
    val episodeId: Long,
    @Indexed
    val publishVersion: Long,
    val region: String,
    val language: String,
    val status: PublishSnapshotStatus,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        /**
         * 도메인 모델을 MongoDB 문서로 변환한다.
         */
        fun fromDomain(snapshot: PublishSnapshot): PublishSnapshotDocument {
            return PublishSnapshotDocument(
                id = snapshot.id,
                snapshotId = snapshot.snapshotId,
                episodeId = snapshot.episodeId,
                publishVersion = snapshot.publishVersion,
                region = snapshot.region,
                language = snapshot.language,
                status = snapshot.status,
                createdAt = snapshot.createdAt,
                updatedAt = snapshot.updatedAt
            )
        }
    }

    /**
     * MongoDB 문서를 도메인 모델로 변환한다.
     */
    fun toDomain(): PublishSnapshot {
        return PublishSnapshot(
            id = id,
            snapshotId = snapshotId,
            episodeId = episodeId,
            publishVersion = publishVersion,
            region = region,
            language = language,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
