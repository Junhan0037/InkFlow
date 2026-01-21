package com.inkflow.workflow.infra.mongo

import com.inkflow.workflow.domain.WorkflowAuditLog
import com.inkflow.workflow.domain.WorkflowAuditLogRepository
import com.inkflow.workflow.domain.WorkflowStatus
import com.inkflow.workflow.domain.WorkflowTransitionAction
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * MongoDB 기반 워크플로우 감사 로그 저장소 구현체.
 */
@Repository
class MongoWorkflowAuditLogRepository(
    private val workflowAuditLogMongoRepository: WorkflowAuditLogMongoRepository,
    private val mongoTemplate: MongoTemplate
) : WorkflowAuditLogRepository {
    /**
     * 감사 로그를 저장하고 저장 결과를 도메인 모델로 반환한다.
     */
    override fun save(log: WorkflowAuditLog): WorkflowAuditLog {
        val document = WorkflowAuditLogDocument.fromDomain(log)
        return workflowAuditLogMongoRepository.save(document).toDomain()
    }

    /**
     * 검색 조건에 맞는 감사 로그를 조회한다.
     */
    override fun search(
        episodeId: Long?,
        actorId: String?,
        action: WorkflowTransitionAction?,
        fromState: WorkflowStatus?,
        toState: WorkflowStatus?,
        occurredFrom: Instant?,
        occurredTo: Instant?,
        pageable: Pageable
    ): Page<WorkflowAuditLog> {
        val query = Query()
        val criteriaList = mutableListOf<Criteria>()

        episodeId?.let { criteriaList.add(Criteria.where("episodeId").`is`(it)) }
        actorId?.let { criteriaList.add(Criteria.where("actorId").`is`(it)) }
        action?.let { criteriaList.add(Criteria.where("action").`is`(it)) }
        fromState?.let { criteriaList.add(Criteria.where("fromState").`is`(it)) }
        toState?.let { criteriaList.add(Criteria.where("toState").`is`(it)) }

        if (occurredFrom != null || occurredTo != null) {
            val occurredCriteria = Criteria.where("occurredAt")
            occurredFrom?.let { occurredCriteria.gte(it) }
            occurredTo?.let { occurredCriteria.lte(it) }
            criteriaList.add(occurredCriteria)
        }

        // 모든 검색 조건을 하나의 AND 조건으로 결합한다.
        when (criteriaList.size) {
            1 -> query.addCriteria(criteriaList.first())
            in 2..Int.MAX_VALUE -> query.addCriteria(Criteria().andOperator(*criteriaList.toTypedArray()))
        }

        query.with(pageable)

        val countQuery = Query.of(query).limit(-1).skip(-1)
        val total = mongoTemplate.count(countQuery, WorkflowAuditLogDocument::class.java)
        val documents = mongoTemplate.find(query, WorkflowAuditLogDocument::class.java)
        val content = documents.map { it.toDomain() }
        return PageImpl(content, pageable, total)
    }
}
