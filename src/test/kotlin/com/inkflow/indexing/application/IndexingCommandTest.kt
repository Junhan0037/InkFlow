package com.inkflow.indexing.application

import com.inkflow.indexing.domain.IndexEntityType
import com.inkflow.indexing.domain.IndexOperation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * IndexingCommand의 생성 규칙을 검증한다.
 */
class IndexingCommandTest {
    /**
     * payload에서 명령으로 변환되는지 확인한다.
     */
    @Test
    fun from_buildsCommand() {
        val payload = IndexRequestedEventPayload(
            entityType = IndexEntityType.WORK,
            entityId = 10L,
            operation = IndexOperation.UPSERT
        )

        val command = IndexingCommand.from(payload)

        assertEquals(IndexEntityType.WORK, command.entityType)
        assertEquals(10L, command.entityId)
        assertEquals(IndexOperation.UPSERT, command.operation)
    }

    /**
     * 잘못된 식별자는 예외를 반환하는지 확인한다.
     */
    @Test
    fun create_throwsWhenEntityIdInvalid() {
        assertThrows(IllegalArgumentException::class.java) {
            IndexingCommand(
                entityType = IndexEntityType.ASSET,
                entityId = 0L,
                operation = IndexOperation.DELETE
            )
        }
    }
}
