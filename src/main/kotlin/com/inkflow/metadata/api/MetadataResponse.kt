package com.inkflow.metadata.api

import com.inkflow.metadata.domain.MetadataSuggestionStatus
import java.time.Instant

/**
 * 승인된 메타데이터 응답 DTO.
 */
data class EpisodeMetadataResponse(
    val episodeId: Long,
    val summary: String,
    val tags: List<String>,
    val approvedBy: String,
    val approvedAt: Instant,
    val version: Int
)

/**
 * 메타 자동 생성 제안 응답 DTO.
 */
data class MetadataSuggestionResponse(
    val suggestionId: Long,
    val episodeId: Long,
    val summary: String,
    val tags: List<String>,
    val status: MetadataSuggestionStatus,
    val requestedBy: String,
    val reviewedBy: String?,
    val reviewedAt: Instant?,
    val rejectionReason: String?,
    val generator: String,
    val createdAt: Instant,
    val updatedAt: Instant
)
