package com.inkflow.metadata.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.common.events.EventEnvelope
import com.inkflow.common.outbox.domain.OutboxEvent
import com.inkflow.common.outbox.domain.OutboxEventRepository
import com.inkflow.metadata.domain.EpisodeMetadata
import com.inkflow.metadata.domain.EpisodeMetadataRepository
import com.inkflow.metadata.domain.EpisodeMetadataSourceRepository
import com.inkflow.metadata.domain.EpisodeMetadataSuggestion
import com.inkflow.metadata.domain.EpisodeMetadataSuggestionRepository
import com.inkflow.metadata.domain.MetadataGenerator
import com.inkflow.metadata.domain.MetadataSuggestionStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * LLM 메타 자동 생성과 휴먼 승인 플로우를 담당하는 애플리케이션 서비스.
 */
@Service
class MetadataApplicationService(
    private val sourceRepository: EpisodeMetadataSourceRepository,
    private val suggestionRepository: EpisodeMetadataSuggestionRepository,
    private val metadataRepository: EpisodeMetadataRepository,
    private val metadataGenerator: MetadataGenerator,
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) {
    companion object {
        private const val EVENT_PRODUCER = "metadata-service" // 메타 이벤트 producer 식별자.
        private const val AGGREGATE_TYPE_METADATA = "EPISODE_METADATA" // 메타 이벤트 aggregate 타입.
    }

    /**
     * 메타 자동 생성을 요청한다.
     */
    @Transactional
    fun requestGeneration(command: RequestMetadataGenerationCommand): EpisodeMetadataSuggestion {
        validateEpisodeId(command.episodeId)
        validateActor("requesterId", command.requesterId)

        val source = loadSource(command.episodeId)
        val generated = metadataGenerator.generate(source)
        val now = Instant.now(clock)

        val suggestion = EpisodeMetadataSuggestion.createPending(
            episodeId = command.episodeId,
            summary = generated.summary,
            tags = generated.tags,
            requestedBy = command.requesterId,
            generator = generated.generator,
            now = now
        )
        val saved = suggestionRepository.save(suggestion)
        recordMetaSuggestedEvent(saved, command.requesterId)
        return saved
    }

    /**
     * 메타 자동 생성 제안을 승인한다.
     */
    @Transactional
    fun approveSuggestion(command: ApproveMetadataSuggestionCommand): EpisodeMetadata {
        validateEpisodeId(command.episodeId)
        validateActor("approverId", command.approverId)

        val suggestion = loadSuggestion(command.episodeId, command.suggestionId)
        if (suggestion.status != MetadataSuggestionStatus.PENDING) {
            throw invalidState("승인 가능한 상태가 아닙니다.")
        }

        val now = Instant.now(clock)
        val summary = command.overrideSummary?.trim()?.takeIf { it.isNotBlank() } ?: suggestion.summary
        val tags = command.overrideTags?.takeIf { it.isNotEmpty() } ?: suggestion.tags

        val updatedMetadata = metadataRepository.findByEpisodeId(command.episodeId)
            ?.update(summary = summary, tags = tags, approverId = command.approverId, now = now)
            ?: EpisodeMetadata.create(
                episodeId = command.episodeId,
                summary = summary,
                tags = tags,
                approvedBy = command.approverId,
                approvedAt = now,
                now = now
            )

        val savedMetadata = metadataRepository.save(updatedMetadata)
        val approvedSuggestion = suggestion.approve(command.approverId, now)
        suggestionRepository.save(approvedSuggestion)
        recordMetaApprovedEvent(savedMetadata)
        return savedMetadata
    }

    /**
     * 메타 자동 생성 제안을 반려한다.
     */
    @Transactional
    fun rejectSuggestion(command: RejectMetadataSuggestionCommand): EpisodeMetadataSuggestion {
        validateEpisodeId(command.episodeId)
        validateActor("reviewerId", command.reviewerId)
        validateReason(command.reason)

        val suggestion = loadSuggestion(command.episodeId, command.suggestionId)
        if (suggestion.status != MetadataSuggestionStatus.PENDING) {
            throw invalidState("반려 가능한 상태가 아닙니다.")
        }

        val now = Instant.now(clock)
        val rejected = suggestion.reject(command.reviewerId, command.reason.trim(), now)
        return suggestionRepository.save(rejected)
    }

    /**
     * 에피소드 승인 메타데이터를 조회한다.
     */
    fun getApprovedMetadata(episodeId: Long): EpisodeMetadata {
        validateEpisodeId(episodeId)
        return metadataRepository.findByEpisodeId(episodeId)
            ?: throw BusinessException(
                errorCode = ErrorCode.NOT_FOUND,
                details = mapOf("episodeId" to episodeId.toString()),
                message = "승인된 메타데이터를 찾을 수 없습니다."
            )
    }

    /**
     * 에피소드의 모든 메타 제안을 조회한다.
     */
    fun getSuggestions(episodeId: Long): List<EpisodeMetadataSuggestion> {
        validateEpisodeId(episodeId)
        return suggestionRepository.findByEpisodeId(episodeId)
    }

    /**
     * 에피소드 원천 정보를 조회한다.
     */
    private fun loadSource(episodeId: Long): com.inkflow.metadata.domain.EpisodeMetadataSource {
        return sourceRepository.findSourceByEpisodeId(episodeId)
            ?: throw BusinessException(
                errorCode = ErrorCode.NOT_FOUND,
                details = mapOf("episodeId" to episodeId.toString()),
                message = "메타 생성 대상 에피소드를 찾을 수 없습니다."
            )
    }

    /**
     * 에피소드 메타 제안을 조회하고 유효성을 검증한다.
     */
    private fun loadSuggestion(episodeId: Long, suggestionId: Long): EpisodeMetadataSuggestion {
        val suggestion = suggestionRepository.findById(suggestionId)
            ?: throw BusinessException(
                errorCode = ErrorCode.NOT_FOUND,
                details = mapOf("suggestionId" to suggestionId.toString()),
                message = "메타 제안을 찾을 수 없습니다."
            )
        if (suggestion.episodeId != episodeId) {
            throw invalid("episodeId", "에피소드와 제안 식별자가 일치하지 않습니다.")
        }
        return suggestion
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
     * 반려 사유를 검증한다.
     */
    private fun validateReason(reason: String) {
        if (reason.isBlank()) {
            throw invalid("reason", "reason 값이 비어 있을 수 없습니다.")
        }
    }

    /**
     * 메타 제안 이벤트를 Outbox에 기록한다.
     */
    private fun recordMetaSuggestedEvent(suggestion: EpisodeMetadataSuggestion, requesterId: String) {
        val payload = EpisodeMetaSuggestedEventPayload(
            episodeId = suggestion.episodeId,
            suggestionId = suggestion.id ?: throw missingSuggestionId(suggestion.episodeId),
            requesterId = requesterId,
            generatedAt = suggestion.createdAt
        )

        val envelope = EventEnvelope.create(
            eventType = MetadataEventTypes.EPISODE_META_SUGGESTED,
            producer = EVENT_PRODUCER,
            payload = payload,
            idempotencyKey = buildIdempotencyKey(suggestion.episodeId, payload.suggestionId),
            occurredAt = suggestion.createdAt
        )

        val serializedPayload = serializeEnvelope(envelope, suggestion.episodeId)
        val outboxEvent = OutboxEvent.pending(
            aggregateType = AGGREGATE_TYPE_METADATA,
            aggregateId = payload.suggestionId.toString(),
            eventType = envelope.eventType.asString(),
            payload = serializedPayload,
            createdAt = suggestion.createdAt
        )
        outboxEventRepository.save(outboxEvent)
    }

    /**
     * 메타 승인 이벤트를 Outbox에 기록한다.
     */
    private fun recordMetaApprovedEvent(metadata: EpisodeMetadata) {
        val payload = EpisodeMetaApprovedEventPayload(
            episodeId = metadata.episodeId,
            metadataVersion = metadata.version,
            approvedBy = metadata.approvedBy,
            approvedAt = metadata.approvedAt
        )

        val envelope = EventEnvelope.create(
            eventType = MetadataEventTypes.EPISODE_META_APPROVED,
            producer = EVENT_PRODUCER,
            payload = payload,
            idempotencyKey = buildIdempotencyKey(metadata.episodeId, metadata.version.toLong()),
            occurredAt = metadata.approvedAt
        )

        val serializedPayload = serializeEnvelope(envelope, metadata.episodeId)
        val outboxEvent = OutboxEvent.pending(
            aggregateType = AGGREGATE_TYPE_METADATA,
            aggregateId = metadata.episodeId.toString(),
            eventType = envelope.eventType.asString(),
            payload = serializedPayload,
            createdAt = metadata.approvedAt
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
                message = "메타 이벤트 직렬화에 실패했습니다.",
                cause = exception
            )
        }
    }

    /**
     * 식별자 누락을 시스템 예외로 변환한다.
     */
    private fun missingSuggestionId(episodeId: Long): SystemException {
        return SystemException(
            errorCode = ErrorCode.INTERNAL_ERROR,
            details = mapOf("episodeId" to episodeId.toString()),
            message = "메타 제안 식별자를 확인할 수 없습니다."
        )
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
     * 메타 이벤트의 멱등성 키를 구성한다.
     */
    private fun buildIdempotencyKey(episodeId: Long, suffix: Long): String {
        return "EPISODE_META:$episodeId:$suffix"
    }
}
