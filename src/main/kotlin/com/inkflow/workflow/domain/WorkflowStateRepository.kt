package com.inkflow.workflow.domain

/**
 * 워크플로우 상태를 저장/조회하기 위한 저장소 계약.
 */
interface WorkflowStateRepository {
    /**
     * 워크플로우 상태를 저장한다.
     */
    fun save(state: WorkflowState): WorkflowState

    /**
     * 에피소드 ID로 워크플로우 상태를 조회한다.
     */
    fun findByEpisodeId(episodeId: Long): WorkflowState?
}
