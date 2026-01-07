package com.inkflow.common.security

import org.springframework.web.server.ServerWebExchange

/**
 * WebFlux 요청 처리에서 RequestContext를 주고받기 위한 접근자.
 */
object RequestContextAccessor {
    private const val ATTRIBUTE_KEY = "inkflow.requestContext"

    /**
     * 교환 객체에서 RequestContext를 조회한다.
     */
    fun get(exchange: ServerWebExchange): RequestContext? {
        return exchange.getAttribute(ATTRIBUTE_KEY)
    }

    /**
     * 교환 객체에 RequestContext를 저장한다.
     */
    fun set(exchange: ServerWebExchange, context: RequestContext) {
        exchange.attributes[ATTRIBUTE_KEY] = context
    }
}
