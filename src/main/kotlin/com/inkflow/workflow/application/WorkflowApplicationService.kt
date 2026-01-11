package com.inkflow.workflow.application

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.workflow.domain.WorkflowState
import com.inkflow.workflow.domain.WorkflowStateRepository
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
    private val clock: Clock
) {
    /**
     * DRAFT → SUBMITTED 전이를 처리한다.
     */
    @Transactional
    fun submit(command: SubmitEpisodeCommand): WorkflowTransitionResult {
        validateEpisodeId(command.episodeId)
        validateActor("submitterId", command.submitterId)

        val now = Instant.now(clock)
        validateDeadline(command.deadline, now)
        val updatedState = transition(command.episodeId) { state -> state.submit(now) }
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
        val updatedState = transition(command.episodeId) { state -> state.startReview(now) }
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
        val updatedState = transition(command.episodeId) { state -> state.approve(now) }
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
        val updatedState = transition(command.episodeId) { state -> state.reject(now) }
        return updatedState.toResult()
    }

    /**
     * 워크플로우 상태를 조회하고 전이 결과를 저장한다.
     */
    private fun transition(
        episodeId: Long,
        transition: (WorkflowState) -> WorkflowState
    ): WorkflowState {
        val current = loadState(episodeId)
        val updated = try {
            transition(current)
        } catch (exception: IllegalArgumentException) {
            // 도메인 전이 규칙 위반은 비즈니스 예외로 변환한다.
            throw invalidState(exception.message ?: "허용되지 않은 전이입니다.")
        }
        return workflowStateRepository.save(updated)
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
