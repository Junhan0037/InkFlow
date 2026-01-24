package com.inkflow.metadata.application

/**
 * 메타 자동 생성 요청 커맨드.
 */
data class RequestMetadataGenerationCommand(
    val episodeId: Long,
    val requesterId: String
)

/**
 * 메타 자동 생성 승인 커맨드.
 */
data class ApproveMetadataSuggestionCommand(
    val episodeId: Long,
    val suggestionId: Long,
    val approverId: String,
    val overrideSummary: String?,
    val overrideTags: List<String>?
)

/**
 * 메타 자동 생성 반려 커맨드.
 */
data class RejectMetadataSuggestionCommand(
    val episodeId: Long,
    val suggestionId: Long,
    val reviewerId: String,
    val reason: String
)
