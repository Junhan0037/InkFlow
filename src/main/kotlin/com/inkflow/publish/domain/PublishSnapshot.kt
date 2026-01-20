package com.inkflow.publish.domain

import java.time.Instant

/**
 * 퍼블리시 스냅샷 상세 도메인 모델.
 */
data class PublishSnapshot(
    val id: String? = null,
    val snapshotId: String,
    val episodeId: Long,
    val publishVersion: Long,
    val region: String,
    val language: String,
    val status: PublishSnapshotStatus,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(snapshotId.isNotBlank()) { "snapshotId는 비어 있을 수 없습니다." }
        require(episodeId > 0) { "episodeId는 1 이상이어야 합니다." }
        require(publishVersion > 0) { "publishVersion은 1 이상이어야 합니다." }
        require(region.isNotBlank()) { "region은 비어 있을 수 없습니다." }
        require(language.isNotBlank()) { "language는 비어 있을 수 없습니다." }
    }

    /**
     * 최신 스냅샷으로 대체된 상태로 갱신한다.
     */
    fun markSuperseded(now: Instant): PublishSnapshot {
        return copy(status = PublishSnapshotStatus.SUPERSEDED, updatedAt = now)
    }

    /**
     * 롤백으로 인해 비활성화된 상태로 갱신한다.
     */
    fun markRolledBack(now: Instant): PublishSnapshot {
        return copy(status = PublishSnapshotStatus.ROLLED_BACK, updatedAt = now)
    }

    /**
     * 활성 스냅샷으로 전환한다.
     */
    fun activate(now: Instant): PublishSnapshot {
        return copy(status = PublishSnapshotStatus.ACTIVE, updatedAt = now)
    }

    companion object {
        /**
         * 신규 퍼블리시 스냅샷을 생성한다.
         */
        fun create(
            snapshotId: String,
            episodeId: Long,
            publishVersion: Long,
            region: String,
            language: String,
            now: Instant
        ): PublishSnapshot {
            return PublishSnapshot(
                id = null,
                snapshotId = snapshotId,
                episodeId = episodeId,
                publishVersion = publishVersion,
                region = region,
                language = language,
                status = PublishSnapshotStatus.ACTIVE,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}
