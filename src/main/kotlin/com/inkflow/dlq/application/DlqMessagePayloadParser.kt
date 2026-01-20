package com.inkflow.dlq.application

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * DLQ 메시지 payload에서 공통 메타데이터를 추출한다.
 */
@Component
class DlqMessagePayloadParser(
    private val objectMapper: ObjectMapper
) {
    /**
     * payload JSON을 파싱해 이벤트 메타데이터 요약을 반환한다.
     */
    fun parse(payload: String): DlqMessagePayloadSummary {
        return try {
            val root = objectMapper.readTree(payload)
            val occurredAt = root.get("occurredAt")?.asText()
                ?.takeIf { it.isNotBlank() }
                ?.let { Instant.parse(it) }
            DlqMessagePayloadSummary(
                eventId = root.get("eventId")?.asText(),
                eventType = root.get("eventType")?.asText(),
                producer = root.get("producer")?.asText(),
                traceId = root.get("traceId")?.asText(),
                idempotencyKey = root.get("idempotencyKey")?.asText(),
                occurredAt = occurredAt
            )
        } catch (exception: Exception) {
            // payload 파싱 실패 시에도 DLQ 적재 흐름이 중단되지 않도록 빈 요약을 반환한다.
            DlqMessagePayloadSummary.empty()
        }
    }
}

/**
 * DLQ 메시지 payload에서 추출한 이벤트 메타데이터 요약.
 */
data class DlqMessagePayloadSummary(
    val eventId: String?,
    val eventType: String?,
    val producer: String?,
    val traceId: String?,
    val idempotencyKey: String?,
    val occurredAt: Instant?
) {
    companion object {
        /**
         * 파싱 실패 시 사용할 빈 요약 객체를 제공한다.
         */
        fun empty(): DlqMessagePayloadSummary {
            return DlqMessagePayloadSummary(
                eventId = null,
                eventType = null,
                producer = null,
                traceId = null,
                idempotencyKey = null,
                occurredAt = null
            )
        }
    }
}
