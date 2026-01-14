package com.inkflow.workflow.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.events.EventObjectMapperFactory
import com.inkflow.common.events.JacksonEventSerializer
import com.inkflow.common.outbox.domain.OutboxEvent
import com.inkflow.common.outbox.domain.OutboxEventRepository
import com.inkflow.workflow.domain.EpisodeQueryRepository
import com.inkflow.workflow.domain.WorkflowAuditLog
import com.inkflow.workflow.domain.WorkflowAuditLogRepository
import com.inkflow.workflow.domain.WorkflowState
import com.inkflow.workflow.domain.WorkflowStateRepository
import com.inkflow.workflow.domain.WorkflowStatus
import com.inkflow.workflow.domain.WorkflowTransitionAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * 워크플로우 애플리케이션 서비스의 핵심 동작을 검증한다.
 */
class WorkflowApplicationServiceTest {
    private val baseTime: Instant = Instant.parse("2024-01-01T00:00:00Z")
    private val clock: Clock = Clock.fixed(baseTime, ZoneOffset.UTC)
    private val eventSerializer = JacksonEventSerializer(EventObjectMapperFactory.defaultObjectMapper())

    /**
     * 제출 전이가 상태 저장, 감사 로그, Outbox 이벤트로 반영되는지 확인한다.
     */
    @Test
    fun submit_recordsAuditLogAndOutbox() {
        // 준비: 워크플로우 상태와 조회 저장소를 초기화한다.
        val stateRepository = InMemoryWorkflowStateRepository()
        val auditRepository = InMemoryWorkflowAuditLogRepository()
        val episodeQueryRepository = InMemoryEpisodeQueryRepository(mapOf(1L to 10L))
        val outboxRepository = InMemoryOutboxEventRepository()
        val service = buildService(
            workflowStateRepository = stateRepository,
            workflowAuditLogRepository = auditRepository,
            episodeQueryRepository = episodeQueryRepository,
            outboxEventRepository = outboxRepository
        )
        stateRepository.save(WorkflowState.createDraft(episodeId = 1L, now = baseTime))

        // 실행: 제출 요청을 수행한다.
        val command = SubmitEpisodeCommand(
            episodeId = 1L,
            submitterId = "creator-1",
            deadline = Instant.parse("2024-01-02T00:00:00Z")
        )
        val result = service.submit(command)

        // 검증: 상태 전이 및 감사 로그 기록을 확인한다.
        val stored = stateRepository.findByEpisodeId(1L)
        assertNotNull(stored)
        assertEquals(WorkflowStatus.SUBMITTED, stored!!.state)
        assertEquals(2, stored.version)
        assertEquals(WorkflowStatus.SUBMITTED, result.state)
        assertEquals(2, result.version)
        assertEquals(1, auditRepository.logs.size)
        val auditLog = auditRepository.logs.first()
        assertEquals(WorkflowTransitionAction.SUBMIT, auditLog.action)
        assertEquals(WorkflowStatus.DRAFT, auditLog.fromState)
        assertEquals(WorkflowStatus.SUBMITTED, auditLog.toState)
        assertEquals(1, auditLog.fromVersion)
        assertEquals(2, auditLog.toVersion)

        // 검증: Outbox 이벤트에 제출 payload가 기록되는지 확인한다.
        assertEquals(1, outboxRepository.events.size)
        val outboxEvent = outboxRepository.events.first()
        assertEquals("EPISODE", outboxEvent.aggregateType)
        assertEquals("1", outboxEvent.aggregateId)
        assertEquals(WorkflowEventTypes.EPISODE_SUBMITTED.asString(), outboxEvent.eventType)
        val envelope = eventSerializer.deserialize(
            outboxEvent.payload.toByteArray(),
            EpisodeSubmittedEventPayload::class.java
        )
        assertEquals("workflow-orchestrator", envelope.producer)
        assertEquals("1:2", envelope.idempotencyKey)
        assertEquals(10L, envelope.payload.workId)
        assertEquals("creator-1", envelope.payload.submitterId)
    }

    /**
     * 검수 시작 전이가 상태/감사 로그에 반영되는지 확인한다.
     */
    @Test
    fun startReview_updatesStateAndAuditLog() {
        // 준비: SUBMITTED 상태를 저장한다.
        val stateRepository = InMemoryWorkflowStateRepository()
        val auditRepository = InMemoryWorkflowAuditLogRepository()
        val outboxRepository = InMemoryOutboxEventRepository()
        val service = buildService(
            workflowStateRepository = stateRepository,
            workflowAuditLogRepository = auditRepository,
            outboxEventRepository = outboxRepository
        )
        stateRepository.save(
            WorkflowState(
                episodeId = 1L,
                state = WorkflowStatus.SUBMITTED,
                version = 2,
                updatedAt = baseTime
            )
        )

        // 실행: 검수 시작 요청을 수행한다.
        val command = StartReviewCommand(episodeId = 1L, reviewerId = "reviewer-1")
        val result = service.startReview(command)

        // 검증: 상태가 REVIEWING으로 변경되고 Outbox는 생성되지 않는다.
        val stored = stateRepository.findByEpisodeId(1L)
        assertEquals(WorkflowStatus.REVIEWING, stored!!.state)
        assertEquals(3, stored.version)
        assertEquals(WorkflowStatus.REVIEWING, result.state)
        assertEquals(3, result.version)
        assertEquals(1, auditRepository.logs.size)
        assertEquals(0, outboxRepository.events.size)
    }

    /**
     * 승인 전이가 Outbox 이벤트와 함께 기록되는지 확인한다.
     */
    @Test
    fun approve_recordsAuditLogAndOutbox() {
        // 준비: REVIEWING 상태를 저장한다.
        val stateRepository = InMemoryWorkflowStateRepository()
        val auditRepository = InMemoryWorkflowAuditLogRepository()
        val outboxRepository = InMemoryOutboxEventRepository()
        val service = buildService(
            workflowStateRepository = stateRepository,
            workflowAuditLogRepository = auditRepository,
            outboxEventRepository = outboxRepository
        )
        stateRepository.save(
            WorkflowState(
                episodeId = 1L,
                state = WorkflowStatus.REVIEWING,
                version = 2,
                updatedAt = baseTime
            )
        )

        // 실행: 승인 요청을 수행한다.
        val command = ApproveEpisodeCommand(
            episodeId = 1L,
            reviewerId = "reviewer-1",
            comment = "승인합니다."
        )
        val result = service.approve(command)

        // 검증: 승인 상태와 감사 로그를 확인한다.
        val stored = stateRepository.findByEpisodeId(1L)
        assertEquals(WorkflowStatus.APPROVED, stored!!.state)
        assertEquals(3, stored.version)
        assertEquals(WorkflowStatus.APPROVED, result.state)
        assertEquals(1, auditRepository.logs.size)

        // 검증: 승인 이벤트가 Outbox에 기록된다.
        assertEquals(1, outboxRepository.events.size)
        val outboxEvent = outboxRepository.events.first()
        assertEquals(WorkflowEventTypes.EPISODE_APPROVED.asString(), outboxEvent.eventType)
        val envelope = eventSerializer.deserialize(
            outboxEvent.payload.toByteArray(),
            EpisodeApprovedEventPayload::class.java
        )
        assertEquals("reviewer-1", envelope.payload.reviewerId)
        assertEquals("1:3", envelope.idempotencyKey)
    }

    /**
     * 반려 전이가 감사 로그에만 기록되는지 확인한다.
     */
    @Test
    fun reject_recordsAuditLogWithoutOutbox() {
        // 준비: REVIEWING 상태를 저장한다.
        val stateRepository = InMemoryWorkflowStateRepository()
        val auditRepository = InMemoryWorkflowAuditLogRepository()
        val outboxRepository = InMemoryOutboxEventRepository()
        val service = buildService(
            workflowStateRepository = stateRepository,
            workflowAuditLogRepository = auditRepository,
            outboxEventRepository = outboxRepository
        )
        stateRepository.save(
            WorkflowState(
                episodeId = 1L,
                state = WorkflowStatus.REVIEWING,
                version = 3,
                updatedAt = baseTime
            )
        )

        // 실행: 반려 요청을 수행한다.
        val command = RejectEpisodeCommand(
            episodeId = 1L,
            reviewerId = "reviewer-1",
            reason = "정책 위반"
        )
        val result = service.reject(command)

        // 검증: 반려 상태로 전이되고 Outbox는 생성되지 않는다.
        val stored = stateRepository.findByEpisodeId(1L)
        assertEquals(WorkflowStatus.REJECTED, stored!!.state)
        assertEquals(4, stored.version)
        assertEquals(WorkflowStatus.REJECTED, result.state)
        assertEquals(1, auditRepository.logs.size)
        assertEquals(0, outboxRepository.events.size)
    }

    /**
     * 허용되지 않은 전이는 INVALID_STATE로 변환된다.
     */
    @Test
    fun startReview_rejectsInvalidState() {
        // 준비: DRAFT 상태를 저장한다.
        val stateRepository = InMemoryWorkflowStateRepository()
        val service = buildService(workflowStateRepository = stateRepository)
        stateRepository.save(WorkflowState.createDraft(episodeId = 1L, now = baseTime))

        // 실행/검증: 검수 시작 전이가 실패한다.
        val exception = assertThrows(BusinessException::class.java) {
            service.startReview(StartReviewCommand(episodeId = 1L, reviewerId = "reviewer-1"))
        }
        assertEquals(ErrorCode.INVALID_STATE, exception.errorCode)
    }

    /**
     * 제출 마감이 과거이면 INVALID_REQUEST가 발생한다.
     */
    @Test
    fun submit_rejectsPastDeadline() {
        // 준비: DRAFT 상태와 서비스 구성을 생성한다.
        val stateRepository = InMemoryWorkflowStateRepository()
        val service = buildService(workflowStateRepository = stateRepository)
        stateRepository.save(WorkflowState.createDraft(episodeId = 1L, now = baseTime))

        // 실행/검증: 과거 deadline이면 예외가 발생한다.
        val exception = assertThrows(BusinessException::class.java) {
            service.submit(
                SubmitEpisodeCommand(
                    episodeId = 1L,
                    submitterId = "creator-1",
                    deadline = baseTime.minusSeconds(60)
                )
            )
        }
        assertEquals(ErrorCode.INVALID_REQUEST, exception.errorCode)
    }

    /**
     * 테스트용 워크플로우 서비스 구성을 생성한다.
     */
    private fun buildService(
        workflowStateRepository: WorkflowStateRepository = InMemoryWorkflowStateRepository(),
        workflowAuditLogRepository: WorkflowAuditLogRepository = InMemoryWorkflowAuditLogRepository(),
        episodeQueryRepository: EpisodeQueryRepository = InMemoryEpisodeQueryRepository(mapOf(1L to 10L)),
        outboxEventRepository: OutboxEventRepository = InMemoryOutboxEventRepository(),
        objectMapper: ObjectMapper = EventObjectMapperFactory.defaultObjectMapper(),
        clock: Clock = this.clock
    ): WorkflowApplicationService {
        return WorkflowApplicationService(
            workflowStateRepository = workflowStateRepository,
            workflowAuditLogRepository = workflowAuditLogRepository,
            episodeQueryRepository = episodeQueryRepository,
            outboxEventRepository = outboxEventRepository,
            objectMapper = objectMapper,
            clock = clock
        )
    }

    /**
     * 인메모리 워크플로우 상태 저장소.
     */
    private class InMemoryWorkflowStateRepository : WorkflowStateRepository {
        private val store = mutableMapOf<Long, WorkflowState>()

        /**
         * 워크플로우 상태를 저장한다.
         */
        override fun save(state: WorkflowState): WorkflowState {
            store[state.episodeId] = state
            return state
        }

        /**
         * 에피소드 ID로 워크플로우 상태를 조회한다.
         */
        override fun findByEpisodeId(episodeId: Long): WorkflowState? {
            return store[episodeId]
        }
    }

    /**
     * 인메모리 감사 로그 저장소.
     */
    private class InMemoryWorkflowAuditLogRepository : WorkflowAuditLogRepository {
        val logs = mutableListOf<WorkflowAuditLog>()

        /**
         * 감사 로그를 저장한다.
         */
        override fun save(log: WorkflowAuditLog): WorkflowAuditLog {
            logs.add(log)
            return log
        }
    }

    /**
     * 인메모리 에피소드 조회 저장소.
     */
    private class InMemoryEpisodeQueryRepository(
        private val mapping: Map<Long, Long>
    ) : EpisodeQueryRepository {
        /**
         * 에피소드 ID로 workId를 조회한다.
         */
        override fun findWorkIdByEpisodeId(episodeId: Long): Long? {
            return mapping[episodeId]
        }
    }

    /**
     * 인메모리 Outbox 이벤트 저장소.
     */
    private class InMemoryOutboxEventRepository : OutboxEventRepository {
        val events = mutableListOf<OutboxEvent>()

        /**
         * Outbox 이벤트를 저장한다.
         */
        override fun save(event: OutboxEvent): OutboxEvent {
            events.add(event)
            return event
        }

        /**
         * 테스트에서는 Relay 처리를 하지 않으므로 빈 목록을 반환한다.
         */
        override fun findPendingEventsForUpdate(
            limit: Int,
            now: Instant,
            lockExpiredBefore: Instant
        ): List<OutboxEvent> {
            return emptyList()
        }

        /**
         * 테스트 범위에서 사용하지 않는 전송 중 갱신 동작.
         */
        override fun markSending(eventId: java.util.UUID, lockedAt: Instant) {
            // 워크플로우 서비스 테스트에서는 전송 상태를 다루지 않는다.
        }

        /**
         * 테스트 범위에서 사용하지 않는 전송 완료 갱신 동작.
         */
        override fun markSent(eventId: java.util.UUID, sentAt: Instant) {
            // 워크플로우 서비스 테스트에서는 전송 상태를 다루지 않는다.
        }

        /**
         * 테스트 범위에서 사용하지 않는 재시도 갱신 동작.
         */
        override fun markRetry(
            eventId: java.util.UUID,
            retryCount: Int,
            nextRetryAt: Instant,
            lastError: String?
        ) {
            // 워크플로우 서비스 테스트에서는 재시도 상태를 다루지 않는다.
        }

        /**
         * 테스트 범위에서 사용하지 않는 실패 갱신 동작.
         */
        override fun markFailed(eventId: java.util.UUID, lastError: String?) {
            // 워크플로우 서비스 테스트에서는 실패 상태를 다루지 않는다.
        }
    }
}
