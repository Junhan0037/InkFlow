package com.inkflow.common.security

import com.inkflow.common.observability.TraceContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

/**
 * 공통 보안/권한 유틸리티 동작을 검증한다.
 */
class SecurityUtilsTest {
    /**
     * 콤마 구분 역할 파싱이 정상 동작하는지 확인한다.
     */
    @Test
    fun headerUtils_splitsCommaSeparatedRoles() {
        val roles = HeaderUtils.splitCommaSeparated("ADMIN, USER, ,REVIEWER")

        assertTrue(roles.contains("ADMIN"))
        assertTrue(roles.contains("USER"))
        assertTrue(roles.contains("REVIEWER"))
    }

    /**
     * 헤더 이름은 대소문자 구분 없이 조회된다.
     */
    @Test
    fun headerUtils_getFirstIgnoresCase() {
        val headers = mapOf("X-User-Id" to "creator-1")

        val value = HeaderUtils.getFirst(headers, "x-user-id")

        assertEquals("creator-1", value)
    }

    /**
     * 사용자 헤더를 기반으로 AuthenticatedUser가 생성되는지 확인한다.
     */
    @Test
    fun headerAuthenticationResolver_readsUserAndRoles() {
        val resolver = HeaderAuthenticationResolver()
        val headers = mapOf(
            RequestContextHeaders.USER_ID to "creator-1",
            RequestContextHeaders.USER_ROLES to "ADMIN, USER"
        )

        val user = resolver.authenticate(headers)

        requireNotNull(user) { "인증 결과가 null이면 안 됩니다." }
        assertEquals("creator-1", user.userId)
        assertTrue(user.roles.contains("ADMIN"))
        assertTrue(user.roles.contains("USER"))
    }

    /**
     * RequestContext는 사용자 정보를 덮어써서 반환한다.
     */
    @Test
    fun requestContext_withUserUpdatesAuthenticatedUser() {
        val context = RequestContext(
            requestId = "req-1",
            traceContext = TraceContext(traceId = "0123456789abcdef0123456789abcdef"),
            authenticatedUser = null,
            clientIp = null,
            userAgent = null,
            tenantId = null
        )

        val updated = context.withUser(AuthenticatedUser(userId = "user-1", roles = setOf("USER")))

        assertEquals("user-1", updated.authenticatedUser?.userId)
    }

    /**
     * 경로 기반 권한 정책이 역할 요구사항을 적용하는지 확인한다.
     */
    @Test
    fun pathPatternAuthorizationPolicy_enforcesRoles() {
        val policy = PathPatternAuthorizationPolicy(
            rules = listOf(
                AuthorizationRule(
                    pathPattern = "/admin/**",
                    method = HttpMethod.GET,
                    requiredRoles = setOf("ADMIN")
                )
            )
        )

        val allowedDecision = policy.evaluate(
            AuthorizationRequest(
                path = "/admin/works",
                method = HttpMethod.GET,
                user = AuthenticatedUser(userId = "user-1", roles = setOf("ADMIN"))
            )
        )
        val deniedDecision = policy.evaluate(
            AuthorizationRequest(
                path = "/admin/works",
                method = HttpMethod.GET,
                user = AuthenticatedUser(userId = "user-2", roles = setOf("USER"))
            )
        )

        assertTrue(allowedDecision.allowed)
        assertEquals(HttpStatus.FORBIDDEN, deniedDecision.failureStatus)
    }

    /**
     * 익명 허용 정책이 활성화되면 인증 없이도 접근이 허용된다.
     */
    @Test
    fun pathPatternAuthorizationPolicy_allowsAnonymousWhenConfigured() {
        val policy = PathPatternAuthorizationPolicy(
            rules = emptyList(),
            defaultAllowAnonymous = true
        )

        val decision = policy.evaluate(
            AuthorizationRequest(
                path = "/public/info",
                method = HttpMethod.GET,
                user = null
            )
        )

        assertTrue(decision.allowed)
    }

    /**
     * 요청 헤더에서 requestId와 Trace 컨텍스트가 추출되는지 확인한다.
     */
    @Test
    fun requestContextFactory_resolvesRequestIdAndTrace() {
        val factory = RequestContextFactory(requestIdGenerator = { "req-generated" })
        val headers = mapOf(
            RequestContextHeaders.REQUEST_ID to "req-1",
            "traceparent" to "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01"
        )

        val context = factory.create(headers, "127.0.0.1")

        assertEquals("req-1", context.requestId)
        assertNotNull(context.traceContext)
        assertEquals("127.0.0.1", context.clientIp)
    }

    /**
     * requestId가 없으면 생성기가 동작하는지 확인한다.
     */
    @Test
    fun requestContextFactory_generatesRequestIdWhenMissing() {
        val factory = RequestContextFactory(requestIdGenerator = { "req-generated" })
        val context = factory.create(emptyMap(), null)

        assertEquals("req-generated", context.requestId)
        assertNull(context.traceContext)
    }
}
