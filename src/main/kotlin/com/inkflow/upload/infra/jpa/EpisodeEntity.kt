package com.inkflow.upload.infra.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * 업로드 권한 검증을 위해 Episode와 Work 연관을 제공하는 엔티티.
 */
@Entity
@Table(name = "episode")
class EpisodeEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_id", nullable = false)
    var work: WorkEntity = WorkEntity()
)
