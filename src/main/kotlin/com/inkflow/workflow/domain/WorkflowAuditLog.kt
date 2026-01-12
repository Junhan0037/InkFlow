package com.inkflow.workflow.domain

import java.time.Instant

/**
 * 워크플로우 상태 전이 감사 로그 도메인 모델.
 */
data class WorkflowAuditLog(
    val id: String? = null,
    val episodeId: Long,
    val actorId: String,
    val action: WorkflowTransitionAction,
    val fromState: WorkflowStatus,
    val toState: WorkflowStatus,
    val fromVersion: Int,
    val toVersion: Int,
    val reason: String?,
    val comment: String?,
    val occurredAt: Instant
) {
    init {
        // 감사 로그 기본 무결성을 보장한다.
        require(episodeId > 0) { "episodeId는 1 이상이어야 합니다." }
        require(actorId.isNotBlank()) { "actorId는 비어 있을 수 없습니다." }
        require(fromVersion > 0) { "fromVersion은 1 이상이어야 합니다." }
        require(toVersion > 0) { "toVersion은 1 이상이어야 합니다." }
        require(toVersion > fromVersion) { "toVersion은 fromVersion보다 커야 합니다." }
    }
}
