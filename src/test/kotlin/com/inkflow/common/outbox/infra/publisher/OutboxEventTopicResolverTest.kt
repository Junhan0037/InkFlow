package com.inkflow.common.outbox.infra.publisher

import com.inkflow.common.error.SystemException
import com.inkflow.common.kafka.config.InkflowKafkaProperties
import com.inkflow.common.outbox.domain.OutboxEvent
import com.inkflow.common.outbox.domain.OutboxEventStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Outbox 이벤트 토픽 라우팅 규칙을 검증한다.
 */
class OutboxEventTopicResolverTest {
    /**
     * 이벤트 타입 접두어별 토픽이 올바르게 매핑되는지 확인한다.
     */
    @Test
    fun resolveTopic_routesByPrefix() {
        // 준비: 커스텀 토픽 설정을 구성한다.
        val properties = InkflowKafkaProperties(
            topics = InkflowKafkaProperties.Topics(
                assetEvents = "asset-topic",
                workflowEvents = "workflow-topic",
                mediaJobs = "media-jobs-topic",
                mediaResults = "media-results-topic",
                indexEvents = "index-topic"
            )
        )
        val resolver = OutboxEventTopicResolver(properties)

        // 실행/검증: 각 이벤트 타입이 올바른 토픽으로 라우팅된다.
        assertEquals("asset-topic", resolver.resolveTopic(buildEvent("asset_stored.v1")))
        assertEquals("workflow-topic", resolver.resolveTopic(buildEvent("episode_submitted.v1")))
        assertEquals("workflow-topic", resolver.resolveTopic(buildEvent("publish_created.v1")))
        assertEquals("media-jobs-topic", resolver.resolveTopic(buildEvent("media_job_created.v1")))
        assertEquals("media-results-topic", resolver.resolveTopic(buildEvent("media_job_failed.v1")))
        assertEquals("index-topic", resolver.resolveTopic(buildEvent("index_work_updated.v1")))
    }

    /**
     * 이벤트 키는 aggregateId로 결정되는지 확인한다.
     */
    @Test
    fun resolveKey_returnsAggregateId() {
        // 준비: 기본 토픽 설정을 사용한다.
        val resolver = OutboxEventTopicResolver(InkflowKafkaProperties())
        val event = buildEvent("asset_stored.v1", aggregateId = "asset-99")

        // 실행/검증: aggregateId가 메시지 키로 반환된다.
        assertEquals("asset-99", resolver.resolveKey(event))
    }

    /**
     * 매핑되지 않은 이벤트 타입은 예외를 던지는지 확인한다.
     */
    @Test
    fun resolveTopic_throwsWhenUnknownType() {
        // 준비: 매핑되지 않은 이벤트 타입을 구성한다.
        val resolver = OutboxEventTopicResolver(InkflowKafkaProperties())
        val event = buildEvent("unknown_event.v1")

        // 실행/검증: SystemException이 발생한다.
        assertThrows(SystemException::class.java) {
            resolver.resolveTopic(event)
        }
    }

    /**
     * 테스트용 Outbox 이벤트를 생성한다.
     */
    private fun buildEvent(eventType: String, aggregateId: String = "asset-1"): OutboxEvent {
        return OutboxEvent(
            id = UUID.randomUUID(),
            aggregateType = "ASSET",
            aggregateId = aggregateId,
            eventType = eventType,
            payload = """{"id":"$aggregateId"}""",
            status = OutboxEventStatus.PENDING,
            retryCount = 0,
            nextRetryAt = null,
            lastError = null,
            lockedAt = null,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            sentAt = null
        )
    }
}
