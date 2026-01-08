package com.inkflow.upload.application

import com.inkflow.common.events.EventType
import com.inkflow.upload.domain.AssetStatus
import java.time.Instant

/**
 * 업로드 도메인 이벤트 타입 모음.
 */
object UploadEventTypes {
    /**
     * Asset 저장 완료 이벤트 타입.
     */
    val ASSET_STORED: EventType = EventType.of("ASSET_STORED", 1)
}

/**
 * Asset 저장 완료 이벤트 payload.
 */
data class AssetStoredEventPayload(
    val assetId: Long,
    val uploadId: String,
    val episodeId: Long,
    val creatorId: String,
    val fileName: String,
    val contentType: String,
    val size: Long,
    val checksum: String,
    val storageBucket: String,
    val storageKey: String,
    val status: AssetStatus,
    val occurredAt: Instant
)
