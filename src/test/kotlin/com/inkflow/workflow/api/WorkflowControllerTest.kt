package com.inkflow.workflow.api

import com.inkflow.common.error.CommonErrorHandler
import com.inkflow.common.security.RequestContextConfig
import com.inkflow.common.security.RequestContextHeaders
import com.inkflow.workflow.application.ApproveEpisodeCommand
import com.inkflow.workflow.application.RejectEpisodeCommand
import com.inkflow.workflow.application.StartReviewCommand
import com.inkflow.workflow.application.SubmitEpisodeCommand
import com.inkflow.workflow.application.WorkflowApplicationService
import com.inkflow.workflow.application.WorkflowTransitionResult
import com.inkflow.workflow.domain.WorkflowStatus
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
 * 워크플로우 컨트롤러의 요청/응답 매핑을 검증한다.
 */
@WebFluxTest(controllers = [WorkflowController::class])
@Import(RequestContextConfig::class, CommonErrorHandler::class, WorkflowControllerTestConfig::class)
class WorkflowControllerTest(
    @Autowired private val webTestClient: WebTestClient
) {
    // 컨트롤러가 사용하는 서비스 목을 주입받는다.
    @Autowired
    private lateinit var workflowApplicationService: WorkflowApplicationService

    /**
     * 에피소드 제출 요청이 정상적으로 응답되는지 확인한다.
     */
    @Test
    fun submitEpisode_returnsResponse() {
        val expectedCommand = SubmitEpisodeCommand(
            episodeId = 1L,
            submitterId = "creator-1",
            deadline = Instant.parse("2026-01-02T00:00:00Z")
        )
        val result = WorkflowTransitionResult(
            episodeId = 1L,
            state = WorkflowStatus.SUBMITTED,
            version = 2
        )
        Mockito.doReturn(result)
            .`when`(workflowApplicationService)
            .submit(expectedCommand)

        val request = SubmitEpisodeRequest(deadline = expectedCommand.deadline)

        webTestClient.post()
            .uri("/episodes/${expectedCommand.episodeId}/submit")
            .contentType(MediaType.APPLICATION_JSON)
            .header(RequestContextHeaders.USER_ID, "creator-1")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.code").isEqualTo("OK")
            .jsonPath("$.data.state").isEqualTo("SUBMITTED")
            .jsonPath("$.data.version").isEqualTo(2)

        Mockito.verify(workflowApplicationService).submit(expectedCommand)
        assertEquals("creator-1", expectedCommand.submitterId)
    }

    /**
     * 검수 시작 요청이 정상적으로 처리되는지 확인한다.
     */
    @Test
    fun startReview_returnsResponse() {
        val expectedCommand = StartReviewCommand(
            episodeId = 1L,
            reviewerId = "reviewer-1"
        )
        val result = WorkflowTransitionResult(
            episodeId = 1L,
            state = WorkflowStatus.REVIEWING,
            version = 3
        )
        Mockito.doReturn(result)
            .`when`(workflowApplicationService)
            .startReview(expectedCommand)

        webTestClient.post()
            .uri("/episodes/${expectedCommand.episodeId}/review/start")
            .header(RequestContextHeaders.USER_ID, "reviewer-1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.code").isEqualTo("OK")
            .jsonPath("$.data.state").isEqualTo("REVIEWING")

        Mockito.verify(workflowApplicationService).startReview(expectedCommand)
    }

    /**
     * 승인 요청에서 reviewerId가 없으면 헤더 값을 사용한다.
     */
    @Test
    fun approveEpisode_usesHeaderReviewerId() {
        val expectedCommand = ApproveEpisodeCommand(
            episodeId = 1L,
            reviewerId = "reviewer-1",
            comment = "승인합니다."
        )
        val result = WorkflowTransitionResult(
            episodeId = 1L,
            state = WorkflowStatus.APPROVED,
            version = 4
        )
        Mockito.doReturn(result)
            .`when`(workflowApplicationService)
            .approve(expectedCommand)

        val request = ApproveEpisodeRequest(comment = "승인합니다.")

        webTestClient.post()
            .uri("/episodes/${expectedCommand.episodeId}/review/approve")
            .contentType(MediaType.APPLICATION_JSON)
            .header(RequestContextHeaders.USER_ID, "reviewer-1")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.code").isEqualTo("OK")
            .jsonPath("$.data.state").isEqualTo("APPROVED")

        Mockito.verify(workflowApplicationService).approve(expectedCommand)
    }

    /**
     * reviewerId가 헤더와 다르면 INVALID_REQUEST가 반환된다.
     */
    @Test
    fun approveEpisode_rejectsReviewerIdMismatch() {
        val request = ApproveEpisodeRequest(
            reviewerId = "reviewer-2",
            comment = "승인합니다."
        )

        webTestClient.post()
            .uri("/episodes/1/review/approve")
            .contentType(MediaType.APPLICATION_JSON)
            .header(RequestContextHeaders.USER_ID, "reviewer-1")
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("INVALID_REQUEST")
    }

    /**
     * 반려 요청이 정상적으로 처리되는지 확인한다.
     */
    @Test
    fun rejectEpisode_returnsResponse() {
        val expectedCommand = RejectEpisodeCommand(
            episodeId = 1L,
            reviewerId = "reviewer-1",
            reason = "정책 위반"
        )
        val result = WorkflowTransitionResult(
            episodeId = 1L,
            state = WorkflowStatus.REJECTED,
            version = 4
        )
        Mockito.doReturn(result)
            .`when`(workflowApplicationService)
            .reject(expectedCommand)

        val request = RejectEpisodeRequest(
            reviewerId = null,
            reason = "정책 위반"
        )

        webTestClient.post()
            .uri("/episodes/${expectedCommand.episodeId}/review/reject")
            .contentType(MediaType.APPLICATION_JSON)
            .header(RequestContextHeaders.USER_ID, "reviewer-1")
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.code").isEqualTo("OK")
            .jsonPath("$.data.state").isEqualTo("REJECTED")

        Mockito.verify(workflowApplicationService).reject(expectedCommand)
    }

    /**
     * 사용자 헤더가 없으면 인증 오류를 반환한다.
     */
    @Test
    fun submitEpisode_withoutUserHeader_returnsUnauthorized() {
        val request = SubmitEpisodeRequest(deadline = Instant.parse("2026-01-02T00:00:00Z"))

        webTestClient.post()
            .uri("/episodes/1/submit")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
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
class WorkflowControllerTestConfig {
    /**
     * 워크플로우 애플리케이션 서비스 목을 제공한다.
     */
    @Bean
    fun workflowApplicationService(): WorkflowApplicationService {
        return Mockito.mock(WorkflowApplicationService::class.java)
    }
}
