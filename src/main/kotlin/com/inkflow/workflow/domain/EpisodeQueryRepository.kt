package com.inkflow.workflow.domain

/**
 * 에피소드 메타 조회를 위한 조회 전용 저장소 계약.
 */
interface EpisodeQueryRepository {
    /**
     * 에피소드에 연결된 workId를 조회한다.
     */
    fun findWorkIdByEpisodeId(episodeId: Long): Long?
}
