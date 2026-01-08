package com.inkflow.upload.api

import java.time.Instant

/**
 * 다운로드 URL 발급 응답 DTO.
 */
data class AssetDownloadResponse(
    val assetId: Long,
    val fileName: String,
    val contentType: String,
    val size: Long,
    val url: String,
    val expiresAt: Instant
)
