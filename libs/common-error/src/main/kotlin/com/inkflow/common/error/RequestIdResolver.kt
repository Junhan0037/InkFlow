package com.inkflow.common.error

import org.springframework.web.server.ServerWebExchange
import java.util.UUID

/**
 * 요청 식별자를 해석하는 계약.
 */
fun interface RequestIdResolver {
    /**
     * 요청에서 requestId를 추출하거나 생성한다.
     */
    fun resolve(exchange: ServerWebExchange): String
}

/**
 * 헤더 기반으로 requestId를 해석하는 기본 구현체다.
 */
class HeaderRequestIdResolver(
    private val headerName: String = ErrorResponseHeaders.REQUEST_ID,
    private val requestIdGenerator: () -> String = { "req-${UUID.randomUUID()}" }
) : RequestIdResolver {
    /**
     * 헤더에서 requestId를 조회하고 없으면 새로 생성한다.
     */
    override fun resolve(exchange: ServerWebExchange): String {
        val headerValue = exchange.request.headers.getFirst(headerName)?.trim()
        return headerValue?.takeIf { it.isNotBlank() } ?: requestIdGenerator()
    }
}
