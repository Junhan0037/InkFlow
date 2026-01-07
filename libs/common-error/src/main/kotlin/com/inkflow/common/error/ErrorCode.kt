package com.inkflow.common.error

import io.grpc.Status
import org.springframework.http.HttpStatus

/**
 * 도메인/시스템 전반에서 사용하는 공통 에러 코드를 정의한다.
 */
enum class ErrorCode(
    val code: String,
    val message: String,
    val httpStatus: HttpStatus,
    val grpcCode: Status.Code,
    val retryable: Boolean
) {
    /**
     * 요청 값이 올바르지 않을 때 사용하는 코드다.
     */
    INVALID_REQUEST(
        code = "INVALID_REQUEST",
        message = "요청 값이 올바르지 않습니다.",
        httpStatus = HttpStatus.BAD_REQUEST,
        grpcCode = Status.Code.INVALID_ARGUMENT,
        retryable = false
    ),

    /**
     * 상태 전이가 허용되지 않을 때 사용하는 코드다.
     */
    INVALID_STATE(
        code = "INVALID_STATE",
        message = "요청 상태가 올바르지 않습니다.",
        httpStatus = HttpStatus.CONFLICT,
        grpcCode = Status.Code.FAILED_PRECONDITION,
        retryable = false
    ),

    /**
     * 인증이 필요한 요청에 사용하는 코드다.
     */
    UNAUTHORIZED(
        code = "UNAUTHORIZED",
        message = "인증이 필요합니다.",
        httpStatus = HttpStatus.UNAUTHORIZED,
        grpcCode = Status.Code.UNAUTHENTICATED,
        retryable = false
    ),

    /**
     * 권한이 없는 요청에 사용하는 코드다.
     */
    FORBIDDEN(
        code = "FORBIDDEN",
        message = "접근 권한이 없습니다.",
        httpStatus = HttpStatus.FORBIDDEN,
        grpcCode = Status.Code.PERMISSION_DENIED,
        retryable = false
    ),

    /**
     * 리소스를 찾을 수 없을 때 사용하는 코드다.
     */
    NOT_FOUND(
        code = "NOT_FOUND",
        message = "리소스를 찾을 수 없습니다.",
        httpStatus = HttpStatus.NOT_FOUND,
        grpcCode = Status.Code.NOT_FOUND,
        retryable = false
    ),

    /**
     * 리소스 충돌이 발생했을 때 사용하는 코드다.
     */
    CONFLICT(
        code = "CONFLICT",
        message = "리소스 충돌이 발생했습니다.",
        httpStatus = HttpStatus.CONFLICT,
        grpcCode = Status.Code.ABORTED,
        retryable = false
    ),

    /**
     * 요청 한도를 초과했을 때 사용하는 코드다.
     */
    RATE_LIMITED(
        code = "RATE_LIMITED",
        message = "요청이 너무 많습니다.",
        httpStatus = HttpStatus.TOO_MANY_REQUESTS,
        grpcCode = Status.Code.RESOURCE_EXHAUSTED,
        retryable = true
    ),

    /**
     * 외부 의존성 장애에 사용하는 코드다.
     */
    DEPENDENCY_FAILURE(
        code = "DEPENDENCY_FAILURE",
        message = "의존 서비스 오류가 발생했습니다.",
        httpStatus = HttpStatus.SERVICE_UNAVAILABLE,
        grpcCode = Status.Code.UNAVAILABLE,
        retryable = true
    ),

    /**
     * 처리 중 알 수 없는 오류가 발생했을 때 사용하는 코드다.
     */
    INTERNAL_ERROR(
        code = "INTERNAL_ERROR",
        message = "서버 오류가 발생했습니다.",
        httpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
        grpcCode = Status.Code.INTERNAL,
        retryable = true
    );

    /**
     * gRPC Status로 변환한다.
     */
    fun toGrpcStatus(description: String? = null): Status {
        val resolvedMessage = description?.takeIf { it.isNotBlank() } ?: message
        return Status.fromCode(grpcCode).withDescription(resolvedMessage)
    }
}
