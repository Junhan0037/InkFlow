package com.inkflow.publish.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * PublishPolicy의 평가 규칙을 검증한다.
 */
class PublishPolicyTest {
    /**
     * 활성 정책이 유효 기간 안에 있으면 허용되어야 한다.
     */
    @Test
    fun evaluate_allowsWhenWithinWindow() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val policy = PublishPolicy(
            region = "KR",
            language = "ko",
            status = PublishPolicyStatus.ACTIVE,
            availableFrom = now.minusSeconds(60),
            availableUntil = now.plusSeconds(60),
            lockedReason = null
        )

        // 실행: 정책 평가를 수행한다.
        val decision = policy.evaluate(now)

        // 검증: 허용 상태와 사유 없음이 반환된다.
        assertTrue(decision.allowed)
        assertNull(decision.reason)
    }

    /**
     * 잠금 상태라면 잠금 사유와 함께 차단해야 한다.
     */
    @Test
    fun evaluate_deniesWhenLocked() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val policy = PublishPolicy(
            region = "US",
            language = "en",
            status = PublishPolicyStatus.LOCKED,
            availableFrom = null,
            availableUntil = null,
            lockedReason = "운영 정책 잠금"
        )

        // 실행: 정책 평가를 수행한다.
        val decision = policy.evaluate(now)

        // 검증: 잠금 사유로 차단된다.
        assertFalse(decision.allowed)
        assertEquals("운영 정책 잠금", decision.reason)
    }

    /**
     * 비활성화 상태라면 비활성화 사유로 차단해야 한다.
     */
    @Test
    fun evaluate_deniesWhenDisabled() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val policy = PublishPolicy(
            region = "JP",
            language = "ja",
            status = PublishPolicyStatus.DISABLED,
            availableFrom = null,
            availableUntil = null,
            lockedReason = null
        )

        // 실행: 정책 평가를 수행한다.
        val decision = policy.evaluate(now)

        // 검증: 비활성화 안내 문구가 반환된다.
        assertFalse(decision.allowed)
        assertEquals("퍼블리시 정책에 의해 비활성화되었습니다.", decision.reason)
    }

    /**
     * 노출 시작 시간 이전이면 차단해야 한다.
     */
    @Test
    fun evaluate_deniesWhenBeforeAvailableFrom() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val policy = PublishPolicy(
            region = "KR",
            language = "ko",
            status = PublishPolicyStatus.ACTIVE,
            availableFrom = now.plusSeconds(60),
            availableUntil = null,
            lockedReason = null
        )

        // 실행: 정책 평가를 수행한다.
        val decision = policy.evaluate(now)

        // 검증: 시작 시간이 도래하지 않았다는 사유가 반환된다.
        assertFalse(decision.allowed)
        assertEquals("노출 시작 시간이 아직 도래하지 않았습니다.", decision.reason)
    }

    /**
     * 노출 종료 시간 이후이면 차단해야 한다.
     */
    @Test
    fun evaluate_deniesWhenAfterAvailableUntil() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val policy = PublishPolicy(
            region = "KR",
            language = "ko",
            status = PublishPolicyStatus.ACTIVE,
            availableFrom = null,
            availableUntil = now.minusSeconds(60),
            lockedReason = null
        )

        // 실행: 정책 평가를 수행한다.
        val decision = policy.evaluate(now)

        // 검증: 종료 시간 초과 사유가 반환된다.
        assertFalse(decision.allowed)
        assertEquals("노출 기간이 종료되었습니다.", decision.reason)
    }
}
