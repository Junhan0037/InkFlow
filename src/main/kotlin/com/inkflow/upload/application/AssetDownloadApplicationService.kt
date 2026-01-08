package com.inkflow.upload.application

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.upload.domain.AssetMetadata
import com.inkflow.upload.domain.AssetMetadataRepository
import com.inkflow.upload.domain.AssetStatus
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * Asset 다운로드 presigned URL 발급 로직을 담당하는 애플리케이션 서비스.
 */
@Service
class AssetDownloadApplicationService(
    private val assetMetadataRepository: AssetMetadataRepository,
    private val presignedDownloadUrlProvider: PresignedDownloadUrlProvider,
    private val properties: AssetDownloadProperties,
    private val clock: Clock
) {
    /**
     * 다운로드 권한을 확인하고 presigned URL을 발급한다.
     */
    fun issueDownloadUrl(command: AssetDownloadCommand): AssetDownloadResult {
        validate(command)

        val asset = assetMetadataRepository.findById(command.assetId)
            ?: throw BusinessException(
                errorCode = ErrorCode.NOT_FOUND,
                details = mapOf("assetId" to command.assetId.toString()),
                message = "Asset 메타데이터를 찾을 수 없습니다."
            )

        verifyAccess(asset, command.requesterId)
        validateAssetState(asset)

        val now = Instant.now(clock)
        val expiresAt = now.plus(properties.ttl)

        // 스토리지 키를 기반으로 다운로드 presigned URL을 생성한다.
        val presigned = presignedDownloadUrlProvider.createPresignedDownload(
            bucket = asset.storageBucket,
            key = asset.storageKey,
            contentType = asset.contentType,
            expiresAt = expiresAt
        )

        val assetId = asset.id ?: throw missingAssetId(command.assetId)
        return AssetDownloadResult(
            assetId = assetId,
            fileName = asset.fileName,
            contentType = asset.contentType,
            size = asset.size,
            url = presigned.url,
            expiresAt = expiresAt
        )
    }

    /**
     * 다운로드 요청 값의 기본 유효성을 검증한다.
     */
    private fun validate(command: AssetDownloadCommand) {
        if (command.assetId <= 0) {
            throw invalid("assetId", "assetId는 1 이상이어야 합니다.")
        }
        if (command.requesterId.isBlank()) {
            throw invalid("requesterId", "requesterId는 비어 있을 수 없습니다.")
        }
    }

    /**
     * 요청자 권한을 검증한다.
     */
    private fun verifyAccess(asset: AssetMetadata, requesterId: String) {
        if (asset.creatorId != requesterId) {
            throw BusinessException(
                errorCode = ErrorCode.FORBIDDEN,
                details = mapOf("assetId" to (asset.id?.toString() ?: "unknown")),
                message = "해당 Asset에 대한 다운로드 권한이 없습니다."
            )
        }
    }

    /**
     * 다운로드 가능한 상태인지 확인한다.
     */
    private fun validateAssetState(asset: AssetMetadata) {
        if (asset.status != AssetStatus.STORED) {
            throw BusinessException(
                errorCode = ErrorCode.INVALID_STATE,
                details = mapOf(
                    "assetId" to (asset.id?.toString() ?: "unknown"),
                    "status" to asset.status.name
                ),
                message = "다운로드 가능한 상태가 아닙니다."
            )
        }
    }

    /**
     * 요청 오류를 표준 예외로 변환한다.
     */
    private fun invalid(field: String, message: String): BusinessException {
        return BusinessException(
            errorCode = ErrorCode.INVALID_REQUEST,
            details = mapOf("field" to field),
            message = message
        )
    }

    /**
     * Asset 식별자가 누락된 경우 시스템 예외를 생성한다.
     */
    private fun missingAssetId(assetId: Long): SystemException {
        return SystemException(
            errorCode = ErrorCode.INTERNAL_ERROR,
            details = mapOf("assetId" to assetId.toString()),
            message = "Asset 식별자를 확인할 수 없습니다."
        )
    }
}
