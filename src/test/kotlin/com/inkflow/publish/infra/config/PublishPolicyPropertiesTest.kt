package com.inkflow.publish.infra.config

import com.inkflow.publish.domain.PublishPolicyStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * PublishPolicyProperties 규칙 변환 로직을 검증한다.
 */
class PublishPolicyPropertiesTest {
    /**
     * Rule 입력이 정규화되어 도메인 정책으로 변환되는지 확인한다.
     */
    @Test
    fun toDomain_normalizesFields() {
        val rule = PublishPolicyProperties.Rule(
            region = " kr ",
            language = " KO ",
            status = PublishPolicyStatus.ACTIVE,
            lockedReason = "  운영 제한  "
        )

        // 실행: 규칙을 도메인 정책으로 변환한다.
        val policy = rule.toDomain()

        // 검증: 지역/언어/잠금 사유가 정규화된다.
        assertEquals("KR", policy.region)
        assertEquals("ko", policy.language)
        assertEquals("운영 제한", policy.lockedReason)
    }

    /**
     * 잠금 사유가 공백이면 null로 정리되어야 한다.
     */
    @Test
    fun toDomain_clearsBlankLockedReason() {
        val rule = PublishPolicyProperties.Rule(
            region = "US",
            language = "en",
            status = PublishPolicyStatus.ACTIVE,
            lockedReason = "   "
        )

        // 실행: 규칙을 도메인 정책으로 변환한다.
        val policy = rule.toDomain()

        // 검증: 잠금 사유는 null 처리된다.
        assertNull(policy.lockedReason)
    }
}
