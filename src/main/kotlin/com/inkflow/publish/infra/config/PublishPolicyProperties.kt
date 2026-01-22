package com.inkflow.publish.infra.config

import com.inkflow.publish.domain.PublishPolicy
import com.inkflow.publish.domain.PublishPolicyStatus
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Instant

/**
 * 퍼블리시 정책 구성을 정의.
 */
@ConfigurationProperties(prefix = "inkflow.publish.policy")
data class PublishPolicyProperties(
    val defaultStatus: PublishPolicyStatus = PublishPolicyStatus.ACTIVE,
    val rules: List<Rule> = emptyList()
) {
    /**
     * 지역/언어 정책 규칙을 정의한다.
     */
    data class Rule(
        val region: String = "",
        val language: String = "",
        val status: PublishPolicyStatus = PublishPolicyStatus.ACTIVE,
        val availableFrom: Instant? = null,
        val availableUntil: Instant? = null,
        val lockedReason: String? = null
    ) {
        /**
         * 규칙을 도메인 정책으로 변환한다.
         */
        fun toDomain(): PublishPolicy {
            val normalizedRegion = region.trim().uppercase()
            val normalizedLanguage = language.trim().lowercase()
            require(normalizedRegion.isNotBlank()) { "region은 비어 있을 수 없습니다." }
            require(normalizedLanguage.isNotBlank()) { "language는 비어 있을 수 없습니다." }

            val normalizedReason = lockedReason?.trim()?.takeIf { it.isNotBlank() }
            return PublishPolicy(
                region = normalizedRegion,
                language = normalizedLanguage,
                status = status,
                availableFrom = availableFrom,
                availableUntil = availableUntil,
                lockedReason = normalizedReason
            )
        }
    }
}
