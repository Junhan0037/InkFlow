package com.inkflow.common.events

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * 이벤트 스키마 버전을 표현한다.
 */
data class EventSchemaVersion(
    val value: Int
) {
    init {
        // 외부 생성도 허용하되 버전 유효성으로 일관성을 보장한다.
        require(value >= 1) { "이벤트 스키마 버전은 1 이상이어야 합니다." }
    }

    /**
     * JSON 직렬화 시 `v1` 형태로 표현한다.
     */
    @JsonValue
    fun asString(): String = "v${value}"

    companion object {
        private val VERSION_PATTERN = Regex("^v(\\d+)$")

        /**
         * `v1` 형태의 문자열을 파싱해 스키마 버전을 생성한다.
         */
        @JvmStatic
        @JsonCreator
        fun from(value: String): EventSchemaVersion {
            val trimmed = value.trim()
            val match = VERSION_PATTERN.matchEntire(trimmed)
                ?: throw IllegalArgumentException("스키마 버전 형식이 올바르지 않습니다: ${value}")
            val version = match.groupValues[1].toInt()
            return EventSchemaVersion(version)
        }

        /**
         * 정수 버전으로 스키마 버전을 생성한다.
         */
        fun of(value: Int): EventSchemaVersion = EventSchemaVersion(value)
    }
}
