package com.inkflow.media.application

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.common.idempotency.IdempotencyDecision
import com.inkflow.media.domain.DerivativeType
import com.inkflow.media.domain.DerivativeType.THUMBNAIL
import com.inkflow.media.domain.DerivativeMetadataRepository
import com.inkflow.upload.domain.AssetMetadata
import com.inkflow.upload.domain.AssetMetadataRepository
import com.inkflow.upload.domain.AssetStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Media 작업 요청을 처리하는 애플리케이션 서비스.
 */
@Service
class MediaJobApplicationService(
    private val assetMetadataRepository: AssetMetadataRepository,
    private val derivativeMetadataRepository: DerivativeMetadataRepository,
    private val mediaStorageClient: MediaStorageClient,
    private val mediaImageProcessor: MediaImageProcessor,
    private val thumbnailProperties: MediaThumbnailProperties,
    private val mediaDerivativeResultService: MediaDerivativeResultService,
    private val mediaJobIdempotencyService: MediaJobIdempotencyService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Media 작업을 수신해 처리 파이프라인으로 전달한다.
     */
    fun handleJob(command: MediaJobCommand, metadata: MediaJobMessageMetadata) {
        val derivativeType = DerivativeType.from(command.derivativeType)
            ?: throw invalidDerivativeType(command.derivativeType)
        when (derivativeType) {
            THUMBNAIL -> handleThumbnail(command, metadata)
            DerivativeType.RESIZED,
            DerivativeType.TRANSCODED -> {
                throw unsupportedDerivativeType(derivativeType)
            }
        }
    }

    /**
     * 썸네일 생성 파이프라인을 실행한다.
     */
    private fun handleThumbnail(command: MediaJobCommand, metadata: MediaJobMessageMetadata) {
        val startedAt = System.nanoTime()
        logger.info(
            "썸네일 생성 작업 시작. jobId={}, assetId={}, eventId={}, traceId={}",
            command.jobId,
            command.assetId,
            metadata.eventId,
            metadata.traceId
        )
        val asset = assetMetadataRepository.findById(command.assetId)
            ?: throw missingAsset(command.assetId)
        val assetId = asset.id ?: throw missingAssetId(command.assetId)
        validateAssetForThumbnail(asset)

        val idempotencyKey = MediaJobIdempotencyKeys.forDerivative(
            assetId = assetId,
            contentHash = asset.checksum,
            derivativeType = command.derivativeType,
            spec = command.spec
        )
        // 기존 파생 메타가 존재하면 중복 작업을 수행하지 않는다.
        val existingDerivative = derivativeMetadataRepository.findBySpec(
            assetId = assetId,
            type = DerivativeType.THUMBNAIL,
            width = command.spec.width,
            height = command.spec.height,
            format = command.spec.format
        )
        if (existingDerivative != null) {
            logger.info(
                "이미 처리된 썸네일 작업이므로 재처리를 건너뜁니다. jobId={}, assetId={}, derivativeId={}",
                command.jobId,
                assetId,
                existingDerivative.id
            )
            mediaJobIdempotencyService.markCompleted(idempotencyKey)
            return
        }

        val idempotencyDecision = mediaJobIdempotencyService.tryBegin(idempotencyKey)
        if (idempotencyDecision != IdempotencyDecision.STARTED) {
            // 동일 콘텐츠+스펙 작업 중복 처리를 방지한다.
            logger.info(
                "썸네일 작업이 이미 처리 중/완료 상태입니다. jobId={}, assetId={}, decision={}",
                command.jobId,
                assetId,
                idempotencyDecision
            )
            return
        }

        try {
            val sourceLocation = MediaStorageLocation(asset.storageBucket, asset.storageKey)
            val originalObject = mediaStorageClient.download(sourceLocation)
            val thumbnailResult = mediaImageProcessor.createThumbnail(originalObject.bytes, command.spec)

            val targetBucket = thumbnailProperties.storageBucket?.takeIf { it.isNotBlank() } ?: asset.storageBucket
            val targetKey = buildThumbnailKey(assetId, command.jobId, thumbnailResult)

            mediaStorageClient.upload(
                location = MediaStorageLocation(targetBucket, targetKey),
                contentType = thumbnailResult.contentType,
                bytes = thumbnailResult.bytes
            )

            val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

            // 파생 메타 저장과 결과 이벤트 기록을 Outbox 트랜잭션으로 묶어 정합성을 확보한다.
            val savedDerivative = mediaDerivativeResultService.recordThumbnailResult(
                command = command,
                metadata = metadata,
                storageKey = targetKey,
                thumbnailResult = thumbnailResult,
                durationMs = durationMs
            )
            mediaJobIdempotencyService.markCompleted(idempotencyKey)

            logger.info(
                "썸네일 생성 완료. jobId={}, assetId={}, bucket={}, key={}, sizeBytes={}, derivativeId={}, durationMs={}",
                command.jobId,
                assetId,
                targetBucket,
                targetKey,
                thumbnailResult.bytes.size,
                savedDerivative.id,
                durationMs
            )
        } catch (exception: Exception) {
            // 실패 시 동일 작업 재시도를 허용하도록 멱등성 기록을 제거한다.
            mediaJobIdempotencyService.markFailed(idempotencyKey)
            throw exception
        }
    }

    /**
     * 썸네일 생성 대상 Asset의 상태/타입을 검증한다.
     */
    private fun validateAssetForThumbnail(asset: AssetMetadata) {
        if (asset.status != AssetStatus.STORED) {
            throw invalidState("STORED 상태의 Asset만 썸네일을 생성할 수 있습니다.")
        }
        if (!asset.contentType.startsWith("image/")) {
            throw invalid("contentType", "이미지 Asset만 썸네일 생성이 가능합니다.")
        }
    }

    /**
     * 썸네일 저장 키를 구성한다.
     */
    private fun buildThumbnailKey(assetId: Long, jobId: String, result: MediaThumbnailResult): String {
        val normalizedPrefix = normalizePrefix(thumbnailProperties.storageKeyPrefix)
        val fileName = "${jobId}_${result.width}x${result.height}.${result.format}"
        // Asset별 폴더를 두어 파생 리소스가 한 곳에 모이도록 구성한다.
        return "$normalizedPrefix$assetId/$fileName"
    }

    /**
     * 스토리지 키 prefix를 경로 형태로 정규화한다.
     */
    private fun normalizePrefix(prefix: String): String {
        val trimmed = prefix.trim().trimStart('/')
        if (trimmed.isBlank()) {
            return ""
        }
        return if (trimmed.endsWith('/')) trimmed else "$trimmed/"
    }

    /**
     * Asset이 존재하지 않을 때 사용하는 예외를 생성한다.
     */
    private fun missingAsset(assetId: Long): BusinessException {
        return BusinessException(
            errorCode = ErrorCode.NOT_FOUND,
            details = mapOf("assetId" to assetId.toString()),
            message = "Asset을 찾을 수 없습니다."
        )
    }

    /**
     * Asset 식별자 누락을 시스템 예외로 변환한다.
     */
    private fun missingAssetId(assetId: Long): SystemException {
        return SystemException(
            errorCode = ErrorCode.INTERNAL_ERROR,
            details = mapOf("assetId" to assetId.toString()),
            message = "Asset 식별자를 확인할 수 없습니다."
        )
    }

    /**
     * 파생 타입 오류를 표준 예외로 변환한다.
     */
    private fun invalidDerivativeType(derivativeType: String): BusinessException {
        return BusinessException(
            errorCode = ErrorCode.INVALID_REQUEST,
            details = mapOf("derivativeType" to derivativeType),
            message = "지원하지 않는 파생 타입입니다."
        )
    }

    /**
     * 아직 구현되지 않은 파생 타입을 처리한다.
     */
    private fun unsupportedDerivativeType(derivativeType: DerivativeType): BusinessException {
        return BusinessException(
            errorCode = ErrorCode.INVALID_REQUEST,
            details = mapOf("derivativeType" to derivativeType.name),
            message = "아직 지원하지 않는 파생 타입입니다."
        )
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
     * 상태 오류를 표준 예외로 변환한다.
     */
    private fun invalidState(message: String): BusinessException {
        return BusinessException(
            errorCode = ErrorCode.INVALID_STATE,
            message = message
        )
    }
}

/**
 * Media 작업 메시지 메타데이터를 전달하는 DTO.
 */
data class MediaJobMessageMetadata(
    val eventId: UUID,
    val traceId: String?,
    val idempotencyKey: String?
) {
    init {
        require(traceId?.isNotBlank() ?: true) { "traceId는 빈 문자열일 수 없습니다." }
        require(idempotencyKey?.isNotBlank() ?: true) { "idempotencyKey는 빈 문자열일 수 없습니다." }
    }
}
