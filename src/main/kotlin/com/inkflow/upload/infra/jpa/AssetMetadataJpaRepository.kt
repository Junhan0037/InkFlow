package com.inkflow.upload.infra.jpa

import org.springframework.data.jpa.repository.JpaRepository

/**
 * Asset 메타데이터를 조회하기 위한 JPA Repository.
 */
interface AssetMetadataJpaRepository : JpaRepository<AssetMetadataEntity, Long> {
    /**
     * 업로드 ID로 Asset 메타데이터를 조회한다.
     */
    fun findByUploadId(uploadId: String): AssetMetadataEntity?
}
