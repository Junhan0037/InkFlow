package com.inkflow.common.error

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * 공통 에러 매핑 규칙을 검증한다.
 */
class DefaultErrorResolverTest {
    /**
     * BusinessException이 동일한 ErrorCode로 매핑되는지 확인한다.
     */
    @Test
    fun resolve_mapsBusinessException() {
        val resolver = DefaultErrorResolver()
        val exception = BusinessException(
            errorCode = ErrorCode.FORBIDDEN,
            details = mapOf("field" to "value"),
            message = "권한이 없습니다."
        )

        val resolved = resolver.resolve(exception)

        assertEquals(ErrorCode.FORBIDDEN, resolved.errorCode)
        assertEquals("권한이 없습니다.", resolved.message)
        assertEquals("value", resolved.details["field"])
    }

    /**
     * ResponseStatusException이 HTTP 상태에 맞는 ErrorCode로 매핑되는지 확인한다.
     */
    @Test
    fun resolve_mapsResponseStatusException() {
        val resolver = DefaultErrorResolver()
        val exception = ResponseStatusException(HttpStatus.NOT_FOUND, "리소스 없음")

        val resolved = resolver.resolve(exception)

        assertEquals(ErrorCode.NOT_FOUND, resolved.errorCode)
        assertEquals("리소스 없음", resolved.message)
    }

    /**
     * IllegalArgumentException이 INVALID_REQUEST로 매핑되는지 확인한다.
     */
    @Test
    fun resolve_mapsIllegalArgumentException() {
        val resolver = DefaultErrorResolver()
        val exception = IllegalArgumentException("잘못된 요청")

        val resolved = resolver.resolve(exception)

        assertEquals(ErrorCode.INVALID_REQUEST, resolved.errorCode)
        assertEquals("잘못된 요청", resolved.message)
    }

    /**
     * SystemException이 동일한 ErrorCode로 매핑되는지 확인한다.
     */
    @Test
    fun resolve_mapsSystemException() {
        val resolver = DefaultErrorResolver()
        val exception = SystemException(
            errorCode = ErrorCode.DEPENDENCY_FAILURE,
            message = "의존성 장애"
        )

        val resolved = resolver.resolve(exception)

        assertEquals(ErrorCode.DEPENDENCY_FAILURE, resolved.errorCode)
        assertEquals("의존성 장애", resolved.message)
    }

    /**
     * HTTP 상태 코드 매핑 규칙이 기대대로 동작하는지 확인한다.
     */
    @Test
    fun defaultHttpStatusErrorCodeMapper_mapsStatus() {
        val mapper = DefaultHttpStatusErrorCodeMapper()

        assertEquals(ErrorCode.RATE_LIMITED, mapper.resolve(HttpStatus.TOO_MANY_REQUESTS))
        assertEquals(ErrorCode.DEPENDENCY_FAILURE, mapper.resolve(HttpStatus.SERVICE_UNAVAILABLE))
    }
}
