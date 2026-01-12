package com.inkflow.workflow.infra.jpa

import com.inkflow.workflow.domain.EpisodeQueryRepository
import org.springframework.stereotype.Repository

/**
 * JPA 기반 에피소드 조회 저장소 구현체.
 */
@Repository
class JpaEpisodeQueryRepository(
    private val workflowEpisodeJpaRepository: WorkflowEpisodeJpaRepository
) : EpisodeQueryRepository {
    /**
     * 에피소드에 연결된 workId를 조회한다.
     */
    override fun findWorkIdByEpisodeId(episodeId: Long): Long? {
        return workflowEpisodeJpaRepository.findWorkIdByEpisodeId(episodeId)
    }
}
