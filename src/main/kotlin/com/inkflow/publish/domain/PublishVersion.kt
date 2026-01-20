package com.inkflow.publish.domain

import java.time.Instant

/**
 * 퍼블리시 스냅샷 버전 메타데이터 도메인 모델.
 */
data class PublishVersion(
    val id: Long? = null,
    val episodeId: Long,
    val version: Long,
    val snapshotId: String,
    val region: String,
    val language: String,
    val status: PublishVersionStatus,
    val requestId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val rolledBackAt: Instant?
) {
    init {
        require(episodeId > 0) { "episodeId는 1 이상이어야 합니다." }
        require(version > 0) { "version은 1 이상이어야 합니다." }
        require(snapshotId.isNotBlank()) { "snapshotId는 비어 있을 수 없습니다." }
        require(region.isNotBlank()) { "region은 비어 있을 수 없습니다." }
        require(language.isNotBlank()) { "language는 비어 있을 수 없습니다." }
        if (requestId != null) {
            require(requestId.isNotBlank()) { "requestId는 빈 문자열일 수 없습니다." }
        }
    }

    /**
     * 최신 스냅샷으로 대체된 상태로 갱신한다.
     */
    fun markSuperseded(now: Instant): PublishVersion {
        return copy(status = PublishVersionStatus.SUPERSEDED, updatedAt = now)
    }

    /**
     * 롤백으로 인해 비활성화된 상태로 갱신한다.
     */
    fun markRolledBack(now: Instant): PublishVersion {
        return copy(status = PublishVersionStatus.ROLLED_BACK, updatedAt = now, rolledBackAt = now)
    }

    /**
     * 현재 활성 버전으로 전환한다.
     */
    fun activate(now: Instant): PublishVersion {
        return copy(status = PublishVersionStatus.ACTIVE, updatedAt = now, rolledBackAt = null)
    }

    companion object {
        /**
         * 신규 퍼블리시 버전을 생성한다.
         */
        fun create(
            episodeId: Long,
            version: Long,
            snapshotId: String,
            region: String,
            language: String,
            requestId: String?,
            now: Instant
        ): PublishVersion {
            return PublishVersion(
                id = null,
                episodeId = episodeId,
                version = version,
                snapshotId = snapshotId,
                region = region,
                language = language,
                status = PublishVersionStatus.ACTIVE,
                requestId = requestId,
                createdAt = now,
                updatedAt = now,
                rolledBackAt = null
            )
        }
    }
}
