package com.inkflow.workflow.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * 워크플로우 상태 도메인 전이 규칙을 검증한다.
 */
class WorkflowStateTest {
    /**
     * DRAFT 상태 생성 시 기본 값이 올바르게 설정되는지 확인한다.
     */
    @Test
    fun createDraft_initializesState() {
        val now = Instant.parse("2026-01-01T00:00:00Z")

        val state = WorkflowState.createDraft(episodeId = 1L, now = now)

        assertEquals(WorkflowStatus.DRAFT, state.state)
        assertEquals(1, state.version)
        assertEquals(now, state.updatedAt)
    }

    /**
     * 제출 전이가 정상적으로 동작하고 버전이 증가한다.
     */
    @Test
    fun submit_transitionsToSubmitted() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val state = WorkflowState.createDraft(episodeId = 1L, now = now)

        val updated = state.submit(now.plusSeconds(60))

        assertEquals(WorkflowStatus.SUBMITTED, updated.state)
        assertEquals(2, updated.version)
    }

    /**
     * SUBMITTED가 아니면 검수 시작 전이가 거부된다.
     */
    @Test
    fun startReview_rejectsWhenNotSubmitted() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val state = WorkflowState.createDraft(episodeId = 1L, now = now)

        assertThrows(IllegalArgumentException::class.java) {
            state.startReview(now.plusSeconds(60))
        }
    }

    /**
     * REVIEWING 상태에서 승인 전이가 정상 동작한다.
     */
    @Test
    fun approve_transitionsToApproved() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val reviewing = WorkflowState(
            episodeId = 1L,
            state = WorkflowStatus.REVIEWING,
            version = 3,
            updatedAt = now
        )

        val updated = reviewing.approve(now.plusSeconds(60))

        assertEquals(WorkflowStatus.APPROVED, updated.state)
        assertEquals(4, updated.version)
    }

    /**
     * REVIEWING 상태에서 반려 전이가 정상 동작한다.
     */
    @Test
    fun reject_transitionsToRejected() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val reviewing = WorkflowState(
            episodeId = 1L,
            state = WorkflowStatus.REVIEWING,
            version = 3,
            updatedAt = now
        )

        val updated = reviewing.reject(now.plusSeconds(60))

        assertEquals(WorkflowStatus.REJECTED, updated.state)
        assertEquals(4, updated.version)
    }
}
