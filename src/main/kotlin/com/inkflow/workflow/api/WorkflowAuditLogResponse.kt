package com.inkflow.workflow.api

import java.time.Instant

/**
 * 워크플로우 감사 로그 페이지 응답.
 */
data class WorkflowAuditLogPageResponse(
    val items: List<WorkflowAuditLogSummaryResponse>,
    val total: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int
)

/**
 * 워크플로우 감사 로그 요약 응답.
 */
data class WorkflowAuditLogSummaryResponse(
    val id: String,
    val episodeId: Long,
    val actorId: String,
    val action: String,
    val fromState: String,
    val toState: String,
    val fromVersion: Int,
    val toVersion: Int,
    val reason: String?,
    val comment: String?,
    val occurredAt: Instant
)
