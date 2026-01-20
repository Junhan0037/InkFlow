package com.inkflow.publish.infra.jpa

import com.inkflow.publish.domain.PublishVersionStatus
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 퍼블리시 버전 JPA 리포지토리.
 */
interface PublishVersionJpaRepository : JpaRepository<PublishVersionEntity, Long> {
    /**
     * 에피소드별 최신 버전을 조회한다.
     */
    fun findTopByEpisodeIdOrderByVersionDesc(episodeId: Long): PublishVersionEntity?

    /**
     * 활성 버전을 조회한다.
     */
    fun findFirstByEpisodeIdAndStatus(episodeId: Long, status: PublishVersionStatus): PublishVersionEntity?

    /**
     * 에피소드와 버전으로 조회한다.
     */
    fun findByEpisodeIdAndVersion(episodeId: Long, version: Long): PublishVersionEntity?

    /**
     * 에피소드와 requestId로 조회한다.
     */
    fun findByEpisodeIdAndRequestId(episodeId: Long, requestId: String): PublishVersionEntity?
}
