package com.inkflow.common.error

/**
 * 공통 에러 코드와 상세 정보를 담는 기본 예외.
 */
sealed class InkFlowException(
    val errorCode: ErrorCode,
    val details: Map<String, String> = emptyMap(),
    override val message: String = errorCode.message,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * 도메인 규칙 위반 등 비즈니스 오류에 사용하는 예외다.
 */
class BusinessException(
    errorCode: ErrorCode,
    details: Map<String, String> = emptyMap(),
    message: String = errorCode.message
) : InkFlowException(
    errorCode = errorCode,
    details = details,
    message = message
)

/**
 * 인프라/의존성 장애 등 시스템 오류에 사용하는 예외다.
 */
class SystemException(
    errorCode: ErrorCode = ErrorCode.INTERNAL_ERROR,
    details: Map<String, String> = emptyMap(),
    message: String = errorCode.message,
    cause: Throwable? = null
) : InkFlowException(
    errorCode = errorCode,
    details = details,
    message = message,
    cause = cause
)
