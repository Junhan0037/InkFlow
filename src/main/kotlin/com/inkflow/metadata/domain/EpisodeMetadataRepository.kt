package com.inkflow.metadata.domain

/**
 * 승인된 에피소드 메타데이터 저장소 계약.
 */
interface EpisodeMetadataRepository {
    /**
     * 승인된 메타데이터를 저장한다.
     */
    fun save(metadata: EpisodeMetadata): EpisodeMetadata

    /**
     * 에피소드 ID로 승인된 메타데이터를 조회한다.
     */
    fun findByEpisodeId(episodeId: Long): EpisodeMetadata?
}
