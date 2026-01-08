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
