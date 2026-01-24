package com.inkflow.dlq.api

import com.inkflow.common.error.CommonErrorHandler
import com.inkflow.common.security.RequestContextConfig
import com.inkflow.common.security.RequestContextHeaders
import com.inkflow.dlq.application.DlqMessageApplicationService
import com.inkflow.dlq.application.DlqMessagePage
import com.inkflow.dlq.application.DlqSearchCriteria
import com.inkflow.dlq.domain.DlqMessage
import com.inkflow.dlq.domain.DlqMessageStatus
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
 * DLQ 운영 콘솔 API의 요청/응답 매핑을 검증.
 */
@WebFluxTest(controllers = [DlqController::class])
@Import(RequestContextConfig::class, CommonErrorHandler::class, DlqControllerTestConfig::class)
class DlqControllerTest(
    @Autowired private val webTestClient: WebTestClient
) {
    // DLQ 애플리케이션 서비스 목을 주입받는다.
    @Autowired
    private lateinit var dlqMessageApplicationService: DlqMessageApplicationService

    /**
     * DLQ 목록 조회 요청이 정상 응답으로 매핑되는지 확인한다.
     */
    @Test
    fun search_returnsPageResponse() {
        // 준비: DLQ 메시지 페이지 응답을 구성한다.
        val message = buildDlqMessage()
        val page = DlqMessagePage(
            items = listOf(message),
            total = 1,
            page = 0,
            size = 20,
            totalPages = 1
        )
        val expectedCriteria = DlqSearchCriteria(
            status = DlqMessageStatus.PENDING,
            originalTopic = "media.jobs",
            page = 0,
            size = 20
        )
        Mockito.doReturn(page)
            .`when`(dlqMessageApplicationService)
            .search(expectedCriteria)

        // 실행 및 검증: 응답 데이터가 정상 변환된다.
        webTestClient.get()
            .uri("/ops/dlq?status=PENDING&originalTopic=media.jobs")
            .header(RequestContextHeaders.REQUEST_ID, "req-1")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.code").isEqualTo("OK")
            .jsonPath("$.requestId").isEqualTo("req-1")
            .jsonPath("$.data.items[0].id").isEqualTo("dlq-1")
            .jsonPath("$.data.items[0].status").isEqualTo("PENDING")
            .jsonPath("$.data.items[0].originalTopic").isEqualTo("media.jobs")

        Mockito.verify(dlqMessageApplicationService).search(expectedCriteria)
        assertEquals(1, page.items.size)
    }

    /**
     * 운영자 식별자 헤더가 없으면 재처리 요청이 거부되는지 확인한다.
     */
    @Test
    fun reprocess_withoutOperatorHeader_returnsUnauthorized() {
        // 실행 및 검증: USER_ID 헤더가 없으면 401을 반환한다.
        webTestClient.post()
            .uri("/ops/dlq/dlq-1/reprocess")
            .contentType(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
    }

    /**
     * 테스트용 DLQ 메시지를 생성한다.
     */
    private fun buildDlqMessage(): DlqMessage {
        return DlqMessage(
            id = "dlq-1",
            sourceKey = "media.jobs:2:42",
            dlqTopic = "dlq.media.jobs",
            originalTopic = "media.jobs",
            originalPartition = 2,
            originalOffset = 42,
            originalTimestamp = Instant.parse("2026-01-01T00:00:00Z"),
            messageKey = "key-1",
            payload = "{\"eventId\":\"evt-1\"}",
            headers = mapOf("header" to "value"),
            eventId = "evt-1",
            eventType = "MEDIA_JOB_CREATED.v1",
            producer = "media-worker",
            traceId = "trace-1",
            idempotencyKey = "idem-1",
            occurredAt = Instant.parse("2026-01-01T00:00:00Z"),
            errorType = "java.lang.RuntimeException",
            errorMessage = "boom",
            errorStacktrace = "stack",
            status = DlqMessageStatus.PENDING,
            reprocessCount = 0,
            lastReprocessedAt = null,
            lastReprocessBy = null,
            lastReprocessReason = null,
            lastReprocessError = null,
            storedAt = Instant.parse("2026-01-01T00:00:00Z")
        )
    }
}

/**
 * WebFlux 슬라이스 테스트용 빈 구성을 제공한다.
 */
@TestConfiguration
class DlqControllerTestConfig {
    /**
     * DLQ 애플리케이션 서비스 목을 제공한다.
     */
    @Bean
    fun dlqMessageApplicationService(): DlqMessageApplicationService {
        return Mockito.mock(DlqMessageApplicationService::class.java)
    }
}
