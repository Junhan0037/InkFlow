package com.inkflow.upload.application

import java.time.Instant

/**
 * 다운로드 URL 발급 요청을 전달하기 위한 커맨드.
 */
data class AssetDownloadCommand(
    val assetId: Long,
    val requesterId: String
)

/**
 * 다운로드 URL 발급 결과를 표현한다.
 */
data class AssetDownloadResult(
    val assetId: Long,
    val fileName: String,
    val contentType: String,
    val size: Long,
    val url: String,
    val expiresAt: Instant
)
