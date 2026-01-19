package com.inkflow.indexing.application

import com.inkflow.indexing.domain.AssetIndexDocument
import com.inkflow.indexing.domain.EpisodeIndexDocument
import com.inkflow.indexing.domain.WorkIndexDocument

/**
 * 색인 검색을 수행하는 클라이언트 계약.
 */
interface IndexSearchClient {
    /**
     * Work 검색을 수행한다.
     */
    fun searchWorks(command: WorkSearchCommand): SearchResult<WorkIndexDocument>

    /**
     * Episode 검색을 수행한다.
     */
    fun searchEpisodes(command: EpisodeSearchCommand): SearchResult<EpisodeIndexDocument>

    /**
     * Asset 검색을 수행한다.
     */
    fun searchAssets(command: AssetSearchCommand): SearchResult<AssetIndexDocument>
}
