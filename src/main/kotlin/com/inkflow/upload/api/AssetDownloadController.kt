package com.inkflow.upload.api

import com.inkflow.common.error.ApiResponse
import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.security.RequestContextFactory
import com.inkflow.common.security.RequestContextHeaders
import com.inkflow.upload.application.AssetDownloadApplicationService
import com.inkflow.upload.application.AssetDownloadCommand
import com.inkflow.upload.application.AssetDownloadResult
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Asset 다운로드 URL 발급 API.
 */
@RestController
@RequestMapping("/assets")
class AssetDownloadController(
    private val assetDownloadApplicationService: AssetDownloadApplicationService,
    private val requestContextFactory: RequestContextFactory
) {
    /**
     * 다운로드 권한 확인 후 presigned URL을 발급한다.
     */
    @GetMapping("/{assetId}/download")
    fun issueDownloadUrl(
        @PathVariable assetId: Long,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<AssetDownloadResponse>>> {
        val requestId = resolveRequestId(exchange)
        val requesterId = resolveRequesterId(exchange)
        val command = AssetDownloadCommand(assetId = assetId, requesterId = requesterId)

        return Mono.fromCallable { assetDownloadApplicationService.issueDownloadUrl(command) }
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
     * 요청 헤더에서 requesterId를 추출한다.
     */
    private fun resolveRequesterId(exchange: ServerWebExchange): String {
        return exchange.request.headers.getFirst(RequestContextHeaders.USER_ID)
            ?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(
                errorCode = ErrorCode.UNAUTHORIZED,
                message = "요청자 식별자 헤더가 필요합니다."
            )
    }

    /**
     * 다운로드 결과를 응답 DTO로 변환한다.
     */
    private fun AssetDownloadResult.toResponse(): AssetDownloadResponse {
        return AssetDownloadResponse(
            assetId = assetId,
            fileName = fileName,
            contentType = contentType,
            size = size,
            url = url,
            expiresAt = expiresAt
        )
    }

    /**
     * 표준 응답 포맷을 구성한다.
     */
    private fun toResponseEntity(
        requestId: String,
        result: AssetDownloadResult
    ): ResponseEntity<ApiResponse<AssetDownloadResponse>> {
        val response = ApiResponse.success(requestId, result.toResponse())
        return ResponseEntity.ok()
            .header(RequestContextHeaders.REQUEST_ID, requestId)
            .body(response)
    }
}
