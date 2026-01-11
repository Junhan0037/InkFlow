package com.inkflow.workflow.infra.jpa

import com.inkflow.workflow.domain.WorkflowState
import com.inkflow.workflow.domain.WorkflowStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 워크플로우 상태를 JPA로 영속화하기 위한 엔티티.
 */
@Entity
@Table(name = "workflow_state")
class WorkflowStateEntity(
    @Id
    @Column(name = "episode_id", nullable = false)
    var episodeId: Long = 0L,

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    var state: WorkflowStatus = WorkflowStatus.DRAFT,

    @Column(name = "version", nullable = false)
    var version: Int = 1,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH
) {
    /**
     * 엔티티를 도메인 모델로 변환한다.
     */
    fun toDomain(): WorkflowState {
        return WorkflowState(
            episodeId = episodeId,
            state = state,
            version = version,
            updatedAt = updatedAt
        )
    }

    companion object {
        /**
         * 도메인 모델을 엔티티로 변환한다.
         */
        fun fromDomain(state: WorkflowState): WorkflowStateEntity {
            return WorkflowStateEntity(
                episodeId = state.episodeId,
                state = state.state,
                version = state.version,
                updatedAt = state.updatedAt
            )
        }
    }
}
