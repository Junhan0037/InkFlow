package com.inkflow.upload.api

import com.inkflow.common.error.CommonErrorHandler
import com.inkflow.common.security.RequestContextConfig
import com.inkflow.common.security.RequestContextHeaders
import com.inkflow.upload.application.AssetDownloadApplicationService
import com.inkflow.upload.application.AssetDownloadCommand
import com.inkflow.upload.application.AssetDownloadResult
import org.junit.jupiter.api.Assertions.assertEquals
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
 * Asset 다운로드 컨트롤러의 요청/응답 매핑을 검증한다.
 */
@WebFluxTest(controllers = [AssetDownloadController::class])
@Import(RequestContextConfig::class, CommonErrorHandler::class, AssetDownloadControllerTestConfig::class)
class AssetDownloadControllerTest(
    @Autowired private val webTestClient: WebTestClient
) {
    // 컨트롤러가 사용하는 서비스 목을 주입받는다.
    @Autowired
    private lateinit var assetDownloadApplicationService: AssetDownloadApplicationService

    /**
     * 다운로드 URL 요청이 정상적으로 응답되는지 확인한다.
     */
    @Test
    fun issueDownloadUrl_returnsResponse() {
        val result = AssetDownloadResult(
            assetId = 1L,
            fileName = "image.png",
            contentType = "image/png",
            size = 10L,
            url = "https://example.com/download",
            expiresAt = Instant.parse("2026-01-01T00:00:00Z")
        )
        val expectedCommand = AssetDownloadCommand(
            assetId = 1L,
            requesterId = "creator-1"
        )
        Mockito.doReturn(result)
            .`when`(assetDownloadApplicationService)
            .issueDownloadUrl(expectedCommand)

        webTestClient.get()
            .uri("/assets/1/download")
            .header(RequestContextHeaders.USER_ID, "creator-1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.code").isEqualTo("OK")
            .jsonPath("$.data.assetId").isEqualTo(1)
            .jsonPath("$.data.url").isEqualTo("https://example.com/download")

        Mockito.verify(assetDownloadApplicationService).issueDownloadUrl(expectedCommand)
        assertEquals(1L, expectedCommand.assetId)
        assertEquals("creator-1", expectedCommand.requesterId)
    }

    /**
     * 사용자 헤더가 없으면 인증 오류를 반환한다.
     */
    @Test
    fun issueDownloadUrl_withoutUserHeader_returnsUnauthorized() {
        webTestClient.get()
            .uri("/assets/1/download")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
    }
}

/**
 * WebFlux 슬라이스 테스트에서 사용할 목 빈을 등록한다.
 */
@TestConfiguration
class AssetDownloadControllerTestConfig {
    /**
     * 다운로드 애플리케이션 서비스 목을 제공한다.
     */
    @Bean
    fun assetDownloadApplicationService(): AssetDownloadApplicationService {
        return Mockito.mock(AssetDownloadApplicationService::class.java)
    }
}
