package com.inkflow.upload.domain

/**
 * Asset 메타데이터를 저장/조회하기 위한 저장소 계약.
 */
interface AssetMetadataRepository {
    /**
     * Asset 메타데이터를 저장한다.
     */
    fun save(asset: AssetMetadata): AssetMetadata

    /**
     * 업로드 ID로 Asset 메타데이터를 조회한다.
     */
    fun findByUploadId(uploadId: String): AssetMetadata?
}
