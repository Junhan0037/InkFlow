package com.inkflow.upload.application

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
    val url: String
)

/**
 * 업로드 세션 생성 결과를 표현한다.
 */
data class UploadSessionCreationResult(
    val uploadId: String,
    val chunkSize: Long,
    val presignedUrls: List<PresignedPartUrl>,
    val expiresAt: Instant
)
