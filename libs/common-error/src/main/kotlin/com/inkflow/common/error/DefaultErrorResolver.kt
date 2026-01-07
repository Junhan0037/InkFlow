package com.inkflow.common.error

import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ResponseStatusException

/**
 * 기본 예외 매핑 규칙을 제공하는 구현체.
 */
class DefaultErrorResolver(
    private val statusMapper: HttpStatusErrorCodeMapper = DefaultHttpStatusErrorCodeMapper()
) : ErrorResolver {
    /**
     * 예외 유형에 따라 에러 코드를 매핑한다.
     */
    override fun resolve(exception: Throwable): ResolvedError {
        return when (exception) {
            is BusinessException -> resolveFromBusiness(exception)
            is SystemException -> resolveFromSystem(exception)
            is WebExchangeBindException -> resolveFromValidation(exception)
            is ResponseStatusException -> resolveFromResponseStatus(exception)
            is IllegalArgumentException -> ResolvedError(
                errorCode = ErrorCode.INVALID_REQUEST,
                message = exception.message?.takeIf { it.isNotBlank() } ?: ErrorCode.INVALID_REQUEST.message
            )
            else -> ResolvedError(
                errorCode = ErrorCode.INTERNAL_ERROR,
                message = ErrorCode.INTERNAL_ERROR.message
            )
        }
    }

    /**
     * 비즈니스 예외를 표준 에러 정보로 변환한다.
     */
    private fun resolveFromBusiness(exception: BusinessException): ResolvedError {
        return ResolvedError(
            errorCode = exception.errorCode,
            message = exception.message,
            details = exception.details
        )
    }

    /**
     * 시스템 예외를 표준 에러 정보로 변환한다.
     */
    private fun resolveFromSystem(exception: SystemException): ResolvedError {
        return ResolvedError(
            errorCode = exception.errorCode,
            message = exception.message,
            details = exception.details
        )
    }

    /**
     * 검증 예외에서 필드 오류를 추출해 응답에 포함한다.
     */
    private fun resolveFromValidation(exception: WebExchangeBindException): ResolvedError {
        val details = exception.bindingResult.fieldErrors.associate { fieldError ->
            val message = fieldError.defaultMessage?.takeIf { it.isNotBlank() }
                ?: ErrorCode.INVALID_REQUEST.message
            fieldError.field to message
        }
        return ResolvedError(
            errorCode = ErrorCode.INVALID_REQUEST,
            message = ErrorCode.INVALID_REQUEST.message,
            details = details
        )
    }

    /**
     * ResponseStatusException을 에러 코드로 변환한다.
     */
    private fun resolveFromResponseStatus(exception: ResponseStatusException): ResolvedError {
        val httpStatus = resolveHttpStatus(exception.statusCode)
        val errorCode = statusMapper.resolve(httpStatus)
        val reason = exception.reason?.takeIf { it.isNotBlank() }
        return ResolvedError(
            errorCode = errorCode,
            message = reason ?: errorCode.message
        )
    }

    /**
     * HttpStatusCode를 HttpStatus로 변환한다.
     */
    private fun resolveHttpStatus(statusCode: HttpStatusCode): HttpStatus {
        return HttpStatus.resolve(statusCode.value()) ?: HttpStatus.INTERNAL_SERVER_ERROR
    }
}

/**
 * HTTP 상태 코드에서 ErrorCode를 조회하는 계약이다.
 */
fun interface HttpStatusErrorCodeMapper {
    /**
     * HTTP 상태에 해당하는 ErrorCode를 반환한다.
     */
    fun resolve(status: HttpStatus): ErrorCode
}

/**
 * 기본 HTTP 상태 코드 매핑을 제공하는 구현체다.
 */
class DefaultHttpStatusErrorCodeMapper : HttpStatusErrorCodeMapper {
    /**
     * 정의된 매핑 규칙을 적용한다.
     */
    override fun resolve(status: HttpStatus): ErrorCode {
        return when (status) {
            HttpStatus.BAD_REQUEST -> ErrorCode.INVALID_REQUEST
            HttpStatus.UNAUTHORIZED -> ErrorCode.UNAUTHORIZED
            HttpStatus.FORBIDDEN -> ErrorCode.FORBIDDEN
            HttpStatus.NOT_FOUND -> ErrorCode.NOT_FOUND
            HttpStatus.CONFLICT -> ErrorCode.CONFLICT
            HttpStatus.TOO_MANY_REQUESTS -> ErrorCode.RATE_LIMITED
            HttpStatus.SERVICE_UNAVAILABLE -> ErrorCode.DEPENDENCY_FAILURE
            else -> if (status.is4xxClientError) {
                ErrorCode.INVALID_REQUEST
            } else {
                ErrorCode.INTERNAL_ERROR
            }
        }
    }
}
