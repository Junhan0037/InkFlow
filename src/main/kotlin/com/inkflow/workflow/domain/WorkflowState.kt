package com.inkflow.workflow.domain

import java.time.Instant

/**
 * 에피소드의 워크플로우 상태와 버전을 관리하는 도메인 모델.
 */
data class WorkflowState(
    val episodeId: Long,
    val state: WorkflowStatus,
    val version: Int,
    val updatedAt: Instant
) {
    init {
        require(episodeId > 0) { "episodeId는 1 이상이어야 합니다." }
        require(version > 0) { "version은 1 이상이어야 합니다." }
    }

    /**
     * 제출 단계로 전이한다.
     */
    fun submit(now: Instant): WorkflowState {
        require(state == WorkflowStatus.DRAFT) { "DRAFT 상태에서만 SUBMITTED로 전이할 수 있습니다." }
        return transitionTo(WorkflowStatus.SUBMITTED, now)
    }

    /**
     * 검수 시작 단계로 전이한다.
     */
    fun startReview(now: Instant): WorkflowState {
        require(state == WorkflowStatus.SUBMITTED) { "SUBMITTED 상태에서만 REVIEWING으로 전이할 수 있습니다." }
        return transitionTo(WorkflowStatus.REVIEWING, now)
    }

    /**
     * 승인 단계로 전이한다.
     */
    fun approve(now: Instant): WorkflowState {
        require(state == WorkflowStatus.REVIEWING) { "REVIEWING 상태에서만 APPROVED로 전이할 수 있습니다." }
        return transitionTo(WorkflowStatus.APPROVED, now)
    }

    /**
     * 상태 전이와 버전 증가를 공통 처리한다.
     */
    private fun transitionTo(target: WorkflowStatus, now: Instant): WorkflowState {
        // 상태 전이 시 버전을 증가시켜 낙관적 잠금 기준을 맞춘다.
        return copy(state = target, version = version + 1, updatedAt = now)
    }

    companion object {
        /**
         * DRAFT 상태로 초기 워크플로우를 생성한다.
         */
        fun createDraft(episodeId: Long, now: Instant): WorkflowState {
            return WorkflowState(
                episodeId = episodeId,
                state = WorkflowStatus.DRAFT,
                version = 1,
                updatedAt = now
            )
        }
    }
}
