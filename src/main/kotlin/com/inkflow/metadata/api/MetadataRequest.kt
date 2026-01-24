package com.inkflow.metadata.api

/**
 * 메타 제안 승인 요청 DTO.
 */
data class ApproveMetadataSuggestionRequest(
    val summary: String?,
    val tags: List<String>?
)

/**
 * 메타 제안 반려 요청 DTO.
 */
data class RejectMetadataSuggestionRequest(
    val reason: String
)
