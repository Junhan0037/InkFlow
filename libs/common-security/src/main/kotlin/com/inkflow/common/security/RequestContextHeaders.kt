package com.inkflow.common.security

/**
 * 요청 컨텍스트 표준화를 위한 헤더 이름을 정의한다.
 */
object RequestContextHeaders {
    /**
     * 요청 식별자 전달에 사용하는 헤더.
     */
    const val REQUEST_ID = "X-Request-Id"

    /**
     * Idempotency 키 전달에 사용하는 헤더.
     */
    const val IDEMPOTENCY_KEY = "Idempotency-Key"

    /**
     * 인증된 사용자 식별자 전달에 사용하는 헤더.
     */
    const val USER_ID = "X-User-Id"

    /**
     * 사용자 역할 전달에 사용하는 헤더.
     */
    const val USER_ROLES = "X-User-Roles"

    /**
     * 테넌트 식별자 전달에 사용하는 헤더.
     */
    const val TENANT_ID = "X-Tenant-Id"

    /**
     * 인증 토큰 전달에 사용하는 헤더.
     */
    const val AUTHORIZATION = "Authorization"

    /**
     * 클라이언트 IP 전달에 사용하는 헤더.
     */
    const val FORWARDED_FOR = "X-Forwarded-For"

    /**
     * 프록시가 전달한 실제 IP 헤더.
     */
    const val REAL_IP = "X-Real-IP"

    /**
     * 사용자 에이전트 전달에 사용하는 헤더.
     */
    const val USER_AGENT = "User-Agent"
}
