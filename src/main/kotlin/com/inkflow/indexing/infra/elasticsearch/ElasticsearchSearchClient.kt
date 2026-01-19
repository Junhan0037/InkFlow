package com.inkflow.indexing.infra.elasticsearch

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.indexing.application.AssetSearchCommand
import com.inkflow.indexing.application.EpisodeSearchCommand
import com.inkflow.indexing.application.IndexSearchClient
import com.inkflow.indexing.application.IndexingProperties
import com.inkflow.indexing.application.SearchResult
import com.inkflow.indexing.application.WorkSearchCommand
import com.inkflow.indexing.domain.AssetIndexDocument
import com.inkflow.indexing.domain.EpisodeIndexDocument
import com.inkflow.indexing.domain.WorkIndexDocument
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

/**
 * Elasticsearch 검색 API를 사용하는 검색 클라이언트 구현체.
 */
@Component
class ElasticsearchSearchClient(
    webClientBuilder: WebClient.Builder,
    private val objectMapper: ObjectMapper,
    private val properties: IndexingProperties
) : IndexSearchClient {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val webClient: WebClient = webClientBuilder.baseUrl(properties.baseUrl).build()

    /**
     * Work 검색을 수행한다.
     */
    override fun searchWorks(command: WorkSearchCommand): SearchResult<WorkIndexDocument> {
        val body = buildWorkQuery(command)
        return executeSearch(
            indexName = properties.indices.works,
            body = body,
            documentClass = WorkIndexDocument::class.java,
            page = command.pageRequest.page,
            size = command.pageRequest.size
        )
    }

    /**
     * Episode 검색을 수행한다.
     */
    override fun searchEpisodes(command: EpisodeSearchCommand): SearchResult<EpisodeIndexDocument> {
        val body = buildEpisodeQuery(command)
        return executeSearch(
            indexName = properties.indices.episodes,
            body = body,
            documentClass = EpisodeIndexDocument::class.java,
            page = command.pageRequest.page,
            size = command.pageRequest.size
        )
    }

    /**
     * Asset 검색을 수행한다.
     */
    override fun searchAssets(command: AssetSearchCommand): SearchResult<AssetIndexDocument> {
        val body = buildAssetQuery(command)
        return executeSearch(
            indexName = properties.indices.assets,
            body = body,
            documentClass = AssetIndexDocument::class.java,
            page = command.pageRequest.page,
            size = command.pageRequest.size
        )
    }

    /**
     * Elasticsearch 검색 요청을 실행한다.
     */
    private fun <T : Any> executeSearch(
        indexName: String,
        body: Map<String, Any>,
        documentClass: Class<T>,
        page: Int,
        size: Int
    ): SearchResult<T> {
        return try {
            val responseBody = webClient.post()
                .uri("/{index}/_search", indexName)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus({ status -> status.isError }, { response ->
                    response.bodyToMono(String::class.java)
                        .defaultIfEmpty("")
                        .flatMap { bodyText ->
                            Mono.error(buildDependencyFailure(indexName, bodyText))
                        }
                })
                .bodyToMono(String::class.java)
                .block()
                ?: throw SystemException(
                    errorCode = ErrorCode.DEPENDENCY_FAILURE,
                    details = mapOf("index" to indexName),
                    message = "Elasticsearch 검색 응답이 비어 있습니다."
                )

            parseSearchResult(responseBody, documentClass, page, size)
        } catch (exception: SystemException) {
            // 이미 래핑된 시스템 예외는 그대로 전파한다.
            throw exception
        } catch (exception: Exception) {
            logger.error("Elasticsearch 검색 요청에 실패했습니다. index={}", indexName, exception)
            throw SystemException(
                errorCode = ErrorCode.DEPENDENCY_FAILURE,
                details = mapOf("index" to indexName),
                message = "Elasticsearch 검색 요청에 실패했습니다.",
                cause = exception
            )
        }
    }

    /**
     * Work 검색 쿼리를 구성한다.
     */
    private fun buildWorkQuery(command: WorkSearchCommand): Map<String, Any> {
        val filters = mutableListOf<Map<String, Any>>()
        command.status?.let { filters.add(termFilter("status", it)) }
        command.language?.let { filters.add(termFilter("defaultLanguage", it)) }
        command.creatorId?.let { filters.add(termFilter("creatorId", it)) }
        val keywordQuery = command.keyword?.takeIf { it.isNotBlank() }
            ?.let { keywordQuery(it, listOf("title^2", "title")) }

        return buildSearchBody(command.pageRequest, filters, keywordQuery)
    }

    /**
     * Episode 검색 쿼리를 구성한다.
     */
    private fun buildEpisodeQuery(command: EpisodeSearchCommand): Map<String, Any> {
        val filters = mutableListOf<Map<String, Any>>()
        command.workId?.let { filters.add(termFilter("workId", it)) }
        val keywordQuery = command.keyword?.takeIf { it.isNotBlank() }
            ?.let { keywordQuery(it, listOf("title^2", "title")) }

        return buildSearchBody(command.pageRequest, filters, keywordQuery)
    }

    /**
     * Asset 검색 쿼리를 구성한다.
     */
    private fun buildAssetQuery(command: AssetSearchCommand): Map<String, Any> {
        val filters = mutableListOf<Map<String, Any>>()
        command.episodeId?.let { filters.add(termFilter("episodeId", it)) }
        command.status?.let { filters.add(termFilter("status", it)) }
        command.contentType?.let { filters.add(termFilter("contentType", it)) }
        val keywordQuery = command.keyword?.takeIf { it.isNotBlank() }
            ?.let { keywordQuery(it, listOf("fileName^2", "fileName")) }

        return buildSearchBody(command.pageRequest, filters, keywordQuery)
    }

    /**
     * 기본 검색 바디를 구성한다.
     */
    private fun buildSearchBody(
        pageRequest: com.inkflow.indexing.application.SearchPageRequest,
        filters: List<Map<String, Any>>,
        keywordQuery: Map<String, Any>?
    ): Map<String, Any> {
        val boolQuery = mutableMapOf<String, Any>()
        if (keywordQuery != null) {
            boolQuery["must"] = listOf(keywordQuery)
        }
        if (filters.isNotEmpty()) {
            boolQuery["filter"] = filters
        }
        val query = if (boolQuery.isEmpty()) {
            mapOf("match_all" to emptyMap<String, Any>())
        } else {
            mapOf("bool" to boolQuery)
        }

        return mapOf(
            "from" to pageRequest.offset(),
            "size" to pageRequest.size,
            "track_total_hits" to true,
            "query" to query,
            "sort" to listOf(mapOf("updatedAt" to mapOf("order" to "desc")))
        )
    }

    /**
     * keyword 검색 조건을 구성한다.
     */
    private fun keywordQuery(keyword: String, fields: List<String>): Map<String, Any> {
        return mapOf(
            "multi_match" to mapOf(
                "query" to keyword,
                "fields" to fields,
                "operator" to "and"
            )
        )
    }

    /**
     * term 필터 조건을 구성한다.
     */
    private fun termFilter(field: String, value: Any): Map<String, Any> {
        return mapOf("term" to mapOf(field to value))
    }

    /**
     * Elasticsearch 검색 응답을 파싱해 SearchResult로 변환한다.
     */
    private fun <T : Any> parseSearchResult(
        responseBody: String,
        documentClass: Class<T>,
        page: Int,
        size: Int
    ): SearchResult<T> {
        val javaType = objectMapper.typeFactory
            .constructParametricType(ElasticsearchSearchResponse::class.java, documentClass)
        val response = objectMapper.readValue(responseBody, javaType) as ElasticsearchSearchResponse<T>
        val items = response.hits.hits.map { it.source }
        return SearchResult.of(
            items = items,
            total = response.hits.total.value,
            page = page,
            size = size
        )
    }

    /**
     * Elasticsearch 오류 응답을 시스템 예외로 변환한다.
     */
    private fun buildDependencyFailure(indexName: String, responseBody: String): SystemException {
        return SystemException(
            errorCode = ErrorCode.DEPENDENCY_FAILURE,
            details = mapOf("index" to indexName, "response" to responseBody),
            message = "Elasticsearch 검색 응답 오류가 발생했습니다."
        )
    }

    /**
     * Elasticsearch 검색 응답 파싱을 위한 내부 모델.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class ElasticsearchSearchResponse<T>(
        val hits: ElasticsearchHits<T>
    )

    /**
     * Elasticsearch hits 정보를 담는 모델.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class ElasticsearchHits<T>(
        val total: ElasticsearchTotal,
        val hits: List<ElasticsearchHit<T>>
    )

    /**
     * Elasticsearch total 정보를 담는 모델.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class ElasticsearchTotal(
        val value: Long
    )

    /**
     * Elasticsearch hit 정보를 담는 모델.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class ElasticsearchHit<T>(
        @JsonProperty("_source")
        val source: T
    ) {
        // _source를 명시적으로 매핑한다.
    }
}
