package com.inkflow.common.outbox.infra.publisher

import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.common.kafka.config.InkflowKafkaProperties
import com.inkflow.common.outbox.domain.OutboxEvent
import java.util.Locale

/**
 * Outbox 이벤트를 도메인 토픽으로 라우팅하는 규칙을 정의한다.
 */
class OutboxEventTopicResolver(
    private val properties: InkflowKafkaProperties
) {
    /**
     * 이벤트 타입 규칙에 맞는 Kafka 토픽을 결정한다.
     */
    fun resolveTopic(event: OutboxEvent): String {
        val baseEventType = normalizeEventType(event.eventType)
        return when {
            baseEventType.startsWith("ASSET_") -> properties.topics.assetEvents
            baseEventType.startsWith("EPISODE_") || baseEventType.startsWith("PUBLISH_") -> {
                properties.topics.workflowEvents
            }
            baseEventType.startsWith("MEDIA_JOB_") && baseEventType.endsWith("CREATED") -> {
                properties.topics.mediaJobs
            }
            baseEventType.startsWith("MEDIA_JOB_") -> properties.topics.mediaResults
            baseEventType.startsWith("INDEX_") -> properties.topics.indexEvents
            else -> throw SystemException(
                errorCode = ErrorCode.INTERNAL_ERROR,
                details = mapOf(
                    "eventId" to event.id.toString(),
                    "eventType" to event.eventType,
                    "aggregateType" to event.aggregateType
                ),
                message = "Outbox 이벤트에 매핑되는 Kafka 토픽을 찾을 수 없습니다."
            )
        }
    }

    /**
     * 파티션 정렬을 위해 aggregateId를 메시지 키로 사용한다.
     */
    fun resolveKey(event: OutboxEvent): String {
        return event.aggregateId
    }

    /**
     * `ASSET_STORED.v1`처럼 버전을 포함한 타입에서 기본 타입을 추출한다.
     */
    private fun normalizeEventType(eventType: String): String {
        return eventType.substringBefore('.').uppercase(Locale.ROOT)
    }
}
