package com.inkflow.publish.application

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.publish.domain.PublishPolicy
import com.inkflow.publish.domain.PublishPolicyRepository
import com.inkflow.publish.domain.PublishPolicyStatus
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * 지역/언어 퍼블리시 정책을 검증하는 서비스.
 */
@Service
class PublishPolicyService(
    private val publishPolicyRepository: PublishPolicyRepository
) {
    /**
     * 퍼블리시 가능 여부를 검증하고 정책 정보를 반환한다.
     */
    fun ensurePublishable(region: String, language: String, now: Instant): PublishPolicy {
        val normalizedRegion = normalizeRegion(region)
        val normalizedLanguage = normalizeLanguage(language)
        val policy = publishPolicyRepository.findPolicy(normalizedRegion, normalizedLanguage)
            ?: PublishPolicy.defaultPolicy(
                region = normalizedRegion,
                language = normalizedLanguage,
                status = PublishPolicyStatus.ACTIVE
            )

        val decision = policy.evaluate(now)
        if (!decision.allowed) {
            throw BusinessException(
                errorCode = ErrorCode.FORBIDDEN,
                details = mapOf(
                    "region" to normalizedRegion,
                    "language" to normalizedLanguage,
                    "reason" to (decision.reason ?: "unknown")
                ),
                message = decision.reason ?: "퍼블리시 정책으로 차단되었습니다."
            )
        }

        return policy
    }

    /**
     * 지역 코드를 표준 형식(대문자)으로 변환한다.
     */
    private fun normalizeRegion(region: String): String {
        return region.trim().uppercase()
    }

    /**
     * 언어 코드를 표준 형식(소문자)으로 변환한다.
     */
    private fun normalizeLanguage(language: String): String {
        return language.trim().lowercase()
    }
}
