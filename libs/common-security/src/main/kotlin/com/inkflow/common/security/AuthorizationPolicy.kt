package com.inkflow.common.security

import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.server.PathContainer
import org.springframework.web.util.pattern.PathPattern
import org.springframework.web.util.pattern.PathPatternParser

/**
 * 인증/권한 평가에 필요한 요청 정보를 전달한다.
 */
data class AuthorizationRequest(
    val path: String,
    val method: HttpMethod?,
    val user: AuthenticatedUser?
)

/**
 * 경로 기반 권한 규칙을 정의한다.
 */
data class AuthorizationRule(
    val pathPattern: String,
    val method: HttpMethod? = null,
    val requiredRoles: Set<String> = emptySet(),
    val allowAnonymous: Boolean = false
)

/**
 * 권한 평가 결과를 표현한다.
 */
data class AuthorizationDecision(
    val allowed: Boolean,
    val failureStatus: HttpStatus? = null,
    val failureReason: String? = null
) {
    companion object {
        /**
         * 요청 허용 결정을 생성한다.
         */
        fun allow(): AuthorizationDecision = AuthorizationDecision(allowed = true)

        /**
         * 요청 거부 결정을 생성한다.
         */
        fun deny(status: HttpStatus, reason: String): AuthorizationDecision {
            return AuthorizationDecision(allowed = false, failureStatus = status, failureReason = reason)
        }
    }
}

/**
 * 인증/권한 정책을 평가하는 계약이다.
 */
fun interface AuthorizationPolicy {
    /**
     * 요청 정보를 기반으로 접근 허용 여부를 판단한다.
     */
    fun evaluate(request: AuthorizationRequest): AuthorizationDecision
}

/**
 * 모든 요청을 허용하는 기본 정책이다.
 */
class AllowAllAuthorizationPolicy : AuthorizationPolicy {
    /**
     * 모든 요청을 허용한다.
     */
    override fun evaluate(request: AuthorizationRequest): AuthorizationDecision = AuthorizationDecision.allow()
}

/**
 * 경로 패턴 기반 권한 정책을 구현한다.
 */
class PathPatternAuthorizationPolicy(
    rules: List<AuthorizationRule>,
    private val defaultAllowAnonymous: Boolean = false
) : AuthorizationPolicy {
    private val parser = PathPatternParser()
    private val compiledRules = rules.map { rule ->
        CompiledAuthorizationRule(rule, parser.parse(rule.pathPattern))
    }

    /**
     * 정의된 규칙을 순서대로 적용해 접근 허용 여부를 판단한다.
     */
    override fun evaluate(request: AuthorizationRequest): AuthorizationDecision {
        val matchedRule = compiledRules.firstOrNull { it.matches(request) }
            ?: return defaultDecision(request)
        return matchedRule.evaluate(request.user)
    }

    /**
     * 매칭 규칙이 없을 때 기본 정책을 적용한다.
     */
    private fun defaultDecision(request: AuthorizationRequest): AuthorizationDecision {
        if (defaultAllowAnonymous) {
            return AuthorizationDecision.allow()
        }
        return if (request.user == null) {
            AuthorizationDecision.deny(HttpStatus.UNAUTHORIZED, "unauthenticated")
        } else {
            AuthorizationDecision.allow()
        }
    }

    /**
     * 파싱된 패턴과 규칙을 함께 보관한다.
     */
    private data class CompiledAuthorizationRule(
        val rule: AuthorizationRule,
        val pattern: PathPattern
    ) {
        /**
         * 요청 정보가 규칙과 매칭되는지 확인한다.
         */
        fun matches(request: AuthorizationRequest): Boolean {
            if (rule.method != null && rule.method != request.method) {
                return false
            }
            val pathContainer = PathContainer.parsePath(request.path)
            return pattern.matches(pathContainer)
        }

        /**
         * 매칭된 규칙에 따라 권한을 평가한다.
         */
        fun evaluate(user: AuthenticatedUser?): AuthorizationDecision {
            if (rule.allowAnonymous) {
                return AuthorizationDecision.allow()
            }
            if (user == null) {
                return AuthorizationDecision.deny(HttpStatus.UNAUTHORIZED, "unauthenticated")
            }
            if (rule.requiredRoles.isNotEmpty() && !user.hasAnyRole(rule.requiredRoles)) {
                return AuthorizationDecision.deny(HttpStatus.FORBIDDEN, "forbidden")
            }
            return AuthorizationDecision.allow()
        }
    }
}
