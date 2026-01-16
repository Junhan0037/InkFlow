package com.inkflow.media.domain

/**
 * 파생 리소스 메타데이터를 저장/조회하기 위한 저장소 계약.
 */
interface DerivativeMetadataRepository {
    /**
     * 파생 리소스 메타데이터를 저장한다.
     */
    fun save(derivative: DerivativeMetadata): DerivativeMetadata
}
