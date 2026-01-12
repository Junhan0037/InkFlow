package com.inkflow.workflow.infra.mongo

import com.inkflow.workflow.domain.WorkflowAuditLog
import com.inkflow.workflow.domain.WorkflowStatus
import com.inkflow.workflow.domain.WorkflowTransitionAction
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * MongoDB에 저장하는 워크플로우 상태 전이 감사 로그 문서.
 */
@Document(collection = "workflow_audit_logs")
data class WorkflowAuditLogDocument(
    @Id
    val id: String? = null,
    @Indexed
    val episodeId: Long,
    val actorId: String,
    val action: WorkflowTransitionAction,
    val fromState: WorkflowStatus,
    val toState: WorkflowStatus,
    val fromVersion: Int,
    val toVersion: Int,
    val reason: String?,
    val comment: String?,
    @Indexed
    val occurredAt: Instant
) {
    companion object {
        /**
         * 도메인 모델을 MongoDB 문서로 변환한다.
         */
        fun fromDomain(log: WorkflowAuditLog): WorkflowAuditLogDocument {
            return WorkflowAuditLogDocument(
                id = log.id,
                episodeId = log.episodeId,
                actorId = log.actorId,
                action = log.action,
                fromState = log.fromState,
                toState = log.toState,
                fromVersion = log.fromVersion,
                toVersion = log.toVersion,
                reason = log.reason,
                comment = log.comment,
                occurredAt = log.occurredAt
            )
        }
    }

    /**
     * MongoDB 문서를 도메인 모델로 변환한다.
     */
    fun toDomain(): WorkflowAuditLog {
        return WorkflowAuditLog(
            id = id,
            episodeId = episodeId,
            actorId = actorId,
            action = action,
            fromState = fromState,
            toState = toState,
            fromVersion = fromVersion,
            toVersion = toVersion,
            reason = reason,
            comment = comment,
            occurredAt = occurredAt
        )
    }
}
