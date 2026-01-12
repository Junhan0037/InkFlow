package com.inkflow.workflow.application

import com.inkflow.common.events.EventType
import java.time.Instant

/**
 * 워크플로우 이벤트 타입 정의.
 */
object WorkflowEventTypes {
    // 에피소드 제출 이벤트 타입.
    val EPISODE_SUBMITTED: EventType = EventType.of("EPISODE_SUBMITTED", 1)
    //에피소드 승인 이벤트 타입.
    val EPISODE_APPROVED: EventType = EventType.of("EPISODE_APPROVED", 1)
}

/**
 * 에피소드 제출 이벤트 payload.
 */
data class EpisodeSubmittedEventPayload(
    val episodeId: Long,
    val workId: Long,
    val submitterId: String,
    val deadline: Instant
)

/**
 * 에피소드 승인 이벤트 payload.
 */
data class EpisodeApprovedEventPayload(
    val episodeId: Long,
    val reviewerId: String,
    val approvedAt: Instant
)
