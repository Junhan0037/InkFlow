package com.inkflow.workflow.application

import com.inkflow.workflow.domain.WorkflowStatus

/**
 * 워크플로우 전이 결과를 응답 계층으로 전달하기 위한 DTO.
 */
data class WorkflowTransitionResult(
    val episodeId: Long,
    val state: WorkflowStatus,
    val version: Int
)
