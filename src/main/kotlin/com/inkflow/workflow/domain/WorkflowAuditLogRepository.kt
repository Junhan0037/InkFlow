package com.inkflow.workflow.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.Instant

/**
 * 워크플로우 감사 로그를 저장하기 위한 저장소 계약.
 */
interface WorkflowAuditLogRepository {
    /**
     * 상태 전이 감사 로그를 저장한다.
     */
    fun save(log: WorkflowAuditLog): WorkflowAuditLog

    /**
     * 검색 조건과 페이징 조건으로 감사 로그를 조회한다.
     */
    fun search(
        episodeId: Long?,
        actorId: String?,
        action: WorkflowTransitionAction?,
        fromState: WorkflowStatus?,
        toState: WorkflowStatus?,
        occurredFrom: Instant?,
        occurredTo: Instant?,
        pageable: Pageable
    ): Page<WorkflowAuditLog>
}
