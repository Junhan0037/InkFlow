package com.inkflow.upload.infra.jpa

import com.inkflow.upload.domain.EpisodeAccessRepository
import org.springframework.stereotype.Repository

/**
 * JPA 기반 에피소드 업로드 권한 조회 구현체.
 */
@Repository
class JpaEpisodeAccessRepository(
    private val episodeAccessJpaRepository: EpisodeAccessJpaRepository
) : EpisodeAccessRepository {
    /**
     * 에피소드에 연결된 creatorId를 조회한다.
     */
    override fun findCreatorIdByEpisodeId(episodeId: Long): String? {
        return episodeAccessJpaRepository.findCreatorIdByEpisodeId(episodeId)
    }
}
