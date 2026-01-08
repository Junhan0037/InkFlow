package com.inkflow.upload.api

import com.inkflow.common.error.ApiResponse
import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.security.RequestContextFactory
import com.inkflow.common.security.RequestContextHeaders
import com.inkflow.upload.application.CreateUploadSessionCommand
import com.inkflow.upload.application.UploadSessionApplicationService
import com.inkflow.upload.application.UploadSessionCreationResult
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * 업로드 세션 생성 API를 제공한다.
 */
@RestController
@RequestMapping("/uploads")
class UploadSessionController(
    private val uploadSessionApplicationService: UploadSessionApplicationService,
    private val requestContextFactory: RequestContextFactory
) {
    /**
     * 업로드 세션을 생성하고 presigned multipart URL을 반환한다.
     */
    @PostMapping
    fun createUploadSession(
        @RequestBody request: CreateUploadSessionRequest,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<CreateUploadSessionResponse>>> {
        val requestId = resolveRequestId(exchange)
        val creatorId = resolveCreatorId(exchange)
        val command = request.toCommand(creatorId)

        return Mono.fromCallable { uploadSessionApplicationService.createSession(command) }
            // JDBC/Redis 호출은 blocking이므로 별도 스케줄러에서 수행한다.
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
     * 요청 헤더에서 creatorId를 추출한다.
     */
    private fun resolveCreatorId(exchange: ServerWebExchange): String {
        return exchange.request.headers.getFirst(RequestContextHeaders.USER_ID)
            ?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(
                errorCode = ErrorCode.UNAUTHORIZED,
                message = "creatorId 헤더가 필요합니다."
            )
    }

    /**
     * 표준 응답 포맷을 구성한다.
     */
    private fun toResponseEntity(
        requestId: String,
        result: UploadSessionCreationResult
    ): ResponseEntity<ApiResponse<CreateUploadSessionResponse>> {
        val response = ApiResponse.success(requestId, result.toResponse())
        return ResponseEntity.ok()
            .header(RequestContextHeaders.REQUEST_ID, requestId)
            .body(response)
    }

    /**
     * API 요청을 커맨드로 변환한다.
     */
    private fun CreateUploadSessionRequest.toCommand(creatorId: String): CreateUploadSessionCommand {
        return CreateUploadSessionCommand(
            episodeId = episodeId,
            creatorId = creatorId,
            fileName = fileName,
            contentType = contentType,
            size = size,
            checksum = checksum,
            totalParts = totalParts
        )
    }

    /**
     * 응답 DTO로 변환한다.
     */
    private fun UploadSessionCreationResult.toResponse(): CreateUploadSessionResponse {
        return CreateUploadSessionResponse(
            uploadId = uploadId,
            chunkSize = chunkSize,
            presignedUrls = presignedUrls.map { PresignedPartUrlResponse(it.partNumber, it.url) },
            expiresAt = expiresAt
        )
    }
}
