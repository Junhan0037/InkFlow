package com.inkflow.metadata.application

import com.inkflow.common.events.EventType
import java.time.Instant

/**
 * 메타 자동 생성 관련 이벤트 타입 정의.
 */
object MetadataEventTypes {
    // 자동 생성 제안 생성 이벤트 타입.
    val EPISODE_META_SUGGESTED: EventType = EventType.of("EPISODE_META_SUGGESTED", 1)
    // 메타 승인 완료 이벤트 타입.
    val EPISODE_META_APPROVED: EventType = EventType.of("EPISODE_META_APPROVED", 1)
}

/**
 * 메타 자동 생성 제안 이벤트 payload.
 */
data class EpisodeMetaSuggestedEventPayload(
    val episodeId: Long,
    val suggestionId: Long,
    val requesterId: String,
    val generatedAt: Instant
)

/**
 * 메타 승인 완료 이벤트 payload.
 */
data class EpisodeMetaApprovedEventPayload(
    val episodeId: Long,
    val metadataVersion: Int,
    val approvedBy: String,
    val approvedAt: Instant
)
