package com.inkflow.workflow.infra.mongo

import org.springframework.data.mongodb.repository.MongoRepository

/**
 * MongoDB 워크플로우 감사 로그 Spring Data 리포지토리.
 */
interface WorkflowAuditLogMongoRepository : MongoRepository<WorkflowAuditLogDocument, String>
