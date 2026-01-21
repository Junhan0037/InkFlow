package com.inkflow.workflow.api

import com.inkflow.common.error.ApiResponse
import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.security.RequestContextFactory
import com.inkflow.common.security.RequestContextHeaders
import com.inkflow.workflow.application.WorkflowAuditLogPage
import com.inkflow.workflow.application.WorkflowAuditLogQueryService
import com.inkflow.workflow.application.WorkflowAuditLogSearchCriteria
import com.inkflow.workflow.domain.WorkflowAuditLog
import com.inkflow.workflow.domain.WorkflowStatus
import com.inkflow.workflow.domain.WorkflowTransitionAction
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Instant

/**
 * 운영 콘솔에서 사용하는 워크플로우 감사 로그 조회 API.
 */
@RestController
@RequestMapping("/ops/workflows/audit-logs")
class WorkflowAuditLogController(
    private val workflowAuditLogQueryService: WorkflowAuditLogQueryService,
    private val requestContextFactory: RequestContextFactory
) {
    /**
     * 감사 로그 목록을 검색 조건에 따라 조회한다.
     */
    @GetMapping
    fun search(
        @RequestParam(required = false) episodeId: Long?,
        @RequestParam(required = false) actorId: String?,
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) fromState: String?,
        @RequestParam(required = false) toState: String?,
        @RequestParam(required = false) occurredFrom: String?,
        @RequestParam(required = false) occurredTo: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<WorkflowAuditLogPageResponse>>> {
        val requestId = resolveRequestId(exchange)
        val criteria = WorkflowAuditLogSearchCriteria(
            episodeId = episodeId,
            actorId = actorId?.trim(),
            action = parseAction(action),
            fromState = parseStatus(fromState, "fromState"),
            toState = parseStatus(toState, "toState"),
            occurredFrom = parseInstant(occurredFrom, "occurredFrom"),
            occurredTo = parseInstant(occurredTo, "occurredTo"),
            page = page,
            size = size
        )

        return Mono.fromCallable { workflowAuditLogQueryService.search(criteria) }
            // MongoDB 조회는 blocking이므로 별도 스케줄러에서 수행한다.
            .subscribeOn(Schedulers.boundedElastic())
            .map { result -> toResponseEntity(requestId, result.toResponse()) }
    }

    /**
     * 요청 헤더에서 requestId를 추출하거나 생성한다.
     */
    private fun resolveRequestId(exchange: ServerWebExchange): String {
        val headers = exchange.request.headers.toSingleValueMap()
        return requestContextFactory.resolveRequestId(headers)
    }

    /**
     * 액션 문자열을 enum으로 변환한다.
     */
    private fun parseAction(action: String?): WorkflowTransitionAction? {
        if (action.isNullOrBlank()) {
            return null
        }
        return try {
            WorkflowTransitionAction.valueOf(action.trim().uppercase())
        } catch (exception: IllegalArgumentException) {
            throw BusinessException(
                errorCode = ErrorCode.INVALID_REQUEST,
                message = "action 값이 올바르지 않습니다."
            )
        }
    }

    /**
     * 상태 문자열을 enum으로 변환한다.
     */
    private fun parseStatus(value: String?, fieldName: String): WorkflowStatus? {
        if (value.isNullOrBlank()) {
            return null
        }
        return try {
            WorkflowStatus.valueOf(value.trim().uppercase())
        } catch (exception: IllegalArgumentException) {
            throw BusinessException(
                errorCode = ErrorCode.INVALID_REQUEST,
                message = "${fieldName} 값이 올바르지 않습니다."
            )
        }
    }

    /**
     * ISO-8601 형식의 시간을 Instant로 변환한다.
     */
    private fun parseInstant(value: String?, fieldName: String): Instant? {
        if (value.isNullOrBlank()) {
            return null
        }
        return try {
            Instant.parse(value.trim())
        } catch (exception: Exception) {
            throw BusinessException(
                errorCode = ErrorCode.INVALID_REQUEST,
                message = "${fieldName} 형식이 올바르지 않습니다."
            )
        }
    }

    /**
     * 표준 응답 포맷을 구성한다.
     */
    private fun <T> toResponseEntity(
        requestId: String,
        response: T
    ): ResponseEntity<ApiResponse<T>> {
        val body = ApiResponse.success(requestId, response)
        return ResponseEntity.ok()
            .header(RequestContextHeaders.REQUEST_ID, requestId)
            .body(body)
    }

    /**
     * 감사 로그 페이지 응답으로 변환한다.
     */
    private fun WorkflowAuditLogPage.toResponse(): WorkflowAuditLogPageResponse {
        return WorkflowAuditLogPageResponse(
            items = items.map { it.toSummaryResponse() },
            total = total,
            page = page,
            size = size,
            totalPages = totalPages
        )
    }

    /**
     * 감사 로그를 요약 응답 DTO로 변환한다.
     */
    private fun WorkflowAuditLog.toSummaryResponse(): WorkflowAuditLogSummaryResponse {
        return WorkflowAuditLogSummaryResponse(
            id = id.orEmpty(),
            episodeId = episodeId,
            actorId = actorId,
            action = action.name,
            fromState = fromState.name,
            toState = toState.name,
            fromVersion = fromVersion,
            toVersion = toVersion,
            reason = reason,
            comment = comment,
            occurredAt = occurredAt
        )
    }
}
