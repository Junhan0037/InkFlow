package com.inkflow.metadata.api

import com.inkflow.common.error.ApiResponse
import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.common.security.RequestContextFactory
import com.inkflow.common.security.RequestContextHeaders
import com.inkflow.metadata.application.ApproveMetadataSuggestionCommand
import com.inkflow.metadata.application.MetadataApplicationService
import com.inkflow.metadata.application.RejectMetadataSuggestionCommand
import com.inkflow.metadata.application.RequestMetadataGenerationCommand
import com.inkflow.metadata.domain.EpisodeMetadata
import com.inkflow.metadata.domain.EpisodeMetadataSuggestion
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * 메타 자동 생성 및 승인 플로우 API.
 */
@RestController
@RequestMapping("/episodes/{episodeId}/metadata")
class MetadataController(
    private val metadataApplicationService: MetadataApplicationService,
    private val requestContextFactory: RequestContextFactory
) {
    /**
     * 메타 자동 생성을 요청한다.
     */
    @PostMapping("/auto")
    fun requestGeneration(
        @PathVariable episodeId: Long,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<MetadataSuggestionResponse>>> {
        val requestId = resolveRequestId(exchange)
        val requesterId = resolveUserId(exchange)
        val command = RequestMetadataGenerationCommand(
            episodeId = episodeId,
            requesterId = requesterId
        )

        return Mono.fromCallable { metadataApplicationService.requestGeneration(command) }
            // JDBC 호출은 blocking이므로 별도 스케줄러에서 수행한다.
            .subscribeOn(Schedulers.boundedElastic())
            .map { suggestion -> toResponseEntity(requestId, suggestion.toResponse()) }
    }

    /**
     * 에피소드의 메타 자동 생성 제안을 조회한다.
     */
    @GetMapping("/auto")
    fun listSuggestions(
        @PathVariable episodeId: Long,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<List<MetadataSuggestionResponse>>>> {
        val requestId = resolveRequestId(exchange)

        return Mono.fromCallable { metadataApplicationService.getSuggestions(episodeId) }
            // JDBC 호출은 blocking이므로 별도 스케줄러에서 수행한다.
            .subscribeOn(Schedulers.boundedElastic())
            .map { suggestions ->
                val response = suggestions.map { it.toResponse() }
                toResponseEntity(requestId, response)
            }
    }

    /**
     * 메타 자동 생성 제안을 승인한다.
     */
    @PostMapping("/auto/{suggestionId}/approve")
    fun approveSuggestion(
        @PathVariable episodeId: Long,
        @PathVariable suggestionId: Long,
        @RequestBody request: ApproveMetadataSuggestionRequest,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<EpisodeMetadataResponse>>> {
        val requestId = resolveRequestId(exchange)
        val approverId = resolveUserId(exchange)
        val command = ApproveMetadataSuggestionCommand(
            episodeId = episodeId,
            suggestionId = suggestionId,
            approverId = approverId,
            overrideSummary = request.summary?.trim()?.takeIf { it.isNotBlank() },
            overrideTags = request.tags?.map { it.trim() }?.filter { it.isNotBlank() }
        )

        return Mono.fromCallable { metadataApplicationService.approveSuggestion(command) }
            // JDBC 호출은 blocking이므로 별도 스케줄러에서 수행한다.
            .subscribeOn(Schedulers.boundedElastic())
            .map { metadata -> toResponseEntity(requestId, metadata.toResponse()) }
    }

    /**
     * 메타 자동 생성 제안을 반려한다.
     */
    @PostMapping("/auto/{suggestionId}/reject")
    fun rejectSuggestion(
        @PathVariable episodeId: Long,
        @PathVariable suggestionId: Long,
        @RequestBody request: RejectMetadataSuggestionRequest,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<MetadataSuggestionResponse>>> {
        val requestId = resolveRequestId(exchange)
        val reviewerId = resolveUserId(exchange)
        val command = RejectMetadataSuggestionCommand(
            episodeId = episodeId,
            suggestionId = suggestionId,
            reviewerId = reviewerId,
            reason = request.reason.trim()
        )

        return Mono.fromCallable { metadataApplicationService.rejectSuggestion(command) }
            // JDBC 호출은 blocking이므로 별도 스케줄러에서 수행한다.
            .subscribeOn(Schedulers.boundedElastic())
            .map { suggestion -> toResponseEntity(requestId, suggestion.toResponse()) }
    }

    /**
     * 승인된 메타데이터를 조회한다.
     */
    @GetMapping
    fun getApprovedMetadata(
        @PathVariable episodeId: Long,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<EpisodeMetadataResponse>>> {
        val requestId = resolveRequestId(exchange)

        return Mono.fromCallable { metadataApplicationService.getApprovedMetadata(episodeId) }
            // JDBC 호출은 blocking이므로 별도 스케줄러에서 수행한다.
            .subscribeOn(Schedulers.boundedElastic())
            .map { metadata -> toResponseEntity(requestId, metadata.toResponse()) }
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
     * 승인 메타데이터를 응답 DTO로 변환한다.
     */
    private fun EpisodeMetadata.toResponse(): EpisodeMetadataResponse {
        return EpisodeMetadataResponse(
            episodeId = episodeId,
            summary = summary,
            tags = tags,
            approvedBy = approvedBy,
            approvedAt = approvedAt,
            version = version
        )
    }

    /**
     * 메타 제안을 응답 DTO로 변환한다.
     */
    private fun EpisodeMetadataSuggestion.toResponse(): MetadataSuggestionResponse {
        // 저장 후 응답에 필요한 식별자가 없으면 시스템 오류로 처리한다.
        val suggestionId = id ?: throw SystemException(
            errorCode = ErrorCode.INTERNAL_ERROR,
            message = "메타 제안 식별자를 확인할 수 없습니다."
        )
        return MetadataSuggestionResponse(
            suggestionId = suggestionId,
            episodeId = episodeId,
            summary = summary,
            tags = tags,
            status = status,
            requestedBy = requestedBy,
            reviewedBy = reviewedBy,
            reviewedAt = reviewedAt,
            rejectionReason = rejectionReason,
            generator = generator,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    /**
     * 표준 응답 포맷을 구성한다.
     */
    private fun <T> toResponseEntity(
        requestId: String,
        body: T
    ): ResponseEntity<ApiResponse<T>> {
        val response = ApiResponse.success(requestId, body)
        return ResponseEntity.ok()
            .header(RequestContextHeaders.REQUEST_ID, requestId)
            .body(response)
    }
}
