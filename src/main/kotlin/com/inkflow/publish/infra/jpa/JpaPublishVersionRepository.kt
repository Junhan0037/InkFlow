package com.inkflow.publish.infra.jpa

import com.inkflow.publish.domain.PublishVersion
import com.inkflow.publish.domain.PublishVersionRepository
import com.inkflow.publish.domain.PublishVersionStatus
import org.springframework.stereotype.Repository

/**
 * JPA 기반 퍼블리시 버전 저장소 구현체.
 */
@Repository
class JpaPublishVersionRepository(
    private val publishVersionJpaRepository: PublishVersionJpaRepository
) : PublishVersionRepository {
    /**
     * 퍼블리시 버전을 저장한다.
     */
    override fun save(version: PublishVersion): PublishVersion {
        val entity = PublishVersionEntity.fromDomain(version)
        return publishVersionJpaRepository.save(entity).toDomain()
    }

    /**
     * 에피소드별 최신 퍼블리시 버전을 조회한다.
     */
    override fun findLatestByEpisodeId(episodeId: Long): PublishVersion? {
        return publishVersionJpaRepository.findTopByEpisodeIdOrderByVersionDesc(episodeId)?.toDomain()
    }

    /**
     * 에피소드별 활성 퍼블리시 버전을 조회한다.
     */
    override fun findActiveByEpisodeId(episodeId: Long): PublishVersion? {
        return publishVersionJpaRepository.findFirstByEpisodeIdAndStatus(
            episodeId,
            PublishVersionStatus.ACTIVE
        )?.toDomain()
    }

    /**
     * 에피소드와 버전으로 퍼블리시 버전을 조회한다.
     */
    override fun findByEpisodeIdAndVersion(episodeId: Long, version: Long): PublishVersion? {
        return publishVersionJpaRepository.findByEpisodeIdAndVersion(episodeId, version)?.toDomain()
    }

    /**
     * 에피소드와 requestId로 퍼블리시 버전을 조회한다.
     */
    override fun findByEpisodeIdAndRequestId(episodeId: Long, requestId: String): PublishVersion? {
        return publishVersionJpaRepository.findByEpisodeIdAndRequestId(episodeId, requestId)?.toDomain()
    }
}
