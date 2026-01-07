package com.inkflow.common.security

import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * 인증/권한 처리와 요청 컨텍스트 표준화를 동시에 수행하는 WebFlux 필터.
 */
class SecurityWebFilter(
    private val authenticationResolver: AuthenticationResolver = NoOpAuthenticationResolver(),
    private val authorizationPolicy: AuthorizationPolicy = AllowAllAuthorizationPolicy(),
    private val requestContextFactory: RequestContextFactory = RequestContextFactory(),
    private val errorResponseWriter: SecurityErrorResponseWriter = DefaultSecurityErrorResponseWriter(),
    private val order: Int = Ordered.HIGHEST_PRECEDENCE + 10
) : WebFilter, Ordered {
    /**
     * 요청 컨텍스트를 구성하고 권한 정책을 평가한 뒤 체인을 실행한다.
     */
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val headers = HeaderUtils.toSingleValueMap(exchange.request.headers)
        val remoteAddress = resolveRemoteAddress(exchange)
        val baseContext = requestContextFactory.create(headers, remoteAddress)
        val authenticatedUser = resolveAuthenticatedUser(headers)
        val requestContext = baseContext.withUser(authenticatedUser)

        // 이후 핸들러에서 접근할 수 있도록 교환 객체에 컨텍스트를 저장한다.
        RequestContextAccessor.set(exchange, requestContext)
        applyResponseHeaders(exchange.response.headers, requestContext)

        val authorizationDecision = authorizationPolicy.evaluate(
            AuthorizationRequest(
                path = exchange.request.path.pathWithinApplication().value(),
                method = exchange.request.method,
                user = authenticatedUser
            )
        )

        if (!authorizationDecision.allowed) {
            return errorResponseWriter.write(exchange, authorizationDecision, requestContext)
        }

        return chain.filter(exchange)
            .contextWrite { context -> ReactorRequestContext.put(context, requestContext) }
    }

    /**
     * 필터 우선순위를 정의한다.
     */
    override fun getOrder(): Int = order

    /**
     * 응답 헤더에 요청 ID와 Trace 컨텍스트를 반영한다.
     */
    private fun applyResponseHeaders(headers: HttpHeaders, requestContext: RequestContext) {
        headers.set(RequestContextHeaders.REQUEST_ID, requestContext.requestId)
        val traceContext = requestContext.traceContext ?: return
        // spanId가 없는 경우에는 표준 traceparent를 구성할 수 없다.
        if (traceContext.spanId.isNullOrBlank()) {
            return
        }
        val traceHeaders = mutableMapOf<String, String>()
        requestContextFactory.injectTraceContext(traceHeaders, traceContext)
        traceHeaders.forEach { (key, value) -> headers.set(key, value) }
    }

    /**
     * 인증 실패 시에도 안정적으로 처리할 수 있도록 예외를 흡수한다.
     */
    private fun resolveAuthenticatedUser(headers: Map<String, String>): AuthenticatedUser? {
        return runCatching { authenticationResolver.authenticate(headers) }.getOrNull()
    }

    /**
     * 원격 주소를 문자열로 변환한다.
     */
    private fun resolveRemoteAddress(exchange: ServerWebExchange): String? {
        return exchange.request.remoteAddress?.address?.hostAddress
    }
}
