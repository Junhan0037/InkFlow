package com.inkflow.indexing.application

import com.inkflow.indexing.domain.AssetIndexDocument
import com.inkflow.indexing.domain.EpisodeIndexDocument
import com.inkflow.indexing.domain.WorkIndexDocument
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 색인 검색 요청을 처리하는 애플리케이션 서비스.
 */
@Service
class IndexSearchApplicationService(
    private val indexSearchClient: IndexSearchClient
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Work 검색을 수행한다.
     */
    fun searchWorks(command: WorkSearchCommand): SearchResult<WorkIndexDocument> {
        logger.info(
            "Work 검색 요청. keyword={}, status={}, language={}, creatorId={}, page={}, size={}",
            command.keyword,
            command.status,
            command.language,
            command.creatorId,
            command.pageRequest.page,
            command.pageRequest.size
        )
        return indexSearchClient.searchWorks(command)
    }

    /**
     * Episode 검색을 수행한다.
     */
    fun searchEpisodes(command: EpisodeSearchCommand): SearchResult<EpisodeIndexDocument> {
        logger.info(
            "Episode 검색 요청. keyword={}, workId={}, page={}, size={}",
            command.keyword,
            command.workId,
            command.pageRequest.page,
            command.pageRequest.size
        )
        return indexSearchClient.searchEpisodes(command)
    }

    /**
     * Asset 검색을 수행한다.
     */
    fun searchAssets(command: AssetSearchCommand): SearchResult<AssetIndexDocument> {
        logger.info(
            "Asset 검색 요청. keyword={}, episodeId={}, status={}, contentType={}, page={}, size={}",
            command.keyword,
            command.episodeId,
            command.status,
            command.contentType,
            command.pageRequest.page,
            command.pageRequest.size
        )
        return indexSearchClient.searchAssets(command)
    }
}
