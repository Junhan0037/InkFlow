package com.inkflow.media.domain

/**
 * 파생 리소스 메타데이터를 저장/조회하기 위한 저장소 계약.
 */
interface DerivativeMetadataRepository {
    /**
     * 파생 리소스 메타데이터를 저장한다.
     */
    fun save(derivative: DerivativeMetadata): DerivativeMetadata

    /**
     * 동일 스펙의 파생 메타를 조회한다.
     */
    fun findBySpec(
        assetId: Long,
        type: DerivativeType,
        width: Int?,
        height: Int?,
        format: String
    ): DerivativeMetadata?
}
