package com.inkflow.workflow.domain

/**
 * 워크플로우 감사 로그를 저장하기 위한 저장소 계약.
 */
interface WorkflowAuditLogRepository {
    /**
     * 상태 전이 감사 로그를 저장한다.
     */
    fun save(log: WorkflowAuditLog): WorkflowAuditLog
}
