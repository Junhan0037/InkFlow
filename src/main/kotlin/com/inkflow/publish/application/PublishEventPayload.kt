package com.inkflow.publish.application

import com.inkflow.common.events.EventType
import java.time.Instant

/**
 * 퍼블리시 도메인 이벤트 타입 모음.
 */
object PublishEventTypes {
    /**
     * 퍼블리시 스냅샷 생성 이벤트 타입.
     */
    val PUBLISH_SNAPSHOT_CREATED: EventType = EventType.of("PUBLISH_SNAPSHOT_CREATED", 1)

    /**
     * 퍼블리시 롤백 완료 이벤트 타입.
     */
    val PUBLISH_SNAPSHOT_ROLLED_BACK: EventType = EventType.of("PUBLISH_SNAPSHOT_ROLLED_BACK", 1)
}

/**
 * 퍼블리시 스냅샷 생성 이벤트 payload.
 */
data class PublishSnapshotCreatedEventPayload(
    val episodeId: Long,
    val snapshotId: String,
    val publishVersion: Long,
    val region: String,
    val language: String,
    val occurredAt: Instant
)

/**
 * 퍼블리시 롤백 완료 이벤트 payload.
 */
data class PublishSnapshotRolledBackEventPayload(
    val episodeId: Long,
    val targetVersion: Long,
    val previousVersion: Long,
    val targetSnapshotId: String,
    val rolledBackSnapshotId: String,
    val occurredAt: Instant
)
