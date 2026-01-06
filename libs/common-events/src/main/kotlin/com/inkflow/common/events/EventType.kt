package com.inkflow.common.events

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * 이벤트 타입과 스키마 버전을 함께 관리한다.
 */
data class EventType(
    val name: String,
    val version: EventSchemaVersion
) {
    init {
        // 외부 생성도 허용하되 타입/버전 규약으로 일관성을 보장한다.
        require(name.isNotBlank()) { "이벤트 타입 이름은 비어 있을 수 없습니다." }
        require(NAME_PATTERN.matches(name)) { "이벤트 타입 이름은 대문자/숫자/언더스코어만 허용됩니다: ${name}" }
    }

    /**
     * JSON 직렬화 시 `ASSET_STORED.v1` 형태로 표현한다.
     */
    @JsonValue
    fun asString(): String = "${name}.${version.asString()}"

    companion object {
        private val EVENT_TYPE_PATTERN = Regex("^([A-Z0-9_]+)\\.v(\\d+)$")
        private val NAME_PATTERN = Regex("^[A-Z0-9_]+$")

        /**
         * `ASSET_STORED.v1` 형태의 문자열을 파싱해 이벤트 타입을 생성한다.
         */
        @JvmStatic
        @JsonCreator
        fun from(value: String): EventType {
            val trimmed = value.trim()
            val match = EVENT_TYPE_PATTERN.matchEntire(trimmed)
                ?: throw IllegalArgumentException("이벤트 타입 형식이 올바르지 않습니다: ${value}")
            val name = match.groupValues[1]
            val version = EventSchemaVersion.of(match.groupValues[2].toInt())
            return EventType(name, version)
        }

        /**
         * 이벤트 이름과 버전으로 이벤트 타입을 생성한다.
         */
        fun of(name: String, version: Int): EventType = EventType(name, EventSchemaVersion.of(version))
    }
}
