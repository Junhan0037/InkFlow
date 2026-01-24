package com.inkflow.metadata.domain

import java.time.Instant

/**
 * 휴먼 승인 이후 확정된 에피소드 메타데이터.
 */
data class EpisodeMetadata(
    val episodeId: Long,
    val summary: String,
    val tags: List<String>,
    val approvedBy: String,
    val approvedAt: Instant,
    val version: Int,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(episodeId > 0) { "episodeId는 0보다 커야 합니다." }
        require(summary.isNotBlank()) { "summary는 비어 있을 수 없습니다." }
        require(tags.isNotEmpty()) { "tags는 1개 이상 필요합니다." }
        require(approvedBy.isNotBlank()) { "approvedBy는 비어 있을 수 없습니다." }
        require(version >= 0) { "version은 0 이상이어야 합니다." }
    }

    /**
     * 승인된 메타데이터를 갱신한다.
     */
    fun update(summary: String, tags: List<String>, approverId: String, now: Instant): EpisodeMetadata {
        val normalizedSummary = normalizeSummary(summary)
        val normalizedTags = normalizeTags(tags)
        return copy(
            summary = normalizedSummary,
            tags = normalizedTags,
            approvedBy = approverId,
            approvedAt = now,
            version = version + 1,
            updatedAt = now
        )
    }

    companion object {
        /**
         * 승인된 메타데이터를 신규로 생성한다.
         */
        fun create(
            episodeId: Long,
            summary: String,
            tags: List<String>,
            approvedBy: String,
            approvedAt: Instant,
            now: Instant
        ): EpisodeMetadata {
            val normalizedSummary = normalizeSummary(summary)
            val normalizedTags = normalizeTags(tags)
            return EpisodeMetadata(
                episodeId = episodeId,
                summary = normalizedSummary,
                tags = normalizedTags,
                approvedBy = approvedBy,
                approvedAt = approvedAt,
                version = 0,
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
