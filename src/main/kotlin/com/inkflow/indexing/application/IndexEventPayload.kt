package com.inkflow.indexing.application

import com.inkflow.common.events.EventType
import com.inkflow.indexing.domain.IndexEntityType
import com.inkflow.indexing.domain.IndexOperation

/**
 * Index 이벤트 타입 정의.
 */
object IndexEventTypes {
    /**
     * 색인 요청 이벤트 타입.
     */
    val INDEX_REQUESTED: EventType = EventType.of("INDEX_REQUESTED", 1)
}

/**
 * 색인 요청 이벤트 payload.
 */
data class IndexRequestedEventPayload(
    val entityType: IndexEntityType,
    val entityId: Long,
    val operation: IndexOperation
) {
    init {
        // 색인 요청 payload의 기본 유효성을 검증한다.
        require(entityId > 0) { "entityId는 0보다 커야 합니다." }
    }
}
