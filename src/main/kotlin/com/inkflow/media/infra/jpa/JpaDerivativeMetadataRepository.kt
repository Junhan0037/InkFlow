package com.inkflow.media.infra.jpa

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.media.domain.DerivativeMetadata
import com.inkflow.media.domain.DerivativeMetadataRepository
import com.inkflow.media.domain.DerivativeType
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository

/**
 * JPA 기반 Derivative 메타데이터 저장소 구현체.
 */
@Repository
class JpaDerivativeMetadataRepository(
    private val derivativeMetadataJpaRepository: DerivativeMetadataJpaRepository
) : DerivativeMetadataRepository {
    /**
     * Derivative 메타데이터를 저장하고 충돌 시 CONFLICT 예외를 발생시킨다.
     */
    override fun save(derivative: DerivativeMetadata): DerivativeMetadata {
        val entity = DerivativeMetadataEntity.fromDomain(derivative)
        return try {
            derivativeMetadataJpaRepository.save(entity).toDomain()
        } catch (exception: DataIntegrityViolationException) {
            throw BusinessException(
                errorCode = ErrorCode.CONFLICT,
                details = mapOf(
                    "assetId" to derivative.assetId.toString(),
                    "type" to derivative.type.name,
                    "width" to (derivative.width?.toString() ?: "null"),
                    "height" to (derivative.height?.toString() ?: "null"),
                    "format" to derivative.format
                ),
                message = "이미 존재하는 Derivative 메타데이터입니다."
            )
        }
    }

    /**
     * 동일 스펙의 파생 메타데이터를 조회한다.
     */
    override fun findBySpec(
        assetId: Long,
        type: DerivativeType,
        width: Int?,
        height: Int?,
        format: String
    ): DerivativeMetadata? {
        return derivativeMetadataJpaRepository
            .findByAssetIdAndTypeAndWidthAndHeightAndFormat(assetId, type, width, height, format)
            ?.toDomain()
    }
}
