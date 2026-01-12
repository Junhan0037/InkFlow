package com.inkflow.workflow.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.common.events.EventEnvelope
import com.inkflow.common.outbox.domain.OutboxEvent
import com.inkflow.common.outbox.domain.OutboxEventRepository
import com.inkflow.workflow.domain.EpisodeQueryRepository
import com.inkflow.workflow.domain.WorkflowAuditLog
import com.inkflow.workflow.domain.WorkflowAuditLogRepository
import com.inkflow.workflow.domain.WorkflowState
import com.inkflow.workflow.domain.WorkflowStateRepository
import com.inkflow.workflow.domain.WorkflowTransitionAction
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * 워크플로우 상태 전이를 담당하는 애플리케이션 서비스.
 */
@Service
class WorkflowApplicationService(
    private val workflowStateRepository: WorkflowStateRepository,
    private val workflowAuditLogRepository: WorkflowAuditLogRepository,
    private val episodeQueryRepository: EpisodeQueryRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) {
    companion object {
        private const val EVENT_PRODUCER = "workflow-orchestrator" // Outbox 이벤트 producer 식별자.
        private const val AGGREGATE_TYPE_EPISODE = "EPISODE" // 에피소드 이벤트 aggregate 타입.
    }
    /**
     * DRAFT → SUBMITTED 전이를 처리한다.
     */
    @Transactional
    fun submit(command: SubmitEpisodeCommand): WorkflowTransitionResult {
        validateEpisodeId(command.episodeId)
        validateActor("submitterId", command.submitterId)

        val now = Instant.now(clock)
        validateDeadline(command.deadline, now)
        val updatedState = transition(
            episodeId = command.episodeId,
            actorId = command.submitterId,
            action = WorkflowTransitionAction.SUBMIT,
            reason = null,
            comment = null
        ) { state -> state.submit(now) }

        // 제출 이벤트를 Outbox에 기록해 비동기 파이프라인을 시작한다.
        recordEpisodeSubmittedEvent(command, updatedState)
        return updatedState.toResult()
    }

    /**
     * SUBMITTED → REVIEWING 전이를 처리한다.
     */
    @Transactional
    fun startReview(command: StartReviewCommand): WorkflowTransitionResult {
        validateEpisodeId(command.episodeId)
        validateActor("reviewerId", command.reviewerId)

        val now = Instant.now(clock)
        val updatedState = transition(
            episodeId = command.episodeId,
            actorId = command.reviewerId,
            action = WorkflowTransitionAction.START_REVIEW,
            reason = null,
            comment = null
        ) { state -> state.startReview(now) }
        return updatedState.toResult()
    }

    /**
     * REVIEWING → APPROVED 전이를 처리한다.
     */
    @Transactional
    fun approve(command: ApproveEpisodeCommand): WorkflowTransitionResult {
        validateEpisodeId(command.episodeId)
        validateActor("reviewerId", command.reviewerId)

        val now = Instant.now(clock)
        val updatedState = transition(
            episodeId = command.episodeId,
            actorId = command.reviewerId,
            action = WorkflowTransitionAction.APPROVE,
            reason = null,
            comment = command.comment
        ) { state -> state.approve(now) }

        // 승인 이벤트를 Outbox에 기록해 후속 처리를 트리거한다.
        recordEpisodeApprovedEvent(command, updatedState)
        return updatedState.toResult()
    }

    /**
     * REVIEWING → REJECTED 전이를 처리한다.
     */
    @Transactional
    fun reject(command: RejectEpisodeCommand): WorkflowTransitionResult {
        validateEpisodeId(command.episodeId)
        validateActor("reviewerId", command.reviewerId)
        validateReason(command.reason)

        val now = Instant.now(clock)
        val updatedState = transition(
            episodeId = command.episodeId,
            actorId = command.reviewerId,
            action = WorkflowTransitionAction.REJECT,
            reason = command.reason,
            comment = null
        ) { state -> state.reject(now) }
        return updatedState.toResult()
    }

    /**
     * 워크플로우 상태를 조회하고 전이 결과를 저장한다.
     */
    private fun transition(
        episodeId: Long,
        actorId: String,
        action: WorkflowTransitionAction,
        reason: String?,
        comment: String?,
        transition: (WorkflowState) -> WorkflowState
    ): WorkflowState {
        val current = loadState(episodeId)
        val updated = try {
            transition(current)
        } catch (exception: IllegalArgumentException) {
            // 도메인 전이 규칙 위반은 비즈니스 예외로 변환한다.
            throw invalidState(exception.message ?: "허용되지 않은 전이입니다.")
        }
        val saved = workflowStateRepository.save(updated)
        // 상태 전이 감사 로그를 남겨 추적성과 책임성을 확보한다.
        recordAuditLog(current, saved, actorId, action, reason, comment)
        return saved
    }

    /**
     * 워크플로우 상태를 조회하고 없으면 예외를 발생시킨다.
     */
    private fun loadState(episodeId: Long): WorkflowState {
        return workflowStateRepository.findByEpisodeId(episodeId)
            ?: throw BusinessException(
                errorCode = ErrorCode.NOT_FOUND,
                details = mapOf("episodeId" to episodeId.toString()),
                message = "에피소드 워크플로우 상태를 찾을 수 없습니다."
            )
    }

    /**
     * 에피소드 식별자를 검증한다.
     */
    private fun validateEpisodeId(episodeId: Long) {
        if (episodeId <= 0) {
            throw invalid("episodeId", "episodeId는 1 이상이어야 합니다.")
        }
    }

    /**
     * 사용자 식별자를 검증한다.
     */
    private fun validateActor(field: String, actorId: String) {
        if (actorId.isBlank()) {
            throw invalid(field, "$field 값이 비어 있을 수 없습니다.")
        }
    }

    /**
     * 제출 마감 시간을 검증한다.
     */
    private fun validateDeadline(deadline: Instant, now: Instant) {
        if (deadline.isBefore(now)) {
            throw invalid("deadline", "deadline은 현재 시각 이후여야 합니다.")
        }
    }

    /**
     * 반려 사유를 검증한다.
     */
    private fun validateReason(reason: String) {
        if (reason.isBlank()) {
            throw invalid("reason", "reason 값이 비어 있을 수 없습니다.")
        }
    }

    /**
     * 요청 오류를 표준 예외로 변환한다.
     */
    private fun invalid(field: String, message: String): BusinessException {
        return BusinessException(
            errorCode = ErrorCode.INVALID_REQUEST,
            details = mapOf("field" to field),
            message = message
        )
    }

    /**
     * 상태 오류를 표준 예외로 변환한다.
     */
    private fun invalidState(message: String): BusinessException {
        return BusinessException(
            errorCode = ErrorCode.INVALID_STATE,
            message = message
        )
    }

    /**
     * 제출 이벤트를 Outbox에 기록한다.
     */
    private fun recordEpisodeSubmittedEvent(
        command: SubmitEpisodeCommand,
        updatedState: WorkflowState
    ) {
        val workId = episodeQueryRepository.findWorkIdByEpisodeId(updatedState.episodeId)
            ?: throw BusinessException(
                errorCode = ErrorCode.NOT_FOUND,
                details = mapOf("episodeId" to updatedState.episodeId.toString()),
                message = "에피소드 정보를 찾을 수 없습니다."
            )

        val payload = EpisodeSubmittedEventPayload(
            episodeId = updatedState.episodeId,
            workId = workId,
            submitterId = command.submitterId,
            deadline = command.deadline
        )

        val envelope = EventEnvelope.create(
            eventType = WorkflowEventTypes.EPISODE_SUBMITTED,
            producer = EVENT_PRODUCER,
            payload = payload,
            idempotencyKey = buildIdempotencyKey(updatedState),
            occurredAt = updatedState.updatedAt
        )

        val outboxEvent = OutboxEvent.pending(
            aggregateType = AGGREGATE_TYPE_EPISODE,
            aggregateId = updatedState.episodeId.toString(),
            eventType = envelope.eventType.asString(),
            payload = serializeEnvelope(envelope, updatedState.episodeId),
            createdAt = updatedState.updatedAt
        )
        outboxEventRepository.save(outboxEvent)
    }

    /**
     * 승인 이벤트를 Outbox에 기록한다.
     */
    private fun recordEpisodeApprovedEvent(
        command: ApproveEpisodeCommand,
        updatedState: WorkflowState
    ) {
        val payload = EpisodeApprovedEventPayload(
            episodeId = updatedState.episodeId,
            reviewerId = command.reviewerId,
            approvedAt = updatedState.updatedAt
        )

        val envelope = EventEnvelope.create(
            eventType = WorkflowEventTypes.EPISODE_APPROVED,
            producer = EVENT_PRODUCER,
            payload = payload,
            idempotencyKey = buildIdempotencyKey(updatedState),
            occurredAt = updatedState.updatedAt
        )

        val outboxEvent = OutboxEvent.pending(
            aggregateType = AGGREGATE_TYPE_EPISODE,
            aggregateId = updatedState.episodeId.toString(),
            eventType = envelope.eventType.asString(),
            payload = serializeEnvelope(envelope, updatedState.episodeId),
            createdAt = updatedState.updatedAt
        )

        outboxEventRepository.save(outboxEvent)
    }

    /**
     * Outbox payload 직렬화를 공통 처리한다.
     */
    private fun serializeEnvelope(envelope: EventEnvelope<*>, episodeId: Long): String {
        return try {
            objectMapper.writeValueAsString(envelope)
        } catch (exception: Exception) {
            throw SystemException(
                errorCode = ErrorCode.INTERNAL_ERROR,
                details = mapOf("episodeId" to episodeId.toString()),
                message = "Outbox 이벤트 직렬화에 실패했습니다.",
                cause = exception
            )
        }
    }

    /**
     * 워크플로우 전이 중복 처리를 방지하기 위한 키를 생성한다.
     */
    private fun buildIdempotencyKey(state: WorkflowState): String {
        return "${state.episodeId}:${state.version}"
    }

    /**
     * 워크플로우 상태 전이 감사 로그를 저장한다.
     */
    private fun recordAuditLog(
        previous: WorkflowState,
        current: WorkflowState,
        actorId: String,
        action: WorkflowTransitionAction,
        reason: String?,
        comment: String?
    ) {
        val auditLog = WorkflowAuditLog(
            episodeId = current.episodeId,
            actorId = actorId,
            action = action,
            fromState = previous.state,
            toState = current.state,
            fromVersion = previous.version,
            toVersion = current.version,
            reason = reason,
            comment = comment,
            occurredAt = current.updatedAt
        )
        workflowAuditLogRepository.save(auditLog)
    }

    /**
     * 워크플로우 상태를 전이 결과 DTO로 변환한다.
     */
    private fun WorkflowState.toResult(): WorkflowTransitionResult {
        return WorkflowTransitionResult(
            episodeId = episodeId,
            state = state,
            version = version
        )
    }
}
