package com.inkflow.common.security

/**
 * 요청 헤더에서 인증 정보를 해석하는 계약.
 */
fun interface AuthenticationResolver {
    /**
     * 헤더를 기반으로 인증된 사용자를 반환한다.
     */
    fun authenticate(headers: Map<String, String>): AuthenticatedUser?
}

/**
 * 인증 로직을 사용하지 않는 기본 구현체.
 */
class NoOpAuthenticationResolver : AuthenticationResolver {
    /**
     * 인증 처리를 생략하고 null을 반환한다.
     */
    override fun authenticate(headers: Map<String, String>): AuthenticatedUser? = null
}

/**
 * 여러 인증 해석기를 순차적으로 적용하는 구현체.
 */
class CompositeAuthenticationResolver(
    private val resolvers: List<AuthenticationResolver>
) : AuthenticationResolver {
    /**
     * 등록된 해석기 중 최초로 인증에 성공한 결과를 반환한다.
     */
    override fun authenticate(headers: Map<String, String>): AuthenticatedUser? {
        return resolvers.asSequence()
            .mapNotNull { it.authenticate(headers) }
            .firstOrNull()
    }
}

/**
 * Bearer 토큰 검증을 담당하는 계약이다.
 */
fun interface AccessTokenVerifier {
    /**
     * 토큰을 검증하고 인증된 사용자 정보를 반환한다.
     */
    fun verify(token: String): AuthenticatedUser?
}

/**
 * Authorization 헤더의 Bearer 토큰을 검증하는 해석기다.
 */
class BearerTokenAuthenticationResolver(
    private val tokenVerifier: AccessTokenVerifier,
    private val authorizationHeader: String = RequestContextHeaders.AUTHORIZATION
) : AuthenticationResolver {
    /**
     * Bearer 토큰을 추출해 검증 결과를 반환한다.
     */
    override fun authenticate(headers: Map<String, String>): AuthenticatedUser? {
        val value = HeaderUtils.getFirst(headers, authorizationHeader)?.trim() ?: return null
        if (!value.startsWith("Bearer ", ignoreCase = true)) {
            return null
        }
        // 대소문자에 영향을 받지 않도록 첫 공백 이후 토큰만 추출한다.
        val token = value.substringAfter(" ").trim()
        if (token.isBlank()) {
            return null
        }
        return tokenVerifier.verify(token)
    }
}

/**
 * 내부 호출용 사용자 헤더를 해석하는 구현체다.
 */
class HeaderAuthenticationResolver(
    private val userIdHeader: String = RequestContextHeaders.USER_ID,
    private val rolesHeader: String = RequestContextHeaders.USER_ROLES
) : AuthenticationResolver {
    /**
     * 사용자 ID/역할 헤더를 기반으로 인증 정보를 구성한다.
     */
    override fun authenticate(headers: Map<String, String>): AuthenticatedUser? {
        val userId = HeaderUtils.getFirst(headers, userIdHeader)?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val roles = HeaderUtils.splitCommaSeparated(HeaderUtils.getFirst(headers, rolesHeader))
        return AuthenticatedUser(userId = userId, roles = roles)
    }
}
