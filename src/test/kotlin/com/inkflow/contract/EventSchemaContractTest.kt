package com.inkflow.contract

import com.inkflow.common.events.EventEnvelope
import com.inkflow.common.events.EventObjectMapperFactory
import com.inkflow.common.events.EventType
import com.inkflow.common.events.JacksonEventSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * 이벤트 스키마(envelope + payload) 계약을 검증한다.
 */
class EventSchemaContractTest {
    private val objectMapper = EventObjectMapperFactory.defaultObjectMapper()
    private val serializer = JacksonEventSerializer(objectMapper)

    /**
     * 이벤트 envelope가 필수 필드와 함께 직렬화되는지 확인한다.
     */
    @Test
    fun eventEnvelope_serializesRequiredFields() {
        val payload = TestPayload(assetId = 10L, status = "STORED")
        val eventType = EventType.of(name = "ASSET_STORED", version = 1)
        val eventId = UUID.fromString("00000000-0000-0000-0000-000000000010")
        val occurredAt = Instant.parse("2026-01-01T00:00:00Z")

        val envelope = EventEnvelope(
            eventId = eventId,
            eventType = eventType,
            occurredAt = occurredAt,
            producer = "upload-service",
            traceId = "trace-1",
            idempotencyKey = "idempotency-1",
            payload = payload
        )

        val jsonBytes = serializer.serialize(envelope)
        val node = objectMapper.readTree(jsonBytes)

        assertTrue(node.hasNonNull("eventId"))
        assertTrue(node.hasNonNull("eventType"))
        assertTrue(node.hasNonNull("occurredAt"))
        assertTrue(node.hasNonNull("producer"))
        assertTrue(node.has("traceId"))
        assertTrue(node.has("idempotencyKey"))
        assertTrue(node.hasNonNull("payload"))

        assertEquals("ASSET_STORED.v1", node.get("eventType").asText())
        assertEquals(10L, node.get("payload").get("assetId").asLong())
        assertEquals("STORED", node.get("payload").get("status").asText())
    }

    /**
     * 이벤트 타입 문자열 포맷이 역직렬화 가능한지 확인한다.
     */
    @Test
    fun eventType_parsesFromString() {
        val parsed = EventType.from("ASSET_STORED.v1")
        assertNotNull(parsed)
        assertEquals("ASSET_STORED.v1", parsed.asString())
    }

    /**
     * 이벤트 테스트용 페이로드를 정의한다.
     */
    private data class TestPayload(
        val assetId: Long,
        val status: String
    )
}
