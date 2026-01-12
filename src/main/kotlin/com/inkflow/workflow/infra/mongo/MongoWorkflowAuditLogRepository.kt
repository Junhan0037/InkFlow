package com.inkflow.workflow.infra.mongo

import com.inkflow.workflow.domain.WorkflowAuditLog
import com.inkflow.workflow.domain.WorkflowAuditLogRepository
import org.springframework.stereotype.Repository

/**
 * MongoDB 기반 워크플로우 감사 로그 저장소 구현체.
 */
@Repository
class MongoWorkflowAuditLogRepository(
    private val workflowAuditLogMongoRepository: WorkflowAuditLogMongoRepository
) : WorkflowAuditLogRepository {
    /**
     * 감사 로그를 저장하고 저장 결과를 도메인 모델로 반환한다.
     */
    override fun save(log: WorkflowAuditLog): WorkflowAuditLog {
        val document = WorkflowAuditLogDocument.fromDomain(log)
        return workflowAuditLogMongoRepository.save(document).toDomain()
    }
}
