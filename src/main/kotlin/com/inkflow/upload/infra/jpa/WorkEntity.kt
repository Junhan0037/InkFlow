package com.inkflow.upload.infra.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 에피소드 업로드 권한 확인에 필요한 Work 엔티티를 최소 필드로 매핑한다.
 */
@Entity
@Table(name = "work")
class WorkEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: Long = 0L,

    @Column(name = "creator_id", nullable = false)
    var creatorId: String = ""
)
