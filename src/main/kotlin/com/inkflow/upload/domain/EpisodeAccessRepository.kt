package com.inkflow.upload.domain

/**
 * 에피소드 소유자 정보를 조회해 업로드 권한을 판단한다.
 */
interface EpisodeAccessRepository {
    /**
     * 에피소드에 연결된 creatorId를 조회한다.
     */
    fun findCreatorIdByEpisodeId(episodeId: Long): String?
}
