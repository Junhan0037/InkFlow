package com.inkflow.common.events

import com.fasterxml.jackson.databind.ObjectMapper

/**
 * 이벤트 envelope 직렬화/역직렬화 인터페이스를 정의한다.
 */
interface EventSerializer {
    /**
     * 이벤트 envelope를 바이트 배열로 직렬화한다.
     */
    fun <T : Any> serialize(envelope: EventEnvelope<T>): ByteArray

    /**
     * 바이트 배열을 이벤트 envelope로 역직렬화한다.
     */
    fun <T : Any> deserialize(bytes: ByteArray, payloadType: Class<T>): EventEnvelope<T>
}

/**
 * Jackson 기반의 이벤트 직렬화 구현체다.
 */
class JacksonEventSerializer(
    private val objectMapper: ObjectMapper = EventObjectMapperFactory.defaultObjectMapper()
) : EventSerializer {
    /**
     * 이벤트 envelope를 JSON 바이트 배열로 직렬화한다.
     */
    override fun <T : Any> serialize(envelope: EventEnvelope<T>): ByteArray {
        return objectMapper.writeValueAsBytes(envelope)
    }

    /**
     * JSON 바이트 배열을 이벤트 envelope로 역직렬화한다.
     */
    override fun <T : Any> deserialize(bytes: ByteArray, payloadType: Class<T>): EventEnvelope<T> {
        val javaType = objectMapper.typeFactory
            .constructParametricType(EventEnvelope::class.java, payloadType)
        return objectMapper.readValue(bytes, javaType)
    }
}
