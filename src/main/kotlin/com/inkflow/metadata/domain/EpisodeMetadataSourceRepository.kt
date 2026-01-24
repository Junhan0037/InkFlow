package com.inkflow.metadata.domain

/**
 * 메타 자동 생성에 필요한 에피소드 원천 정보를 조회하는 저장소 계약.
 */
interface EpisodeMetadataSourceRepository {
    /**
     * 에피소드 ID로 메타 생성 원천 정보를 조회한다.
     */
    fun findSourceByEpisodeId(episodeId: Long): EpisodeMetadataSource?
}
