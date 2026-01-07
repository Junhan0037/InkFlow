package com.inkflow.common.security

import org.springframework.http.HttpStatus
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * 인증/권한 실패 응답을 표준화하는 계약.
 */
fun interface SecurityErrorResponseWriter {
    /**
     * 권한 실패 응답을 작성한다.
     */
    fun write(
        exchange: ServerWebExchange,
        decision: AuthorizationDecision,
        requestContext: RequestContext
    ): Mono<Void>
}

/**
 * 기본 인증/권한 실패 응답 작성기.
 */
class DefaultSecurityErrorResponseWriter : SecurityErrorResponseWriter {
    /**
     * 상태 코드만 설정하고 본문 없이 응답을 종료한다.
     */
    override fun write(
        exchange: ServerWebExchange,
        decision: AuthorizationDecision,
        requestContext: RequestContext
    ): Mono<Void> {
        val status = decision.failureStatus ?: HttpStatus.FORBIDDEN
        exchange.response.statusCode = status
        return exchange.response.setComplete()
    }
}
