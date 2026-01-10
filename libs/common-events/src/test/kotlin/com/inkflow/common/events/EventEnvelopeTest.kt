package com.inkflow.common.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * 공통 이벤트 모델의 직렬화/파싱 규약을 검증한다.
 */
class EventEnvelopeTest {
    /**
     * EventType 문자열 파싱과 포맷 변환이 일관되는지 확인한다.
     */
    @Test
    fun eventType_parsesAndFormats() {
        val eventType = EventType.from("ASSET_STORED.v1")

        assertEquals("ASSET_STORED", eventType.name)
        assertEquals(1, eventType.version.value)
        assertEquals("ASSET_STORED.v1", eventType.asString())
    }

    /**
     * EventSchemaVersion 문자열 파싱과 포맷 변환이 일관되는지 확인한다.
     */
    @Test
    fun eventSchemaVersion_parsesAndFormats() {
        val version = EventSchemaVersion.from("v2")

        assertEquals(2, version.value)
        assertEquals("v2", version.asString())
    }

    /**
     * 잘못된 이벤트 타입 문자열은 예외를 발생시킨다.
     */
    @Test
    fun eventType_rejectsInvalidFormat() {
        assertThrows(IllegalArgumentException::class.java) {
            EventType.from("asset.stored.v1")
        }
    }

    /**
     * 잘못된 이벤트 스키마 버전 문자열은 예외를 발생시킨다.
     */
    @Test
    fun eventSchemaVersion_rejectsInvalidFormat() {
        assertThrows(IllegalArgumentException::class.java) {
            EventSchemaVersion.from("version1")
        }
    }

    /**
     * producer가 비어 있으면 EventEnvelope 생성이 거부된다.
     */
    @Test
    fun eventEnvelope_rejectsBlankProducer() {
        assertThrows(IllegalArgumentException::class.java) {
            EventEnvelope.create(
                eventType = EventType.of("ASSET_STORED", 1),
                producer = " ",
                payload = SamplePayload(id = 1L, name = "asset")
            )
        }
    }

    /**
     * idempotencyKey가 공백이면 EventEnvelope 생성이 거부된다.
     */
    @Test
    fun eventEnvelope_rejectsBlankIdempotencyKey() {
        assertThrows(IllegalArgumentException::class.java) {
            EventEnvelope.create(
                eventType = EventType.of("ASSET_STORED", 1),
                producer = "upload-api",
                payload = SamplePayload(id = 1L, name = "asset"),
                idempotencyKey = " "
            )
        }
    }

    /**
     * Jackson 기반 이벤트 직렬화/역직렬화가 정상 동작하는지 확인한다.
     */
    @Test
    fun jacksonEventSerializer_roundTrip() {
        val payload = SamplePayload(id = 1L, name = "asset")
        val envelope = EventEnvelope.create(
            eventType = EventType.of("ASSET_STORED", 1),
            producer = "upload-api",
            payload = payload,
            traceId = "0123456789abcdef0123456789abcdef",
            idempotencyKey = "upl-1",
            occurredAt = Instant.parse("2024-01-01T00:00:00Z")
        )

        val serializer = JacksonEventSerializer()
        val serialized = serializer.serialize(envelope)
        val deserialized = serializer.deserialize(serialized, SamplePayload::class.java)

        assertEquals(envelope.eventType.asString(), deserialized.eventType.asString())
        assertEquals(envelope.producer, deserialized.producer)
        assertEquals(envelope.payload, deserialized.payload)
    }

    /**
     * 이벤트 직렬화 테스트용 페이로드.
     */
    data class SamplePayload(
        val id: Long,
        val name: String
    )
}
