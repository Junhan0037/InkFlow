package com.inkflow.metadata.domain

/**
 * 메타 자동 생성 제안을 저장/조회하는 저장소 계약.
 */
interface EpisodeMetadataSuggestionRepository {
    /**
     * 자동 생성 제안을 저장한다.
     */
    fun save(suggestion: EpisodeMetadataSuggestion): EpisodeMetadataSuggestion

    /**
     * 제안 식별자로 메타 제안을 조회한다.
     */
    fun findById(suggestionId: Long): EpisodeMetadataSuggestion?

    /**
     * 에피소드에 대한 모든 제안을 조회한다.
     */
    fun findByEpisodeId(episodeId: Long): List<EpisodeMetadataSuggestion>
}
