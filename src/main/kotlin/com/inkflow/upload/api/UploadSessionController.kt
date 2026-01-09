package com.inkflow.upload.api

import com.inkflow.common.error.ApiResponse
import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.security.RequestContextFactory
import com.inkflow.common.security.RequestContextHeaders
import com.inkflow.upload.application.CompleteUploadSessionCommand
import com.inkflow.upload.application.CompletedPart
import com.inkflow.upload.application.CreateUploadSessionCommand
import com.inkflow.upload.application.IdempotencyService
import com.inkflow.upload.application.UploadSessionCompletionResult
import com.inkflow.upload.application.UploadSessionApplicationService
import com.inkflow.upload.application.UploadSessionCreationResult
import com.inkflow.upload.application.UploadIdempotencyKeys
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.PathVariable
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
    private val idempotencyService: IdempotencyService,
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
        val idempotencyKey = resolveIdempotencyKey(exchange)
        val command = request.toCommand(creatorId)

        val supplier = {
            uploadSessionApplicationService.createSession(command)
        }

        val operation = if (idempotencyKey.isNullOrBlank()) {
            Mono.fromCallable(supplier)
        } else {
            val key = UploadIdempotencyKeys.forCreateSession(creatorId, idempotencyKey)
            Mono.fromCallable {
                // 동일 요청 중복 처리를 방지하기 위해 Idempotency 키를 적용한다.
                idempotencyService.execute(
                    key = key,
                    resultClass = UploadSessionCreationResult::class.java,
                    actionName = "createUploadSession",
                    supplier = supplier
                )
            }
        }

        return operation
            // JDBC/Redis 호출은 blocking이므로 별도 스케줄러에서 수행한다.
            .subscribeOn(Schedulers.boundedElastic())
            .map { result -> toResponseEntity(requestId, result) }
    }

    /**
     * 업로드 완료를 처리하고 Asset 메타데이터를 생성한다.
     */
    @PostMapping("/{uploadId}/complete")
    fun completeUploadSession(
        @PathVariable uploadId: String,
        @RequestBody request: CompleteUploadSessionRequest,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<CompleteUploadSessionResponse>>> {
        val requestId = resolveRequestId(exchange)
        val creatorId = resolveCreatorId(exchange)
        val idempotencyKey = resolveIdempotencyKey(exchange)
        val command = request.toCommand(uploadId, creatorId)

        val supplier = {
            uploadSessionApplicationService.completeSession(command)
        }

        val operation = if (idempotencyKey.isNullOrBlank()) {
            Mono.fromCallable(supplier)
        } else {
            val key = UploadIdempotencyKeys.forCompleteSession(creatorId, uploadId, idempotencyKey)
            Mono.fromCallable {
                // 동일 요청 중복 처리를 방지하기 위해 Idempotency 키를 적용한다.
                idempotencyService.execute(
                    key = key,
                    resultClass = UploadSessionCompletionResult::class.java,
                    actionName = "completeUploadSession",
                    supplier = supplier
                )
            }
        }

        return operation
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
     * 요청 헤더에서 Idempotency 키를 추출한다.
     */
    private fun resolveIdempotencyKey(exchange: ServerWebExchange): String? {
        return exchange.request.headers.getFirst(RequestContextHeaders.IDEMPOTENCY_KEY)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
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

    /**
     * 업로드 완료 요청을 커맨드로 변환한다.
     */
    private fun CompleteUploadSessionRequest.toCommand(
        uploadId: String,
        creatorId: String
    ): CompleteUploadSessionCommand {
        return CompleteUploadSessionCommand(
            uploadId = uploadId,
            creatorId = creatorId,
            uploadedParts = uploadedParts.map {
                CompletedPart(
                    partNumber = it.partNumber,
                    etag = it.etag
                )
            },
            checksum = checksum
        )
    }

    /**
     * 업로드 완료 결과를 응답 DTO로 변환한다.
     */
    private fun UploadSessionCompletionResult.toResponse(): CompleteUploadSessionResponse {
        return CompleteUploadSessionResponse(
            assetId = assetId,
            status = status
        )
    }

    /**
     * 업로드 완료 응답을 표준 포맷으로 구성한다.
     */
    private fun toResponseEntity(
        requestId: String,
        result: UploadSessionCompletionResult
    ): ResponseEntity<ApiResponse<CompleteUploadSessionResponse>> {
        val response = ApiResponse.success(requestId, result.toResponse())
        return ResponseEntity.ok()
            .header(RequestContextHeaders.REQUEST_ID, requestId)
            .body(response)
    }
}
