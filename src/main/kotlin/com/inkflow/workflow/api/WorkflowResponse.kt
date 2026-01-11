package com.inkflow.workflow.api

import com.inkflow.workflow.domain.WorkflowStatus

/**
 * 워크플로우 상태 응답 DTO.
 */
data class WorkflowStateResponse(
    val episodeId: Long,
    val state: WorkflowStatus,
    val version: Int
)
