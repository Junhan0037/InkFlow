package com.inkflow.upload.api

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 업로드 세션 생성 요청 DTO.
 */
data class CreateUploadSessionRequest(
    val episodeId: Long,
    @JsonProperty("filename")
    val fileName: String,
    val contentType: String,
    val size: Long,
    val checksum: String,
    val totalParts: Int
)

/**
 * 업로드 완료 요청 DTO.
 */
data class CompleteUploadSessionRequest(
    val uploadedParts: List<UploadedPartRequest>,
    val checksum: String
)

/**
 * 업로드 완료 요청에 포함되는 파트 정보 DTO.
 */
data class UploadedPartRequest(
    val partNumber: Int,
    val etag: String
)
