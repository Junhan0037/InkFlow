package com.inkflow.dlq.api

import com.inkflow.common.error.ApiResponse
import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.security.RequestContextFactory
import com.inkflow.common.security.RequestContextHeaders
import com.inkflow.dlq.application.DlqMessageApplicationService
import com.inkflow.dlq.application.DlqMessagePage
import com.inkflow.dlq.application.DlqReprocessCommand
import com.inkflow.dlq.application.DlqReprocessResult
import com.inkflow.dlq.application.DlqSearchCriteria
import com.inkflow.dlq.domain.DlqMessage
import com.inkflow.dlq.domain.DlqMessageStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * DLQ 메시지 조회 및 재처리 API.
 */
@RestController
@RequestMapping("/ops/dlq")
class DlqController(
    private val dlqMessageApplicationService: DlqMessageApplicationService,
    private val requestContextFactory: RequestContextFactory
) {
    /**
     * DLQ 메시지 목록을 조회한다.
     */
    @GetMapping
    fun search(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) originalTopic: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<DlqMessagePageResponse>>> {
        val requestId = resolveRequestId(exchange)
        val criteria = DlqSearchCriteria(
            status = parseStatus(status),
            originalTopic = originalTopic,
            page = page,
            size = size
        )

        return Mono.fromCallable { dlqMessageApplicationService.search(criteria) }
            // MongoDB 조회는 blocking이므로 별도 스케줄러에서 수행한다.
            .subscribeOn(Schedulers.boundedElastic())
            .map { result -> toResponseEntity(requestId, result.toResponse()) }
    }

    /**
     * DLQ 메시지 상세를 조회한다.
     */
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<DlqMessageDetailResponse>>> {
        val requestId = resolveRequestId(exchange)
        return Mono.fromCallable { dlqMessageApplicationService.get(id) }
            // MongoDB 조회는 blocking이므로 별도 스케줄러에서 수행한다.
            .subscribeOn(Schedulers.boundedElastic())
            .map { message -> toResponseEntity(requestId, message.toDetailResponse()) }
    }

    /**
     * DLQ 메시지를 재처리한다.
     */
    @PostMapping("/{id}/reprocess")
    fun reprocess(
        @PathVariable id: String,
        @RequestBody(required = false) request: DlqReprocessRequest?,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<DlqReprocessResponse>>> {
        val requestId = resolveRequestId(exchange)
        val operatorId = resolveOperatorId(exchange)
        val command = DlqReprocessCommand(
            messageId = id,
            requestedBy = operatorId,
            reason = request?.reason
        )

        return Mono.fromCallable { dlqMessageApplicationService.reprocess(command) }
            // Kafka 재발행은 blocking I/O이므로 별도 스케줄러에서 수행한다.
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
     * 요청 헤더에서 운영자 ID를 추출한다.
     */
    private fun resolveOperatorId(exchange: ServerWebExchange): String {
        return exchange.request.headers.getFirst(RequestContextHeaders.USER_ID)
            ?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(
                errorCode = ErrorCode.UNAUTHORIZED,
                message = "운영자 식별자 헤더가 필요합니다."
            )
    }

    /**
     * DLQ 상태 문자열을 enum으로 변환한다.
     */
    private fun parseStatus(status: String?): DlqMessageStatus? {
        if (status.isNullOrBlank()) {
            return null
        }
        return try {
            DlqMessageStatus.valueOf(status.trim().uppercase())
        } catch (exception: IllegalArgumentException) {
            throw BusinessException(
                errorCode = ErrorCode.INVALID_REQUEST,
                message = "status 값이 올바르지 않습니다."
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
     * DLQ 메시지 페이지 응답으로 변환한다.
     */
    private fun DlqMessagePage.toResponse(): DlqMessagePageResponse {
        return DlqMessagePageResponse(
            items = items.map { it.toSummaryResponse() },
            total = total,
            page = page,
            size = size,
            totalPages = totalPages
        )
    }

    /**
     * DLQ 메시지 요약 응답으로 변환한다.
     */
    private fun DlqMessage.toSummaryResponse(): DlqMessageSummaryResponse {
        return DlqMessageSummaryResponse(
            id = id.orEmpty(),
            status = status.name,
            dlqTopic = dlqTopic,
            originalTopic = originalTopic,
            eventType = eventType,
            eventId = eventId,
            traceId = traceId,
            idempotencyKey = idempotencyKey,
            errorType = errorType,
            errorMessage = errorMessage,
            reprocessCount = reprocessCount,
            lastReprocessedAt = lastReprocessedAt,
            storedAt = storedAt
        )
    }

    /**
     * DLQ 메시지 상세 응답으로 변환한다.
     */
    private fun DlqMessage.toDetailResponse(): DlqMessageDetailResponse {
        return DlqMessageDetailResponse(
            id = id.orEmpty(),
            status = status.name,
            dlqTopic = dlqTopic,
            originalTopic = originalTopic,
            originalPartition = originalPartition,
            originalOffset = originalOffset,
            originalTimestamp = originalTimestamp,
            messageKey = messageKey,
            payload = payload,
            headers = headers,
            eventType = eventType,
            eventId = eventId,
            producer = producer,
            traceId = traceId,
            idempotencyKey = idempotencyKey,
            occurredAt = occurredAt,
            errorType = errorType,
            errorMessage = errorMessage,
            errorStacktrace = errorStacktrace,
            reprocessCount = reprocessCount,
            lastReprocessedAt = lastReprocessedAt,
            lastReprocessBy = lastReprocessBy,
            lastReprocessReason = lastReprocessReason,
            lastReprocessError = lastReprocessError,
            storedAt = storedAt
        )
    }

    /**
     * DLQ 재처리 결과 응답으로 변환한다.
     */
    private fun DlqReprocessResult.toResponse(): DlqReprocessResponse {
        return DlqReprocessResponse(
            messageId = messageId,
            status = status.name,
            reprocessCount = reprocessCount,
            reprocessedAt = reprocessedAt,
            errorMessage = errorMessage
        )
    }
}
