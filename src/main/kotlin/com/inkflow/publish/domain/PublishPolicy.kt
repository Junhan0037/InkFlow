package com.inkflow.publish.domain

import java.time.Instant

/**
 * 지역/언어별 퍼블리시 정책을 표현.
 */
data class PublishPolicy(
    val region: String,
    val language: String,
    val status: PublishPolicyStatus,
    val availableFrom: Instant?,
    val availableUntil: Instant?,
    val lockedReason: String?
) {
    init {
        require(region.isNotBlank()) { "region은 비어 있을 수 없습니다." }
        require(language.isNotBlank()) { "language는 비어 있을 수 없습니다." }
        if (availableFrom != null && availableUntil != null) {
            require(!availableFrom.isAfter(availableUntil)) { "availableFrom은 availableUntil 이전이어야 합니다." }
        }
        if (lockedReason != null) {
            require(lockedReason.isNotBlank()) { "lockedReason은 빈 문자열일 수 없습니다." }
        }
    }

    /**
     * 현재 시각 기준 퍼블리시 가능 여부를 평가한다.
     */
    fun evaluate(now: Instant): PublishPolicyDecision {
        if (status == PublishPolicyStatus.LOCKED) {
            return PublishPolicyDecision(
                allowed = false,
                reason = lockedReason ?: "운영 정책으로 잠금 상태입니다."
            )
        }
        if (status == PublishPolicyStatus.DISABLED) {
            return PublishPolicyDecision(
                allowed = false,
                reason = "퍼블리시 정책에 의해 비활성화되었습니다."
            )
        }
        if (availableFrom != null && now.isBefore(availableFrom)) {
            return PublishPolicyDecision(
                allowed = false,
                reason = "노출 시작 시간이 아직 도래하지 않았습니다."
            )
        }
        if (availableUntil != null && now.isAfter(availableUntil)) {
            return PublishPolicyDecision(
                allowed = false,
                reason = "노출 기간이 종료되었습니다."
            )
        }
        return PublishPolicyDecision(allowed = true, reason = null)
    }

    companion object {
        /**
         * 정책이 정의되지 않은 경우 기본 정책을 생성한다.
         */
        fun defaultPolicy(region: String, language: String, status: PublishPolicyStatus): PublishPolicy {
            return PublishPolicy(
                region = region,
                language = language,
                status = status,
                availableFrom = null,
                availableUntil = null,
                lockedReason = null
            )
        }
    }
}

/**
 * 정책 평가 결과를 전달한다.
 */
data class PublishPolicyDecision(
    val allowed: Boolean,
    val reason: String?
)
