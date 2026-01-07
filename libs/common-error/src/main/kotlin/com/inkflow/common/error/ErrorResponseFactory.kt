package com.inkflow.common.error

import org.springframework.http.HttpStatus
import org.springframework.web.server.ServerWebExchange

/**
 * 에러 응답 생성에 필요한 정보를 묶어 전달한다.
 */
data class ErrorResponseDescriptor(
    val response: ApiResponse<ErrorDetail>,
    val httpStatus: HttpStatus,
    val errorCode: ErrorCode
)

/**
 * 예외를 표준화된 에러 정보로 변환한 결과다.
 */
data class ResolvedError(
    val errorCode: ErrorCode,
    val message: String,
    val details: Map<String, String> = emptyMap()
)

/**
 * 예외를 표준 에러 정보로 매핑하는 계약이다.
 */
fun interface ErrorResolver {
    /**
     * 예외를 ResolvedError로 변환한다.
     */
    fun resolve(exception: Throwable): ResolvedError
}

/**
 * 에러 응답을 생성하는 팩토리다.
 */
class ErrorResponseFactory(
    private val requestIdResolver: RequestIdResolver = HeaderRequestIdResolver(),
    private val errorResolver: ErrorResolver = DefaultErrorResolver()
) {
    /**
     * 예외와 요청 정보를 기반으로 응답 정보를 생성한다.
     */
    fun fromException(exchange: ServerWebExchange, exception: Throwable): ErrorResponseDescriptor {
        val requestId = requestIdResolver.resolve(exchange)
        return fromException(requestId, exception)
    }

    /**
     * 예외와 requestId를 기반으로 응답 정보를 생성한다.
     */
    fun fromException(requestId: String, exception: Throwable): ErrorResponseDescriptor {
        val resolved = errorResolver.resolve(exception)
        return fromResolvedError(requestId, resolved)
    }

    /**
     * 표준 에러 정보를 응답 모델로 변환한다.
     */
    fun fromResolvedError(requestId: String, resolved: ResolvedError): ErrorResponseDescriptor {
        val errorDetail = ErrorDetail(
            details = resolved.details,
            retryable = resolved.errorCode.retryable
        )
        val response = ApiResponse(
            requestId = requestId,
            code = resolved.errorCode.code,
            message = resolved.message,
            data = errorDetail
        )
        return ErrorResponseDescriptor(
            response = response,
            httpStatus = resolved.errorCode.httpStatus,
            errorCode = resolved.errorCode
        )
    }
}
