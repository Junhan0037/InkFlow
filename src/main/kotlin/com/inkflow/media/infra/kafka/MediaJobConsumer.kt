package com.inkflow.media.infra.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.common.events.EventEnvelope
import com.inkflow.media.application.MediaJobApplicationService
import com.inkflow.media.application.MediaJobCommand
import com.inkflow.media.application.MediaJobCreatedEventPayload
import com.inkflow.media.application.MediaJobEventTypes
import com.inkflow.media.application.MediaJobFailureHandler
import com.inkflow.media.application.MediaJobMessageMetadata
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * media.jobs 토픽에서 Media 작업 요청을 수신하는 Kafka 컨슈머.
 */
@Component
@ConditionalOnProperty(prefix = "inkflow.kafka", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class MediaJobConsumer(
    private val objectMapper: ObjectMapper,
    private val mediaJobApplicationService: MediaJobApplicationService,
    private val mediaJobFailureHandler: MediaJobFailureHandler
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Media 작업 생성 이벤트를 수신해 처리 서비스로 전달한다.
     */
    @KafkaListener(
        topics = ["\${inkflow.kafka.topics.media-jobs}"],
        groupId = "\${inkflow.kafka.consumer.group-id:media-worker}"
    )
    fun consume(message: String) {
        val envelope = deserializeEnvelope(message)
        if (envelope.eventType != MediaJobEventTypes.MEDIA_JOB_CREATED) {
            // 예상하지 않은 이벤트 타입은 로그만 남기고 무시한다.
            logger.warn(
                "Media 작업 컨슈머가 처리하지 않는 이벤트 타입을 수신했습니다. eventType={}, eventId={}",
                envelope.eventType.asString(),
                envelope.eventId
            )
            return
        }

        val command = MediaJobCommand.from(envelope.payload)
        val metadata = MediaJobMessageMetadata(
            eventId = envelope.eventId,
            traceId = envelope.traceId,
            idempotencyKey = envelope.idempotencyKey
        )
        try {
            mediaJobApplicationService.handleJob(command, metadata)
        } catch (exception: Exception) {
            // 실패 로그 저장 및 재시도 기준을 적용한 후 재시도 여부에 따라 예외를 전파한다.
            val decision = mediaJobFailureHandler.handleFailure(command, metadata, exception)
            logger.error(
                "Media 작업 처리 실패. jobId={}, assetId={}, derivativeType={}, shouldRetry={}, reason={}",
                command.jobId,
                command.assetId,
                command.derivativeType,
                decision.shouldRetry,
                decision.reason,
                exception
            )
            if (decision.shouldRetry) {
                throw exception
            }
        }
    }

    /**
     * Kafka 메시지를 EventEnvelope로 역직렬화한다.
     */
    private fun deserializeEnvelope(message: String): EventEnvelope<MediaJobCreatedEventPayload> {
        return try {
            val javaType = objectMapper.typeFactory
                .constructParametricType(EventEnvelope::class.java, MediaJobCreatedEventPayload::class.java)
            objectMapper.readValue(message, javaType)
        } catch (exception: Exception) {
            throw SystemException(
                errorCode = ErrorCode.INTERNAL_ERROR,
                details = mapOf("topic" to "media.jobs"),
                message = "Media 작업 이벤트 역직렬화에 실패했습니다.",
                cause = exception
            )
        }
    }
}
