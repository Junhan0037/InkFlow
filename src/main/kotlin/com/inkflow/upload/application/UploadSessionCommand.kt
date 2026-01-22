package com.inkflow.upload.application

import com.inkflow.upload.domain.AssetStatus
import java.time.Instant

/**
 * 업로드 세션 생성 요청을 전달하기 위한 커맨드.
 */
data class CreateUploadSessionCommand(
    val episodeId: Long,
    val creatorId: String,
    val fileName: String,
    val contentType: String,
    val size: Long,
    val checksum: String,
    val totalParts: Int
)

/**
 * presigned URL 리스트를 표현한다.
 */
data class PresignedPartUrl(
    val partNumber: Int,
    val url: String,
    val accelerationUrls: List<AcceleratedPresignedUrl> = emptyList()
)

/**
 * 업로드 가속 옵션에서 사용할 presigned URL 정보를 표현한다.
 */
data class AcceleratedPresignedUrl(
    val mode: UploadAccelerationMode,
    val url: String,
    val region: String? = null
)

/**
 * 업로드 가속 경로 유형을 구분한다.
 */
enum class UploadAccelerationMode {
    REGION,
    CDN
}

/**
 * 업로드 세션 생성 결과를 표현한다.
 */
data class UploadSessionCreationResult(
    val uploadId: String,
    val chunkSize: Long,
    val presignedUrls: List<PresignedPartUrl>,
    val expiresAt: Instant
)

/**
 * 업로드 완료 요청을 전달하기 위한 커맨드.
 */
data class CompleteUploadSessionCommand(
    val uploadId: String,
    val creatorId: String,
    val uploadedParts: List<CompletedPart>,
    val checksum: String
)

/**
 * 업로드 완료 요청에 포함되는 파트 정보를 표현한다.
 */
data class CompletedPart(
    val partNumber: Int,
    val etag: String
)

/**
 * 업로드 완료 처리 결과를 표현한다.
 */
data class UploadSessionCompletionResult(
    val assetId: Long,
    val status: AssetStatus
)
