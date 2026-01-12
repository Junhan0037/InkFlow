package com.inkflow.workflow.infra.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * 워크플로우 이벤트 발행에 필요한 Episode 조회용 JPA 리포지토리.
 */
interface WorkflowEpisodeJpaRepository : JpaRepository<WorkflowEpisodeEntity, Long> {
    /**
     * 에피소드 ID로 workId를 조회한다.
     */
    @Query("select e.workId from WorkflowEpisodeEntity e where e.id = :episodeId")
    fun findWorkIdByEpisodeId(@Param("episodeId") episodeId: Long): Long?
}
