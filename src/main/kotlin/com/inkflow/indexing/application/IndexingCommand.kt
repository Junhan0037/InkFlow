package com.inkflow.indexing.application

import com.inkflow.indexing.domain.IndexEntityType
import com.inkflow.indexing.domain.IndexOperation

/**
 * 색인 처리 명령 DTO.
 */
data class IndexingCommand(
    val entityType: IndexEntityType,
    val entityId: Long,
    val operation: IndexOperation
) {
    init {
        // 색인 처리에 필요한 식별자를 검증한다.
        require(entityId > 0) { "entityId는 0보다 커야 합니다." }
    }

    companion object {
        /**
         * 이벤트 payload를 색인 처리 명령으로 변환한다.
         */
        fun from(payload: IndexRequestedEventPayload): IndexingCommand {
            return IndexingCommand(
                entityType = payload.entityType,
                entityId = payload.entityId,
                operation = payload.operation
            )
        }
    }
}
