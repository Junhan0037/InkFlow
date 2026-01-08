package com.inkflow.upload.infra.jpa

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * 업로드 세션 엔티티 JPA 저장소.
 */
interface UploadSessionJpaRepository : JpaRepository<UploadSessionEntity, UUID> {
    /**
     * 업로드 ID로 세션을 조회한다.
     */
    fun findByUploadId(uploadId: String): UploadSessionEntity?
}
