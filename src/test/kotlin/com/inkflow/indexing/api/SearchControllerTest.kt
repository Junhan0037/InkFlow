package com.inkflow.indexing.api

import com.inkflow.common.error.CommonErrorHandler
import com.inkflow.common.security.RequestContextConfig
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
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

/**
 * SearchController의 요청/응답 매핑을 검증한다.
 */
@WebFluxTest(controllers = [SearchController::class])
@Import(RequestContextConfig::class, CommonErrorHandler::class, SearchControllerTestConfig::class)
class SearchControllerTest(
    @Autowired private val webTestClient: WebTestClient
) {
    @Autowired
    private lateinit var indexSearchApplicationService: IndexSearchApplicationService

    private val baseTime: Instant = Instant.parse("2024-01-01T00:00:00Z")

    /**
     * Work 검색 응답이 정상 포맷으로 반환되는지 확인한다.
     */
    @Test
    fun searchWorks_returnsResponse() {
        val command = WorkSearchCommand(
            keyword = "keyword",
            status = "PUBLISHED",
            language = "ko",
            creatorId = "creator-1",
            pageRequest = SearchPageRequest(page = 0, size = 20)
        )
        val result = SearchResult.of(
            items = listOf(
                WorkIndexDocument(
                    id = 1L,
                    title = "작품-1",
                    creatorId = "creator-1",
                    status = "PUBLISHED",
                    defaultLanguage = "ko",
                    createdAt = baseTime,
                    updatedAt = baseTime
                )
            ),
            total = 1,
            page = 0,
            size = 20
        )
        Mockito.doReturn(result)
            .`when`(indexSearchApplicationService)
            .searchWorks(command)

        webTestClient.get()
            .uri {
                it.path("/search/works")
                    .queryParam("keyword", "keyword")
                    .queryParam("status", "PUBLISHED")
                    .queryParam("language", "ko")
                    .queryParam("creatorId", "creator-1")
                    .queryParam("page", "0")
                    .queryParam("size", "20")
                    .build()
            }
            .header(RequestContextHeaders.REQUEST_ID, "req-1")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals(RequestContextHeaders.REQUEST_ID, "req-1")
            .expectBody()
            .jsonPath("$.code").isEqualTo("OK")
            .jsonPath("$.data.items[0].id").isEqualTo(1)
            .jsonPath("$.data.items[0].title").isEqualTo("작품-1")
            .jsonPath("$.data.total").isEqualTo(1)

        Mockito.verify(indexSearchApplicationService).searchWorks(command)
    }

    /**
     * Episode 검색 응답이 정상 포맷으로 반환되는지 확인한다.
     */
    @Test
    fun searchEpisodes_returnsResponse() {
        val command = EpisodeSearchCommand(
            keyword = "episode",
            workId = 10L,
            pageRequest = SearchPageRequest(page = 0, size = 10)
        )
        val result = SearchResult.of(
            items = listOf(
                EpisodeIndexDocument(
                    id = 2L,
                    workId = 10L,
                    title = "에피소드-1",
                    seq = 1,
                    publishedAt = baseTime,
                    createdAt = baseTime,
                    updatedAt = baseTime
                )
            ),
            total = 1,
            page = 0,
            size = 10
        )
        Mockito.doReturn(result)
            .`when`(indexSearchApplicationService)
            .searchEpisodes(command)

        webTestClient.get()
            .uri {
                it.path("/search/episodes")
                    .queryParam("keyword", "episode")
                    .queryParam("workId", "10")
                    .queryParam("page", "0")
                    .queryParam("size", "10")
                    .build()
            }
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.code").isEqualTo("OK")
            .jsonPath("$.data.items[0].id").isEqualTo(2)
            .jsonPath("$.data.items[0].workId").isEqualTo(10)

        Mockito.verify(indexSearchApplicationService).searchEpisodes(command)
    }

    /**
     * Asset 검색 응답이 정상 포맷으로 반환되는지 확인한다.
     */
    @Test
    fun searchAssets_returnsResponse() {
        val command = AssetSearchCommand(
            keyword = "asset",
            episodeId = 20L,
            status = "STORED",
            contentType = "image/png",
            pageRequest = SearchPageRequest(page = 1, size = 5)
        )
        val result = SearchResult.of(
            items = listOf(
                AssetIndexDocument(
                    id = 3L,
                    episodeId = 20L,
                    fileName = "asset.png",
                    contentType = "image/png",
                    size = 1024,
                    checksum = "checksum-1",
                    storageKey = "assets/asset.png",
                    status = "STORED",
                    creatorId = "creator-1",
                    uploadId = "upload-1",
                    storageBucket = "source-bucket",
                    createdAt = baseTime,
                    updatedAt = baseTime
                )
            ),
            total = 1,
            page = 1,
            size = 5
        )
        Mockito.doReturn(result)
            .`when`(indexSearchApplicationService)
            .searchAssets(command)

        webTestClient.get()
            .uri {
                it.path("/search/assets")
                    .queryParam("keyword", "asset")
                    .queryParam("episodeId", "20")
                    .queryParam("status", "STORED")
                    .queryParam("contentType", "image/png")
                    .queryParam("page", "1")
                    .queryParam("size", "5")
                    .build()
            }
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.code").isEqualTo("OK")
            .jsonPath("$.data.items[0].id").isEqualTo(3)
            .jsonPath("$.data.items[0].contentType").isEqualTo("image/png")

        Mockito.verify(indexSearchApplicationService).searchAssets(command)
    }

    /**
     * 잘못된 페이지 요청은 INVALID_REQUEST로 응답하는지 확인한다.
     */
    @Test
    fun searchWorks_returnsBadRequest_whenPageInvalid() {
        webTestClient.get()
            .uri { it.path("/search/works").queryParam("page", "-1").build() }
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("INVALID_REQUEST")
    }
}

/**
 * SearchController 테스트에 필요한 목 빈을 등록한다.
 */
@TestConfiguration
class SearchControllerTestConfig {
    /**
     * 검색 애플리케이션 서비스 목을 제공한다.
     */
    @Bean
    fun indexSearchApplicationService(): IndexSearchApplicationService {
        return Mockito.mock(IndexSearchApplicationService::class.java)
    }
}
