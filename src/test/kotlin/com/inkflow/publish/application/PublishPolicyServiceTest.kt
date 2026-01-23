package com.inkflow.publish.application

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.publish.domain.PublishPolicy
import com.inkflow.publish.domain.PublishPolicyRepository
import com.inkflow.publish.domain.PublishPolicyStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * PublishPolicyService의 정책 검증 로직을 확인한다.
 */
class PublishPolicyServiceTest {
    /**
     * 저장소가 없을 때 기본 정책이 반환되고 입력이 정규화되어야 한다.
     */
    @Test
    fun ensurePublishable_returnsDefaultPolicyAndNormalizesInput() {
        val repository = RecordingPublishPolicyRepository(null)
        val service = PublishPolicyService(repository)
        val now = Instant.parse("2024-01-01T00:00:00Z")

        // 실행: 공백/대소문자가 섞인 요청을 전달한다.
        val policy = service.ensurePublishable(" kr ", " Ko ", now)

        // 검증: 기본 정책과 정규화 결과를 확인한다.
        assertEquals(PublishPolicyStatus.ACTIVE, policy.status)
        assertEquals("KR", policy.region)
        assertEquals("ko", policy.language)
        assertEquals("KR", repository.lastRegion)
        assertEquals("ko", repository.lastLanguage)
    }

    /**
     * 차단된 정책이면 FORBIDDEN 예외가 발생해야 한다.
     */
    @Test
    fun ensurePublishable_throwsWhenPolicyDenied() {
        val blockedPolicy = PublishPolicy(
            region = "US",
            language = "en",
            status = PublishPolicyStatus.LOCKED,
            availableFrom = null,
            availableUntil = null,
            lockedReason = "운영 차단"
        )
        val repository = RecordingPublishPolicyRepository(blockedPolicy)
        val service = PublishPolicyService(repository)
        val now = Instant.parse("2024-01-01T00:00:00Z")

        // 실행/검증: 정책이 차단되면 예외가 발생한다.
        val exception = assertThrows(BusinessException::class.java) {
            service.ensurePublishable("us", "EN", now)
        }

        assertEquals(ErrorCode.FORBIDDEN, exception.errorCode)
        assertNotNull(exception.details["reason"])
        assertEquals("US", exception.details["region"])
        assertEquals("en", exception.details["language"])
    }

    /**
     * PublishPolicy 조회 요청을 기록하는 테스트용 저장소.
     */
    private class RecordingPublishPolicyRepository(
        private val policy: PublishPolicy?
    ) : PublishPolicyRepository {
        var lastRegion: String? = null
        var lastLanguage: String? = null

        /**
         * 조회 파라미터를 기록하고 고정된 정책을 반환한다.
         */
        override fun findPolicy(region: String, language: String): PublishPolicy? {
            lastRegion = region
            lastLanguage = language
            return policy
        }
    }
}
