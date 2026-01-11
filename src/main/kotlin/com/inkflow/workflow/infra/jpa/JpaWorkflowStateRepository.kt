package com.inkflow.workflow.infra.jpa

import com.inkflow.workflow.domain.WorkflowState
import com.inkflow.workflow.domain.WorkflowStateRepository
import org.springframework.stereotype.Repository

/**
 * JPA 기반 워크플로우 상태 저장소 구현체.
 */
@Repository
class JpaWorkflowStateRepository(
    private val workflowStateJpaRepository: WorkflowStateJpaRepository
) : WorkflowStateRepository {
    /**
     * 워크플로우 상태를 저장한다.
     */
    override fun save(state: WorkflowState): WorkflowState {
        val entity = WorkflowStateEntity.fromDomain(state)
        return workflowStateJpaRepository.save(entity).toDomain()
    }

    /**
     * 에피소드 ID로 워크플로우 상태를 조회한다.
     */
    override fun findByEpisodeId(episodeId: Long): WorkflowState? {
        return workflowStateJpaRepository.findById(episodeId).orElse(null)?.toDomain()
    }
}
