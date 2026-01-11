package com.inkflow.workflow.domain

/**
 * 에피소드 워크플로우 상태를 정의한다.
 */
enum class WorkflowStatus {
    DRAFT,
    SUBMITTED,
    REVIEWING,
    APPROVED,
    REJECTED,
    PUBLISHING,
    PUBLISHED,
    ROLLED_BACK
}
