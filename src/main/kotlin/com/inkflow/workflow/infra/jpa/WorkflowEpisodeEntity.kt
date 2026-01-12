package com.inkflow.workflow.infra.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 워크플로우 이벤트 발행을 위해 Episode의 최소 필드만 매핑한다.
 */
@Entity
@Table(name = "episode")
class WorkflowEpisodeEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: Long = 0L,

    @Column(name = "work_id", nullable = false)
    var workId: Long = 0L
)
