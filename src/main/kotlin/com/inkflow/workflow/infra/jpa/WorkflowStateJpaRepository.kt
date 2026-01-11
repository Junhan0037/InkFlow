package com.inkflow.workflow.infra.jpa

import org.springframework.data.jpa.repository.JpaRepository

/**
 * 워크플로우 상태 엔티티 JPA 저장소.
 */
interface WorkflowStateJpaRepository : JpaRepository<WorkflowStateEntity, Long>
