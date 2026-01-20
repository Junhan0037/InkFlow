package com.inkflow.publish.domain

/**
 * 퍼블리시 버전 저장소 계약.
 */
interface PublishVersionRepository {
    /**
     * 퍼블리시 버전을 저장한다.
     */
    fun save(version: PublishVersion): PublishVersion

    /**
     * 에피소드별 최신 퍼블리시 버전을 조회한다.
     */
    fun findLatestByEpisodeId(episodeId: Long): PublishVersion?

    /**
     * 에피소드별 활성 퍼블리시 버전을 조회한다.
     */
    fun findActiveByEpisodeId(episodeId: Long): PublishVersion?

    /**
     * 에피소드와 버전으로 퍼블리시 버전을 조회한다.
     */
    fun findByEpisodeIdAndVersion(episodeId: Long, version: Long): PublishVersion?

    /**
     * 에피소드와 requestId로 퍼블리시 버전을 조회한다.
     */
    fun findByEpisodeIdAndRequestId(episodeId: Long, requestId: String): PublishVersion?
}
