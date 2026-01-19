package com.inkflow.indexing.api

import com.inkflow.common.error.ApiResponse
import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.security.RequestContextFactory
import com.inkflow.common.security.RequestContextHeaders
import com.inkflow.indexing.application.AssetSearchCommand
import com.inkflow.indexing.application.EpisodeSearchCommand
import com.inkflow.indexing.application.IndexSearchApplicationService
import com.inkflow.indexing.application.SearchPageRequest
import com.inkflow.indexing.application.SearchResult
import com.inkflow.indexing.application.WorkSearchCommand
import com.inkflow.indexing.domain.AssetIndexDocument
import com.inkflow.indexing.domain.EpisodeIndexDocument
import com.inkflow.indexing.domain.WorkIndexDocument
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * 색인 검색 API.
 */
@RestController
@RequestMapping("/search")
class SearchController(
    private val indexSearchApplicationService: IndexSearchApplicationService,
    private val requestContextFactory: RequestContextFactory
) {
    /**
     * Work 검색 API.
     */
    @GetMapping("/works")
    fun searchWorks(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false, name = "language") language: String?,
        @RequestParam(required = false) creatorId: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<SearchPageResponse<WorkSearchResponse>>>> {
        val requestId = resolveRequestId(exchange)
        val command = buildWorkCommand(keyword, status, language, creatorId, page, size)

        return Mono.fromCallable { indexSearchApplicationService.searchWorks(command) }
            // Elasticsearch 호출은 blocking이므로 별도 스케줄러에서 수행한다.
            .subscribeOn(Schedulers.boundedElastic())
            .map { result -> toResponseEntity(requestId, result.toWorkResponse()) }
    }

    /**
     * Episode 검색 API.
     */
    @GetMapping("/episodes")
    fun searchEpisodes(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) workId: Long?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<SearchPageResponse<EpisodeSearchResponse>>>> {
        val requestId = resolveRequestId(exchange)
        val command = buildEpisodeCommand(keyword, workId, page, size)

        return Mono.fromCallable { indexSearchApplicationService.searchEpisodes(command) }
            // Elasticsearch 호출은 blocking이므로 별도 스케줄러에서 수행한다.
            .subscribeOn(Schedulers.boundedElastic())
            .map { result -> toResponseEntity(requestId, result.toEpisodeResponse()) }
    }

    /**
     * Asset 검색 API.
     */
    @GetMapping("/assets")
    fun searchAssets(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) episodeId: Long?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) contentType: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<SearchPageResponse<AssetSearchResponse>>>> {
        val requestId = resolveRequestId(exchange)
        val command = buildAssetCommand(keyword, episodeId, status, contentType, page, size)

        return Mono.fromCallable { indexSearchApplicationService.searchAssets(command) }
            // Elasticsearch 호출은 blocking이므로 별도 스케줄러에서 수행한다.
            .subscribeOn(Schedulers.boundedElastic())
            .map { result -> toResponseEntity(requestId, result.toAssetResponse()) }
    }

    /**
     * 요청 헤더에서 requestId를 추출하거나 생성한다.
     */
    private fun resolveRequestId(exchange: ServerWebExchange): String {
        val headers = exchange.request.headers.toSingleValueMap()
        return requestContextFactory.resolveRequestId(headers)
    }

    /**
     * Work 검색 명령을 생성한다.
     */
    private fun buildWorkCommand(
        keyword: String?,
        status: String?,
        language: String?,
        creatorId: String?,
        page: Int,
        size: Int
    ): WorkSearchCommand {
        return try {
            WorkSearchCommand(
                keyword = keyword?.trim(),
                status = status?.trim(),
                language = language?.trim(),
                creatorId = creatorId?.trim(),
                pageRequest = SearchPageRequest(page = page, size = size)
            )
        } catch (exception: IllegalArgumentException) {
            throw invalidRequest(exception)
        }
    }

    /**
     * Episode 검색 명령을 생성한다.
     */
    private fun buildEpisodeCommand(
        keyword: String?,
        workId: Long?,
        page: Int,
        size: Int
    ): EpisodeSearchCommand {
        return try {
            EpisodeSearchCommand(
                keyword = keyword?.trim(),
                workId = workId,
                pageRequest = SearchPageRequest(page = page, size = size)
            )
        } catch (exception: IllegalArgumentException) {
            throw invalidRequest(exception)
        }
    }

    /**
     * Asset 검색 명령을 생성한다.
     */
    private fun buildAssetCommand(
        keyword: String?,
        episodeId: Long?,
        status: String?,
        contentType: String?,
        page: Int,
        size: Int
    ): AssetSearchCommand {
        return try {
            AssetSearchCommand(
                keyword = keyword?.trim(),
                episodeId = episodeId,
                status = status?.trim(),
                contentType = contentType?.trim(),
                pageRequest = SearchPageRequest(page = page, size = size)
            )
        } catch (exception: IllegalArgumentException) {
            throw invalidRequest(exception)
        }
    }

    /**
     * Work 검색 결과를 응답 DTO로 변환한다.
     */
    private fun SearchResult<WorkIndexDocument>.toWorkResponse(): SearchPageResponse<WorkSearchResponse> {
        return SearchPageResponse(
            items = items.map { item ->
                WorkSearchResponse(
                    id = item.id,
                    title = item.title,
                    creatorId = item.creatorId,
                    status = item.status,
                    defaultLanguage = item.defaultLanguage,
                    createdAt = item.createdAt,
                    updatedAt = item.updatedAt
                )
            },
            total = total,
            page = page,
            size = size,
            totalPages = totalPages
        )
    }

    /**
     * Episode 검색 결과를 응답 DTO로 변환한다.
     */
    private fun SearchResult<EpisodeIndexDocument>.toEpisodeResponse(): SearchPageResponse<EpisodeSearchResponse> {
        return SearchPageResponse(
            items = items.map { item ->
                EpisodeSearchResponse(
                    id = item.id,
                    workId = item.workId,
                    title = item.title,
                    seq = item.seq,
                    publishedAt = item.publishedAt,
                    createdAt = item.createdAt,
                    updatedAt = item.updatedAt
                )
            },
            total = total,
            page = page,
            size = size,
            totalPages = totalPages
        )
    }

    /**
     * Asset 검색 결과를 응답 DTO로 변환한다.
     */
    private fun SearchResult<AssetIndexDocument>.toAssetResponse(): SearchPageResponse<AssetSearchResponse> {
        return SearchPageResponse(
            items = items.map { item ->
                AssetSearchResponse(
                    id = item.id,
                    episodeId = item.episodeId,
                    fileName = item.fileName,
                    contentType = item.contentType,
                    size = item.size,
                    checksum = item.checksum,
                    storageKey = item.storageKey,
                    status = item.status,
                    creatorId = item.creatorId,
                    uploadId = item.uploadId,
                    storageBucket = item.storageBucket,
                    createdAt = item.createdAt,
                    updatedAt = item.updatedAt
                )
            },
            total = total,
            page = page,
            size = size,
            totalPages = totalPages
        )
    }

    /**
     * 표준 응답 포맷을 구성한다.
     */
    private fun <T> toResponseEntity(
        requestId: String,
        response: SearchPageResponse<T>
    ): ResponseEntity<ApiResponse<SearchPageResponse<T>>> {
        val body = ApiResponse.success(requestId, response)
        return ResponseEntity.ok()
            .header(RequestContextHeaders.REQUEST_ID, requestId)
            .body(body)
    }

    /**
     * 잘못된 검색 요청 예외를 생성한다.
     */
    private fun invalidRequest(exception: IllegalArgumentException): BusinessException {
        return BusinessException(
            errorCode = ErrorCode.INVALID_REQUEST,
            message = exception.message ?: "검색 요청이 올바르지 않습니다."
        )
    }
}
