package com.inkflow.media.infra.jpa

import com.inkflow.media.domain.DerivativeType
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Derivative 메타데이터를 조회하기 위한 JPA Repository.
 */
interface DerivativeMetadataJpaRepository : JpaRepository<DerivativeMetadataEntity, Long> {
    /**
     * 동일 스펙의 Derivative 메타데이터를 조회한다.
     */
    fun findByAssetIdAndTypeAndWidthAndHeightAndFormat(
        assetId: Long,
        type: DerivativeType,
        width: Int?,
        height: Int?,
        format: String
    ): DerivativeMetadataEntity?
}
