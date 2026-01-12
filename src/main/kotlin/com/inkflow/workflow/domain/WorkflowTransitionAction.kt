package com.inkflow.workflow.domain

/**
 * 워크플로우 상태 전이를 발생시키는 액션 유형을 정의한다.
 */
enum class WorkflowTransitionAction {
    SUBMIT, // 제출
    START_REVIEW, // 검수 시작
    APPROVE, // 승인
    REJECT // 반려
}
