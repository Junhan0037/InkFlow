package com.inkflow.common.security

import com.inkflow.common.observability.TraceContext

/**
 * 요청 처리 범위에서 공유하는 표준 컨텍스트 모델.
 */
data class RequestContext(
    val requestId: String,
    val traceContext: TraceContext?,
    val authenticatedUser: AuthenticatedUser?,
    val clientIp: String?,
    val userAgent: String?,
    val tenantId: String?
) {
    init {
        require(requestId.isNotBlank()) { "requestId는 비어 있을 수 없습니다." }
    }

    /**
     * 인증 사용자 정보를 반영한 컨텍스트를 생성한다.
     */
    fun withUser(user: AuthenticatedUser?): RequestContext {
        return copy(authenticatedUser = user)
    }
}
