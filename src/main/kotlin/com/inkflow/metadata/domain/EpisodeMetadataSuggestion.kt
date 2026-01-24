package com.inkflow.metadata.domain

import java.time.Instant

/**
 * LLM 기반 메타 자동 생성 제안 데이터.
 */
data class EpisodeMetadataSuggestion(
    val id: Long? = null,
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
) {
    init {
        require(episodeId > 0) { "episodeId는 0보다 커야 합니다." }
        require(summary.isNotBlank()) { "summary는 비어 있을 수 없습니다." }
        require(tags.isNotEmpty()) { "tags는 1개 이상 필요합니다." }
        require(requestedBy.isNotBlank()) { "requestedBy는 비어 있을 수 없습니다." }
        require(generator.isNotBlank()) { "generator는 비어 있을 수 없습니다." }
    }

    /**
     * 제안을 승인 상태로 전이한다.
     */
    fun approve(reviewerId: String, now: Instant): EpisodeMetadataSuggestion {
        require(status == MetadataSuggestionStatus.PENDING) { "PENDING 상태에서만 승인할 수 있습니다." }
        return copy(
            status = MetadataSuggestionStatus.APPROVED,
            reviewedBy = reviewerId,
            reviewedAt = now,
            rejectionReason = null,
            updatedAt = now
        )
    }

    /**
     * 제안을 반려 상태로 전이한다.
     */
    fun reject(reviewerId: String, reason: String, now: Instant): EpisodeMetadataSuggestion {
        require(status == MetadataSuggestionStatus.PENDING) { "PENDING 상태에서만 반려할 수 있습니다." }
        require(reason.isNotBlank()) { "rejectionReason은 비어 있을 수 없습니다." }
        return copy(
            status = MetadataSuggestionStatus.REJECTED,
            reviewedBy = reviewerId,
            reviewedAt = now,
            rejectionReason = reason,
            updatedAt = now
        )
    }

    companion object {
        /**
         * 대기 상태의 자동 생성 제안을 만든다.
         */
        fun createPending(
            episodeId: Long,
            summary: String,
            tags: List<String>,
            requestedBy: String,
            generator: String,
            now: Instant
        ): EpisodeMetadataSuggestion {
            val normalizedSummary = normalizeSummary(summary)
            val normalizedTags = normalizeTags(tags)
            return EpisodeMetadataSuggestion(
                episodeId = episodeId,
                summary = normalizedSummary,
                tags = normalizedTags,
                status = MetadataSuggestionStatus.PENDING,
                requestedBy = requestedBy,
                reviewedBy = null,
                reviewedAt = null,
                rejectionReason = null,
                generator = generator,
                createdAt = now,
                updatedAt = now
            )
        }

        /**
         * 요약 문자열을 정규화한다.
         */
        private fun normalizeSummary(summary: String): String {
            return summary.trim()
        }

        /**
         * 태그 목록을 정규화한다.
         */
        private fun normalizeTags(tags: List<String>): List<String> {
            return MetadataTagNormalizer.normalize(tags)
        }
    }
}
