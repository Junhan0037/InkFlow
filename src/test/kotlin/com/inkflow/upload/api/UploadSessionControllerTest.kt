package com.inkflow.upload.api

import com.inkflow.common.error.CommonErrorHandler
import com.inkflow.common.security.RequestContextConfig
import com.inkflow.common.security.RequestContextHeaders
import com.inkflow.upload.application.CompleteUploadSessionCommand
import com.inkflow.upload.application.CreateUploadSessionCommand
import com.inkflow.upload.application.IdempotencyService
import com.inkflow.upload.application.UploadSessionApplicationService
import com.inkflow.upload.application.UploadSessionCompletionResult
import com.inkflow.upload.application.UploadSessionCreationResult
import com.inkflow.upload.application.PresignedPartUrl
import com.inkflow.upload.domain.AssetStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

/**
 * 업로드 세션 컨트롤러의 요청/응답 매핑을 검증한다.
 */
@WebFluxTest(controllers = [UploadSessionController::class])
@Import(RequestContextConfig::class, CommonErrorHandler::class, UploadSessionControllerTestConfig::class)
class UploadSessionControllerTest(
    @Autowired private val webTestClient: WebTestClient
) {
    // 컨트롤러가 사용하는 서비스 목을 주입받는다.
    @Autowired
    private lateinit var uploadSessionApplicationService: UploadSessionApplicationService

    // 멱등성 서비스 목을 주입받는다.
    @Autowired
    private lateinit var idempotencyService: IdempotencyService

    /**
     * 업로드 세션 생성 요청이 정상적으로 응답되는지 확인한다.
     */
    @Test
    fun createUploadSession_returnsResponse() {
        val result = UploadSessionCreationResult(
            uploadId = "upl-1",
            chunkSize = 10L,
            presignedUrls = listOf(PresignedPartUrl(partNumber = 1, url = "https://example.com/1")),
            expiresAt = Instant.parse("2024-01-01T00:00:00Z")
        )
        val expectedCommand = CreateUploadSessionCommand(
            episodeId = 1L,
            creatorId = "creator-1",
            fileName = "image.png",
            contentType = "image/png",
            size = 10L,
            checksum = "checksum-1",
            totalParts = 1
        )
        Mockito.doReturn(result)
            .`when`(uploadSessionApplicationService)
            .createSession(expectedCommand)

        val request = CreateUploadSessionRequest(
            episodeId = expectedCommand.episodeId,
            fileName = expectedCommand.fileName,
            contentType = expectedCommand.contentType,
            size = expectedCommand.size,
            checksum = expectedCommand.checksum,
            totalParts = expectedCommand.totalParts
        )

        webTestClient.post()
            .uri("/uploads")
            .contentType(MediaType.APPLICATION_JSON)
            .header(RequestContextHeaders.USER_ID, "creator-1")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.code").isEqualTo("OK")
            .jsonPath("$.data.uploadId").isEqualTo("upl-1")
            .jsonPath("$.data.chunkSize").isEqualTo(10)

        Mockito.verify(uploadSessionApplicationService).createSession(expectedCommand)
        assertEquals(1L, expectedCommand.episodeId)
        assertEquals("creator-1", expectedCommand.creatorId)
        assertEquals("image.png", expectedCommand.fileName)
    }

    /**
     * 사용자 헤더가 없으면 인증 오류를 반환한다.
     */
    @Test
    fun createUploadSession_withoutUserHeader_returnsUnauthorized() {
        val request = CreateUploadSessionRequest(
            episodeId = 1L,
            fileName = "image.png",
            contentType = "image/png",
            size = 10L,
            checksum = "checksum-1",
            totalParts = 1
        )

        webTestClient.post()
            .uri("/uploads")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
    }

    /**
     * 업로드 완료 요청이 정상적으로 응답되는지 확인한다.
     */
    @Test
    fun completeUploadSession_returnsResponse() {
        val result = UploadSessionCompletionResult(
            assetId = 10L,
            status = AssetStatus.STORED
        )
        val expectedCommand = CompleteUploadSessionCommand(
            uploadId = "upl-1",
            creatorId = "creator-1",
            uploadedParts = listOf(
                com.inkflow.upload.application.CompletedPart(partNumber = 1, etag = "etag-1")
            ),
            checksum = "checksum-1"
        )
        Mockito.doReturn(result)
            .`when`(uploadSessionApplicationService)
            .completeSession(expectedCommand)

        val request = CompleteUploadSessionRequest(
            uploadedParts = listOf(
                UploadedPartRequest(partNumber = 1, etag = "etag-1")
            ),
            checksum = "checksum-1"
        )

        webTestClient.post()
            .uri("/uploads/${expectedCommand.uploadId}/complete")
            .contentType(MediaType.APPLICATION_JSON)
            .header(RequestContextHeaders.USER_ID, "creator-1")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.code").isEqualTo("OK")
            .jsonPath("$.data.assetId").isEqualTo(10)
            .jsonPath("$.data.status").isEqualTo("STORED")

        Mockito.verify(uploadSessionApplicationService).completeSession(expectedCommand)
        assertEquals("upl-1", expectedCommand.uploadId)
        assertEquals("creator-1", expectedCommand.creatorId)
        assertEquals(1, expectedCommand.uploadedParts.size)
    }
}

/**
 * WebFlux 슬라이스 테스트에서 사용할 목 빈을 등록한다.
 */
@TestConfiguration
class UploadSessionControllerTestConfig {
    /**
     * 업로드 세션 애플리케이션 서비스 목을 제공한다.
     */
    @Bean
    fun uploadSessionApplicationService(): UploadSessionApplicationService {
        return Mockito.mock(UploadSessionApplicationService::class.java)
    }

    /**
     * 멱등성 서비스 목을 제공한다.
     */
    @Bean
    fun idempotencyService(): IdempotencyService {
        return Mockito.mock(IdempotencyService::class.java)
    }
}
