package com.inkflow.workflow.api

import com.inkflow.common.error.ApiResponse
import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.security.RequestContextFactory
import com.inkflow.common.security.RequestContextHeaders
import com.inkflow.workflow.application.ApproveEpisodeCommand
import com.inkflow.workflow.application.RejectEpisodeCommand
import com.inkflow.workflow.application.StartReviewCommand
import com.inkflow.workflow.application.SubmitEpisodeCommand
import com.inkflow.workflow.application.WorkflowApplicationService
import com.inkflow.workflow.application.WorkflowTransitionResult
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * 워크플로우 상태 전이 API.
 */
@RestController
@RequestMapping("/episodes")
class WorkflowController(
    private val workflowApplicationService: WorkflowApplicationService,
    private val requestContextFactory: RequestContextFactory
) {
    /**
     * 에피소드를 제출 상태로 전이한다.
     */
    @PostMapping("/{episodeId}/submit")
    fun submitEpisode(
        @PathVariable episodeId: Long,
        @RequestBody request: SubmitEpisodeRequest,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<WorkflowStateResponse>>> {
        val requestId = resolveRequestId(exchange)
        val submitterId = resolveUserId(exchange)
        val command = request.toCommand(episodeId, submitterId)

        return Mono.fromCallable { workflowApplicationService.submit(command) }
            // JDBC 호출은 blocking이므로 별도 스케줄러에서 수행한다.
            .subscribeOn(Schedulers.boundedElastic())
            .map { result -> toResponseEntity(requestId, result) }
    }

    /**
     * 에피소드 검수를 시작한다.
     */
    @PostMapping("/{episodeId}/review/start")
    fun startReview(
        @PathVariable episodeId: Long,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<WorkflowStateResponse>>> {
        val requestId = resolveRequestId(exchange)
        val reviewerId = resolveUserId(exchange)
        val command = StartReviewCommand(episodeId = episodeId, reviewerId = reviewerId)

        return Mono.fromCallable { workflowApplicationService.startReview(command) }
            // JDBC 호출은 blocking이므로 별도 스케줄러에서 수행한다.
            .subscribeOn(Schedulers.boundedElastic())
            .map { result -> toResponseEntity(requestId, result) }
    }

    /**
     * 에피소드를 승인한다.
     */
    @PostMapping("/{episodeId}/review/approve")
    fun approveEpisode(
        @PathVariable episodeId: Long,
        @RequestBody request: ApproveEpisodeRequest,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<WorkflowStateResponse>>> {
        val requestId = resolveRequestId(exchange)
        val reviewerId = resolveReviewerId(exchange, request.reviewerId)
        val command = request.toCommand(episodeId, reviewerId)

        return Mono.fromCallable { workflowApplicationService.approve(command) }
            // JDBC 호출은 blocking이므로 별도 스케줄러에서 수행한다.
            .subscribeOn(Schedulers.boundedElastic())
            .map { result -> toResponseEntity(requestId, result) }
    }

    /**
     * 에피소드를 반려한다.
     */
    @PostMapping("/{episodeId}/review/reject")
    fun rejectEpisode(
        @PathVariable episodeId: Long,
        @RequestBody request: RejectEpisodeRequest,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<WorkflowStateResponse>>> {
        val requestId = resolveRequestId(exchange)
        val reviewerId = resolveReviewerId(exchange, request.reviewerId)
        val command = request.toCommand(episodeId, reviewerId)

        return Mono.fromCallable { workflowApplicationService.reject(command) }
            // JDBC 호출은 blocking이므로 별도 스케줄러에서 수행한다.
            .subscribeOn(Schedulers.boundedElastic())
            .map { result -> toResponseEntity(requestId, result) }
    }

    /**
     * 요청 헤더에서 requestId를 추출하거나 생성한다.
     */
    private fun resolveRequestId(exchange: ServerWebExchange): String {
        val headers = exchange.request.headers.toSingleValueMap()
        return requestContextFactory.resolveRequestId(headers)
    }

    /**
     * 요청 헤더에서 사용자 식별자를 추출한다.
     */
    private fun resolveUserId(exchange: ServerWebExchange): String {
        return exchange.request.headers.getFirst(RequestContextHeaders.USER_ID)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(
                errorCode = ErrorCode.UNAUTHORIZED,
                message = "userId 헤더가 필요합니다."
            )
    }

    /**
     * 요청 본문 reviewerId와 인증 사용자 ID의 일치 여부를 검증한다.
     */
    private fun resolveReviewerId(exchange: ServerWebExchange, reviewerId: String?): String {
        val headerId = resolveUserId(exchange)
        val normalized = reviewerId?.trim()?.takeIf { it.isNotBlank() }
        if (normalized != null && normalized != headerId) {
            throw invalid("reviewerId", "reviewerId가 인증 사용자와 일치하지 않습니다.")
        }
        return normalized ?: headerId
    }

    /**
     * 제출 요청을 커맨드로 변환한다.
     */
    private fun SubmitEpisodeRequest.toCommand(
        episodeId: Long,
        submitterId: String
    ): SubmitEpisodeCommand {
        return SubmitEpisodeCommand(
            episodeId = episodeId,
            submitterId = submitterId,
            deadline = deadline
        )
    }

    /**
     * 승인 요청을 커맨드로 변환한다.
     */
    private fun ApproveEpisodeRequest.toCommand(
        episodeId: Long,
        reviewerId: String
    ): ApproveEpisodeCommand {
        return ApproveEpisodeCommand(
            episodeId = episodeId,
            reviewerId = reviewerId,
            comment = comment?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * 반려 요청을 커맨드로 변환한다.
     */
    private fun RejectEpisodeRequest.toCommand(
        episodeId: Long,
        reviewerId: String
    ): RejectEpisodeCommand {
        return RejectEpisodeCommand(
            episodeId = episodeId,
            reviewerId = reviewerId,
            reason = reason.trim()
        )
    }

    /**
     * 전이 결과를 응답 DTO로 변환한다.
     */
    private fun WorkflowTransitionResult.toResponse(): WorkflowStateResponse {
        return WorkflowStateResponse(
            episodeId = episodeId,
            state = state,
            version = version
        )
    }

    /**
     * 표준 응답 포맷을 구성한다.
     */
    private fun toResponseEntity(
        requestId: String,
        result: WorkflowTransitionResult
    ): ResponseEntity<ApiResponse<WorkflowStateResponse>> {
        val response = ApiResponse.success(requestId, result.toResponse())
        return ResponseEntity.ok()
            .header(RequestContextHeaders.REQUEST_ID, requestId)
            .body(response)
    }

    /**
     * 요청 오류를 표준 예외로 변환한다.
     */
    private fun invalid(field: String, message: String): BusinessException {
        return BusinessException(
            errorCode = ErrorCode.INVALID_REQUEST,
            details = mapOf("field" to field),
            message = message
        )
    }
}
