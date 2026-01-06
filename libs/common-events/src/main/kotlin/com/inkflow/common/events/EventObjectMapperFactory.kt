package com.inkflow.common.events

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule

/**
 * 이벤트 직렬화 규약에 맞는 ObjectMapper를 생성한다.
 */
object EventObjectMapperFactory {
    /**
     * JSON 직렬화 규약에 맞춘 기본 ObjectMapper를 반환한다.
     */
    fun defaultObjectMapper(): ObjectMapper {
        return ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())
            // null 필드는 이벤트 페이로드에서 제거해 호환성을 높인다.
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            // ISO-8601 문자열로 시간을 직렬화한다.
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // 알 수 없는 필드는 무시해 forward-compatibility를 확보한다.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }
}
