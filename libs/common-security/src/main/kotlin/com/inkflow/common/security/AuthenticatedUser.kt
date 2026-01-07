package com.inkflow.common.security

/**
 * 인증이 완료된 사용자의 핵심 식별 정보를 담는다.
 */
data class AuthenticatedUser(
    val userId: String,
    val roles: Set<String>,
    val attributes: Map<String, String> = emptyMap()
) {
    init {
        require(userId.isNotBlank()) { "userId는 비어 있을 수 없습니다." }
    }

    /**
     * 요구 역할 중 하나라도 보유했는지 확인한다.
     */
    fun hasAnyRole(requiredRoles: Set<String>): Boolean {
        if (requiredRoles.isEmpty()) {
            return true
        }
        return roles.any { it in requiredRoles }
    }
}
