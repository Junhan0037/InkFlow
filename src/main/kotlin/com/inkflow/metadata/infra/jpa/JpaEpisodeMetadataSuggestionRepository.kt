package com.inkflow.metadata.infra.jpa

import com.inkflow.metadata.domain.EpisodeMetadataSuggestion
import com.inkflow.metadata.domain.EpisodeMetadataSuggestionRepository
import com.inkflow.metadata.infra.MetadataTagCodec
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

/**
 * JPA 기반 메타 제안 저장소 구현체.
 */
@Repository
class JpaEpisodeMetadataSuggestionRepository(
    private val suggestionJpaRepository: EpisodeMetadataSuggestionJpaRepository,
    private val tagCodec: MetadataTagCodec
) : EpisodeMetadataSuggestionRepository {
    /**
     * 자동 생성 제안을 저장한다.
     */
    override fun save(suggestion: EpisodeMetadataSuggestion): EpisodeMetadataSuggestion {
        val entity = EpisodeMetadataSuggestionEntity.fromDomain(suggestion, tagCodec)
        return suggestionJpaRepository.save(entity).toDomain(tagCodec)
    }

    /**
     * 제안 식별자로 메타 제안을 조회한다.
     */
    override fun findById(suggestionId: Long): EpisodeMetadataSuggestion? {
        return suggestionJpaRepository.findByIdOrNull(suggestionId)?.toDomain(tagCodec)
    }

    /**
     * 에피소드별 모든 제안을 조회한다.
     */
    override fun findByEpisodeId(episodeId: Long): List<EpisodeMetadataSuggestion> {
        return suggestionJpaRepository.findByEpisodeIdOrderByCreatedAtDesc(episodeId)
            .map { it.toDomain(tagCodec) }
    }
}
