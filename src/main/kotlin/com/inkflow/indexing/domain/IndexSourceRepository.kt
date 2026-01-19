package com.inkflow.indexing.domain

/**
 * 색인 대상 엔티티의 원천 데이터를 조회하는 저장소 계약.
 */
interface IndexSourceRepository {
    /**
     * Work 원천 데이터를 조회한다.
     */
    fun findWork(workId: Long): WorkIndexSource?

    /**
     * Episode 원천 데이터를 조회한다.
     */
    fun findEpisode(episodeId: Long): EpisodeIndexSource?

    /**
     * Asset 원천 데이터를 조회한다.
     */
    fun findAsset(assetId: Long): AssetIndexSource?
}
