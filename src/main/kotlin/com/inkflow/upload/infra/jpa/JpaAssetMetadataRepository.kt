package com.inkflow.upload.infra.jpa

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.upload.domain.AssetMetadata
import com.inkflow.upload.domain.AssetMetadataRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository

/**
 * JPA 기반 Asset 메타데이터 저장소 구현체.
 */
@Repository
class JpaAssetMetadataRepository(
    private val assetMetadataJpaRepository: AssetMetadataJpaRepository
) : AssetMetadataRepository {
    /**
     * Asset 메타데이터를 저장하고 충돌 시 CONFLICT 예외를 발생시킨다.
     */
    override fun save(asset: AssetMetadata): AssetMetadata {
        val entity = AssetMetadataEntity.fromDomain(asset)
        return try {
            assetMetadataJpaRepository.save(entity).toDomain()
        } catch (exception: DataIntegrityViolationException) {
            throw BusinessException(
                errorCode = ErrorCode.CONFLICT,
                details = mapOf(
                    "uploadId" to asset.uploadId,
                    "episodeId" to asset.episodeId.toString(),
                    "checksum" to asset.checksum
                ),
                message = "이미 존재하는 Asset 메타데이터입니다."
            )
        }
    }

    /**
     * 업로드 ID로 Asset 메타데이터를 조회한다.
     */
    override fun findByUploadId(uploadId: String): AssetMetadata? {
        return assetMetadataJpaRepository.findByUploadId(uploadId)?.toDomain()
    }
}
