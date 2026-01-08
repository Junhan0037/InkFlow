package com.inkflow.upload.api

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
    val url: String
)
