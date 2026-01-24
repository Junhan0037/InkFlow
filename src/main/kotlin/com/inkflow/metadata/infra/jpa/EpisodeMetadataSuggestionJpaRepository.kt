package com.inkflow.metadata.infra.jpa

import org.springframework.data.jpa.repository.JpaRepository

/**
 * 메타 자동 생성 제안 JPA Repository.
 */
interface EpisodeMetadataSuggestionJpaRepository : JpaRepository<EpisodeMetadataSuggestionEntity, Long> {
    /**
     * 에피소드별 제안을 최신순으로 조회한다.
     */
    fun findByEpisodeIdOrderByCreatedAtDesc(episodeId: Long): List<EpisodeMetadataSuggestionEntity>
}
