package com.inkflow.common.security

import com.inkflow.common.observability.TraceContext
import com.inkflow.common.observability.TraceContextPropagator
import com.inkflow.common.observability.W3CTraceContextPropagator
import java.util.UUID

/**
 * 요청 헤더와 네트워크 정보를 기반으로 RequestContext를 생성한다.
 */
class RequestContextFactory(
    private val requestIdGenerator: () -> String = { "req-${UUID.randomUUID()}" },
    private val traceContextPropagator: TraceContextPropagator = W3CTraceContextPropagator
) {
    /**
     * 요청 헤더와 주소 정보를 이용해 RequestContext를 생성한다.
     */
    fun create(
        headers: Map<String, String>,
        remoteAddress: String?,
        authenticatedUser: AuthenticatedUser? = null
    ): RequestContext {
        val requestId = resolveRequestId(headers)
        val traceContext = resolveTraceContext(headers)
        val clientIp = resolveClientIp(headers, remoteAddress)
        val userAgent = HeaderUtils.getFirst(headers, RequestContextHeaders.USER_AGENT)
        val tenantId = HeaderUtils.getFirst(headers, RequestContextHeaders.TENANT_ID)

        return RequestContext(
            requestId = requestId,
            traceContext = traceContext,
            authenticatedUser = authenticatedUser,
            clientIp = clientIp,
            userAgent = userAgent,
            tenantId = tenantId
        )
    }

    /**
     * 요청 ID를 헤더에서 추출하거나 새로 생성한다.
     */
    fun resolveRequestId(headers: Map<String, String>): String {
        return HeaderUtils.getFirst(headers, RequestContextHeaders.REQUEST_ID)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: requestIdGenerator()
    }

    /**
     * Trace 컨텍스트를 헤더에서 추출한다.
     */
    fun resolveTraceContext(headers: Map<String, String>): TraceContext? {
        return traceContextPropagator.extract(headers)
    }

    /**
     * Trace 컨텍스트를 응답 헤더에 주입한다.
     */
    fun injectTraceContext(headers: MutableMap<String, String>, traceContext: TraceContext) {
        traceContextPropagator.inject(headers, traceContext)
    }

    /**
     * 클라이언트 IP를 표준 헤더 우선순위로 선택한다.
     */
    private fun resolveClientIp(headers: Map<String, String>, remoteAddress: String?): String? {
        val forwardedFor = HeaderUtils.getFirst(headers, RequestContextHeaders.FORWARDED_FOR)
        if (!forwardedFor.isNullOrBlank()) {
            return forwardedFor.split(",").firstOrNull()?.trim()
        }
        val realIp = HeaderUtils.getFirst(headers, RequestContextHeaders.REAL_IP)
        if (!realIp.isNullOrBlank()) {
            return realIp.trim()
        }
        return remoteAddress
    }
}
