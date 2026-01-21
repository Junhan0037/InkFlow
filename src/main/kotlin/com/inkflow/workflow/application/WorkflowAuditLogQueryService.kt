package com.inkflow.workflow.application

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.workflow.domain.WorkflowAuditLog
import com.inkflow.workflow.domain.WorkflowAuditLogRepository
import com.inkflow.workflow.domain.WorkflowStatus
import com.inkflow.workflow.domain.WorkflowTransitionAction
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * 운영 콘솔용 워크플로우 감사 로그 조회 서비스.
 */
@Service
class WorkflowAuditLogQueryService(
    private val repository: WorkflowAuditLogRepository
) {
    /**
     * 검색 조건에 맞는 감사 로그를 페이지 단위로 조회한다.
     */
    fun search(criteria: WorkflowAuditLogSearchCriteria): WorkflowAuditLogPage {
        validateSearchCriteria(criteria)
        val pageRequest = PageRequest.of(
            criteria.page,
            criteria.size,
            Sort.by(Sort.Direction.DESC, "occurredAt")
        )
        val page = repository.search(
            episodeId = criteria.episodeId,
            actorId = criteria.actorId,
            action = criteria.action,
            fromState = criteria.fromState,
            toState = criteria.toState,
            occurredFrom = criteria.occurredFrom,
            occurredTo = criteria.occurredTo,
            pageable = pageRequest
        )
        return WorkflowAuditLogPage.from(page)
    }

    /**
     * 검색 조건 파라미터를 검증한다.
     */
    private fun validateSearchCriteria(criteria: WorkflowAuditLogSearchCriteria) {
        if (criteria.page < 0) {
            throw BusinessException(ErrorCode.INVALID_REQUEST, message = "page는 0 이상이어야 합니다.")
        }
        if (criteria.size <= 0 || criteria.size > MAX_PAGE_SIZE) {
            throw BusinessException(
                ErrorCode.INVALID_REQUEST,
                message = "size는 1 이상 ${MAX_PAGE_SIZE} 이하이어야 합니다."
            )
        }
        if (criteria.episodeId != null && criteria.episodeId <= 0) {
            throw BusinessException(ErrorCode.INVALID_REQUEST, message = "episodeId는 1 이상이어야 합니다.")
        }
        if (criteria.actorId != null && criteria.actorId.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_REQUEST, message = "actorId는 비어 있을 수 없습니다.")
        }
        if (criteria.occurredFrom != null && criteria.occurredTo != null &&
            criteria.occurredFrom.isAfter(criteria.occurredTo)
        ) {
            throw BusinessException(
                ErrorCode.INVALID_REQUEST,
                message = "occurredFrom은 occurredTo보다 이후일 수 없습니다."
            )
        }
    }

    companion object {
        private const val MAX_PAGE_SIZE = 200
    }
}

/**
 * 워크플로우 감사 로그 검색 조건을 전달한다.
 */
data class WorkflowAuditLogSearchCriteria(
    val episodeId: Long?,
    val actorId: String?,
    val action: WorkflowTransitionAction?,
    val fromState: WorkflowStatus?,
    val toState: WorkflowStatus?,
    val occurredFrom: Instant?,
    val occurredTo: Instant?,
    val page: Int,
    val size: Int
)

/**
 * 워크플로우 감사 로그 페이지 응답 모델.
 */
data class WorkflowAuditLogPage(
    val items: List<WorkflowAuditLog>,
    val total: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int
) {
    companion object {
        /**
         * Spring Data Page를 감사 로그 페이지로 변환한다.
         */
        fun from(page: Page<WorkflowAuditLog>): WorkflowAuditLogPage {
            return WorkflowAuditLogPage(
                items = page.content,
                total = page.totalElements,
                page = page.number,
                size = page.size,
                totalPages = page.totalPages
            )
        }
    }
}
