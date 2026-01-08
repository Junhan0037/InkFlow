package com.inkflow.upload.application

import java.time.Instant

/**
 * 멀티파트 업로드용 presigned URL 발급 계약.
 */
interface MultipartPresignedUrlProvider {
    /**
     * 멀티파트 업로드용 presigned URL과 업로드 식별자를 생성한다.
     */
    fun createPresignedMultipart(
        bucket: String,
        key: String,
        totalParts: Int,
        expiresAt: Instant
    ): MultipartPresignedUpload
}

/**
 * 멀티파트 업로드 생성 결과를 표현한다.
 */
data class MultipartPresignedUpload(
    val multipartUploadId: String,
    val presignedUrls: List<PresignedPartUrl>
)
