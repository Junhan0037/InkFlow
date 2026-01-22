package com.inkflow.upload.api

import com.inkflow.upload.application.UploadAccelerationMode
import com.inkflow.upload.domain.AssetStatus
import java.time.Instant

/**
 * 업로드 세션 생성 응답 DTO.
 */
data class CreateUploadSessionResponse(
    val uploadId: String,
    val chunkSize: Long,
    val presignedUrls: List<PresignedPartUrlResponse>,
    val expiresAt: Instant
)

/**
 * presigned URL 응답 DTO.
 */
data class PresignedPartUrlResponse(
    val partNumber: Int,
    val url: String,
    val accelerationUrls: List<AccelerationPresignedUrlResponse> = emptyList()
)

/**
 * 업로드 가속 옵션용 presigned URL 응답 DTO.
 */
data class AccelerationPresignedUrlResponse(
    val mode: UploadAccelerationMode,
    val url: String,
    val region: String? = null
)

/**
 * 업로드 완료 응답 DTO.
 */
data class CompleteUploadSessionResponse(
    val assetId: Long,
    val status: AssetStatus
)
