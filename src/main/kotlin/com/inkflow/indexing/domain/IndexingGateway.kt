package com.inkflow.indexing.domain

/**
 * 외부 검색 인덱스에 대한 갱신 인터페이스.
 */
interface IndexingGateway {
    /**
     * Work 문서를 UPSERT한다.
     */
    fun upsertWork(document: WorkIndexDocument)

    /**
     * Episode 문서를 UPSERT한다.
     */
    fun upsertEpisode(document: EpisodeIndexDocument)

    /**
     * Asset 문서를 UPSERT한다.
     */
    fun upsertAsset(document: AssetIndexDocument)

    /**
     * Work 문서를 삭제한다.
     */
    fun deleteWork(workId: Long)

    /**
     * Episode 문서를 삭제한다.
     */
    fun deleteEpisode(episodeId: Long)

    /**
     * Asset 문서를 삭제한다.
     */
    fun deleteAsset(assetId: Long)
}
