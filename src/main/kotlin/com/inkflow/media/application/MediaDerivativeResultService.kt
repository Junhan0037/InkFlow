package com.inkflow.media.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.common.events.EventEnvelope
import com.inkflow.common.outbox.domain.OutboxEvent
import com.inkflow.common.outbox.domain.OutboxEventRepository
import com.inkflow.media.domain.DerivativeMetadata
import com.inkflow.media.domain.DerivativeMetadataRepository
import com.inkflow.media.domain.DerivativeType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * 파생 리소스 메타 저장과 결과 이벤트 기록을 담당.
 */
@Service
class MediaDerivativeResultService(
    private val derivativeMetadataRepository: DerivativeMetadataRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) {
    companion object {
        private const val EVENT_PRODUCER = "media-worker" // Media 결과 이벤트 producer 식별자.
        private const val AGGREGATE_TYPE_DERIVATIVE = "DERIVATIVE" // Derivative 이벤트 aggregate 타입.
    }

    /**
     * 썸네일 파생 메타를 저장하고 결과 이벤트를 Outbox에 기록한다.
     */
    @Transactional
    fun recordThumbnailResult(
        command: MediaJobCommand,
        metadata: MediaJobMessageMetadata,
        storageKey: String,
        thumbnailResult: MediaThumbnailResult,
        durationMs: Long
    ): DerivativeMetadata {
        val now = Instant.now(clock)
        val derivative = DerivativeMetadata.createReady(
            assetId = command.assetId,
            type = DerivativeType.THUMBNAIL,
            width = thumbnailResult.width,
            height = thumbnailResult.height,
            format = thumbnailResult.format,
            storageKey = storageKey,
            now = now
        )

        val savedDerivative = derivativeMetadataRepository.save(derivative)
        val derivativeId = savedDerivative.id ?: throw missingDerivativeId(command.jobId)
        val payload = MediaJobCompletedEventPayload(
            jobId = command.jobId,
            assetId = command.assetId,
            derivativeId = derivativeId,
            status = savedDerivative.status,
            durationMs = durationMs
        )

        val envelope = EventEnvelope.create(
            eventType = MediaJobEventTypes.MEDIA_JOB_COMPLETED,
            producer = EVENT_PRODUCER,
            payload = payload,
            traceId = metadata.traceId,
            idempotencyKey = metadata.idempotencyKey ?: command.jobId,
            occurredAt = now
        )

        val serializedPayload = try {
            objectMapper.writeValueAsString(envelope)
        } catch (exception: Exception) {
            throw SystemException(
                errorCode = ErrorCode.INTERNAL_ERROR,
                details = mapOf("jobId" to command.jobId),
                message = "Media 결과 이벤트 직렬화에 실패했습니다.",
                cause = exception
            )
        }

        val outboxEvent = OutboxEvent.pending(
            aggregateType = AGGREGATE_TYPE_DERIVATIVE,
            aggregateId = derivativeId.toString(),
            eventType = envelope.eventType.asString(),
            payload = serializedPayload,
            createdAt = now
        )
        outboxEventRepository.save(outboxEvent)
        return savedDerivative
    }

    /**
     * Derivative 식별자 누락을 시스템 예외로 변환한다.
     */
    private fun missingDerivativeId(jobId: String): SystemException {
        return SystemException(
            errorCode = ErrorCode.INTERNAL_ERROR,
            details = mapOf("jobId" to jobId),
            message = "Derivative 식별자를 확인할 수 없습니다."
        )
    }
}
