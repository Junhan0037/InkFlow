package com.inkflow.media.application

import com.inkflow.common.events.EventType

/**
 * Media 작업 이벤트 타입 정의.
 */
object MediaJobEventTypes {
    /**
     * Media 작업 생성 이벤트 타입.
     */
    val MEDIA_JOB_CREATED: EventType = EventType.of("MEDIA_JOB_CREATED", 1)
}

/**
 * Media 작업 생성 이벤트 payload.
 */
data class MediaJobCreatedEventPayload(
    val jobId: String,
    val assetId: Long,
    val derivativeType: String,
    val spec: MediaJobSpec
) {
    init {
        require(jobId.isNotBlank()) { "jobId는 비어 있을 수 없습니다." }
        require(assetId > 0) { "assetId는 0보다 커야 합니다." }
        require(derivativeType.isNotBlank()) { "derivativeType은 비어 있을 수 없습니다." }
    }
}

/**
 * Media 파생 작업 스펙 DTO.
 */
data class MediaJobSpec(
    val width: Int,
    val height: Int,
    val format: String
) {
    init {
        require(width > 0) { "width는 0보다 커야 합니다." }
        require(height > 0) { "height는 0보다 커야 합니다." }
        require(format.isNotBlank()) { "format은 비어 있을 수 없습니다." }
    }
}

/**
 * Media 작업 처리 요청 DTO.
 */
data class MediaJobCommand(
    val jobId: String,
    val assetId: Long,
    val derivativeType: String,
    val spec: MediaJobSpec
) {
    init {
        require(jobId.isNotBlank()) { "jobId는 비어 있을 수 없습니다." }
        require(assetId > 0) { "assetId는 0보다 커야 합니다." }
        require(derivativeType.isNotBlank()) { "derivativeType은 비어 있을 수 없습니다." }
    }

    companion object {
        /**
         * 이벤트 payload를 Media 작업 명령으로 변환한다.
         */
        fun from(payload: MediaJobCreatedEventPayload): MediaJobCommand {
            return MediaJobCommand(
                jobId = payload.jobId,
                assetId = payload.assetId,
                derivativeType = payload.derivativeType,
                spec = payload.spec
            )
        }
    }
}
