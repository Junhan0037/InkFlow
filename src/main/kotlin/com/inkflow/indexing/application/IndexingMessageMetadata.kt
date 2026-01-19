package com.inkflow.indexing.application

import java.util.UUID

/**
 * 색인 이벤트 메시지 메타데이터.
 */
data class IndexingMessageMetadata(
    val eventId: UUID,
    val traceId: String?,
    val idempotencyKey: String?
) {
    init {
        // eventId는 필수 값이므로 검증한다.
        require(eventId.toString().isNotBlank()) { "eventId는 비어 있을 수 없습니다." }
        if (traceId != null) {
            require(traceId.isNotBlank()) { "traceId는 빈 문자열일 수 없습니다." }
        }
        if (idempotencyKey != null) {
            require(idempotencyKey.isNotBlank()) { "idempotencyKey는 빈 문자열일 수 없습니다." }
        }
    }
}
