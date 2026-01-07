package com.inkflow.common.error

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * WebFlux 환경에서 공통 에러 응답을 생성하는 핸들러.
 */
@RestControllerAdvice
class CommonErrorHandler(
    private val errorResponseFactory: ErrorResponseFactory = ErrorResponseFactory(),
    private val logger: Logger = LoggerFactory.getLogger(CommonErrorHandler::class.java)
) {
    /**
     * 비즈니스 예외를 표준 에러 응답으로 변환한다.
     */
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        exception: BusinessException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<ErrorDetail>>> {
        val descriptor = errorResponseFactory.fromException(exchange, exception)
        logBusinessError(exception, descriptor, exchange)
        return Mono.just(toResponseEntity(descriptor))
    }

    /**
     * 시스템 예외를 표준 에러 응답으로 변환한다.
     */
    @ExceptionHandler(SystemException::class)
    fun handleSystemException(
        exception: SystemException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<ErrorDetail>>> {
        val descriptor = errorResponseFactory.fromException(exchange, exception)
        logSystemError(exception, descriptor, exchange)
        return Mono.just(toResponseEntity(descriptor))
    }

    /**
     * 처리되지 않은 예외를 공통 포맷으로 변환한다.
     */
    @ExceptionHandler(Throwable::class)
    fun handleUnexpectedException(
        exception: Throwable,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ApiResponse<ErrorDetail>>> {
        val descriptor = errorResponseFactory.fromException(exchange, exception)
        logUnexpectedError(exception, descriptor, exchange)
        return Mono.just(toResponseEntity(descriptor))
    }

    /**
     * ResponseEntity에 requestId 헤더를 반영한다.
     */
    private fun toResponseEntity(descriptor: ErrorResponseDescriptor): ResponseEntity<ApiResponse<ErrorDetail>> {
        val headers = HttpHeaders()
        headers.set(ErrorResponseHeaders.REQUEST_ID, descriptor.response.requestId)
        return ResponseEntity(descriptor.response, headers, descriptor.httpStatus)
    }

    /**
     * 비즈니스 예외 로그를 기록한다.
     */
    private fun logBusinessError(
        exception: BusinessException,
        descriptor: ErrorResponseDescriptor,
        exchange: ServerWebExchange
    ) {
        logger.warn(
            "api.business.error path={} code={} requestId={} message={}",
            exchange.request.path.pathWithinApplication().value(),
            descriptor.errorCode.code,
            descriptor.response.requestId,
            exception.message
        )
    }

    /**
     * 시스템 예외 로그를 기록한다.
     */
    private fun logSystemError(
        exception: SystemException,
        descriptor: ErrorResponseDescriptor,
        exchange: ServerWebExchange
    ) {
        logger.error(
            "api.system.error path={} code={} requestId={} message={}",
            exchange.request.path.pathWithinApplication().value(),
            descriptor.errorCode.code,
            descriptor.response.requestId,
            exception.message,
            exception
        )
    }

    /**
     * 예상치 못한 예외 로그를 기록한다.
     */
    private fun logUnexpectedError(
        exception: Throwable,
        descriptor: ErrorResponseDescriptor,
        exchange: ServerWebExchange
    ) {
        logger.error(
            "api.unexpected.error path={} code={} requestId={} message={}",
            exchange.request.path.pathWithinApplication().value(),
            descriptor.errorCode.code,
            descriptor.response.requestId,
            exception.message,
            exception
        )
    }
}
