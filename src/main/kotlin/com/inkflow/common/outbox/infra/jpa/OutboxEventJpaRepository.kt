package com.inkflow.common.outbox.infra.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/**
 * Outbox 이벤트를 조회하기 위한 JPA Repository.
 */
interface OutboxEventJpaRepository : JpaRepository<OutboxEventEntity, UUID> {
    /**
     * 전송 대기 이벤트를 조회하면서 행 잠금을 획득해 중복 발행을 방지한다.
     */
    @Query(
        value = """
            SELECT *
            FROM outbox_event
            WHERE status = :status
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun findPendingForUpdate(
        @Param("status") status: String,
        @Param("limit") limit: Int
    ): List<OutboxEventEntity>

    /**
     * 이벤트 상태와 전송 시간을 갱신한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            UPDATE outbox_event
            SET status = :status,
                sent_at = :sentAt
            WHERE id = :id
        """,
        nativeQuery = true
    )
    fun updateStatus(
        @Param("id") id: UUID,
        @Param("status") status: String,
        @Param("sentAt") sentAt: Instant?
    ): Int
}
