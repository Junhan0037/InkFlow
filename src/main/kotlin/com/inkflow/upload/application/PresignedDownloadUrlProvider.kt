package com.inkflow.upload.application

import java.time.Instant

/**
 * 다운로드용 presigned URL 발급 계약.
 */
interface PresignedDownloadUrlProvider {
    /**
     * 단일 객체 다운로드용 presigned URL을 생성한다.
     */
    fun createPresignedDownload(
        bucket: String,
        key: String,
        contentType: String,
        expiresAt: Instant
    ): PresignedDownloadUrl
}

/**
 * 다운로드 presigned URL 정보를 표현한다.
 */
data class PresignedDownloadUrl(
    val url: String
)
