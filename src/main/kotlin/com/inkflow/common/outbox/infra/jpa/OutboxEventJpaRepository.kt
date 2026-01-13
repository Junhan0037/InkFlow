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
            WHERE (
                status = :pendingStatus
                OR (
                    status = :sendingStatus
                    AND (locked_at IS NULL OR locked_at <= :lockExpiredBefore)
                )
            )
              AND (next_retry_at IS NULL OR next_retry_at <= :now)
            ORDER BY COALESCE(next_retry_at, created_at) ASC, created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    fun findPendingForUpdate(
        @Param("pendingStatus") pendingStatus: String,
        @Param("sendingStatus") sendingStatus: String,
        @Param("now") now: Instant,
        @Param("lockExpiredBefore") lockExpiredBefore: Instant,
        @Param("limit") limit: Int
    ): List<OutboxEventEntity>

    /**
     * 이벤트를 전송 중 상태로 잠근다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            UPDATE outbox_event
            SET status = :status,
                locked_at = :lockedAt,
                last_error = NULL
            WHERE id = :id
        """,
        nativeQuery = true
    )
    fun updateSending(
        @Param("id") id: UUID,
        @Param("status") status: String,
        @Param("lockedAt") lockedAt: Instant
    ): Int

    /**
     * 이벤트 상태와 전송 시간을 갱신한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            UPDATE outbox_event
            SET status = :status,
                sent_at = :sentAt,
                next_retry_at = NULL,
                locked_at = NULL,
                last_error = NULL
            WHERE id = :id
        """,
        nativeQuery = true
    )
    fun updateSent(
        @Param("id") id: UUID,
        @Param("status") status: String,
        @Param("sentAt") sentAt: Instant?
    ): Int

    /**
     * 이벤트를 재시도 대상으로 갱신한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            UPDATE outbox_event
            SET status = :status,
                retry_count = :retryCount,
                next_retry_at = :nextRetryAt,
                locked_at = NULL,
                last_error = :lastError
            WHERE id = :id
        """,
        nativeQuery = true
    )
    fun updateForRetry(
        @Param("id") id: UUID,
        @Param("status") status: String,
        @Param("retryCount") retryCount: Int,
        @Param("nextRetryAt") nextRetryAt: Instant,
        @Param("lastError") lastError: String?
    ): Int

    /**
     * 이벤트를 실패 상태로 갱신한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            UPDATE outbox_event
            SET status = :status,
                next_retry_at = NULL,
                locked_at = NULL,
                last_error = :lastError
            WHERE id = :id
        """,
        nativeQuery = true
    )
    fun updateFailed(
        @Param("id") id: UUID,
        @Param("status") status: String,
        @Param("lastError") lastError: String?
    ): Int
}
