package com.inkflow.metadata.infra.jpa

import com.inkflow.metadata.domain.EpisodeMetadata
import com.inkflow.metadata.domain.EpisodeMetadataRepository
import com.inkflow.metadata.infra.MetadataTagCodec
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

/**
 * JPA 기반 승인 메타데이터 저장소 구현체.
 */
@Repository
class JpaEpisodeMetadataRepository(
    private val episodeMetadataJpaRepository: EpisodeMetadataJpaRepository,
    private val tagCodec: MetadataTagCodec
) : EpisodeMetadataRepository {
    /**
     * 승인된 메타데이터를 저장한다.
     */
    override fun save(metadata: EpisodeMetadata): EpisodeMetadata {
        val entity = EpisodeMetadataEntity.fromDomain(metadata, tagCodec)
        return episodeMetadataJpaRepository.save(entity).toDomain(tagCodec)
    }

    /**
     * 에피소드 ID로 승인된 메타데이터를 조회한다.
     */
    override fun findByEpisodeId(episodeId: Long): EpisodeMetadata? {
        return episodeMetadataJpaRepository.findByIdOrNull(episodeId)?.toDomain(tagCodec)
    }
}
