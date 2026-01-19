package com.inkflow.indexing.domain

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * 색인 대상 엔티티 타입을 정의.
 */
enum class IndexEntityType {
    WORK,
    EPISODE,
    ASSET;

    /**
     * JSON 직렬화 시 enum 이름을 그대로 사용한다.
     */
    @JsonValue
    fun asString(): String = name

    companion object {
        /**
         * 대소문자를 허용하며 엔티티 타입을 파싱한다.
         */
        @JvmStatic
        @JsonCreator
        fun from(value: String): IndexEntityType {
            val normalized = value.trim().uppercase()
            return entries.firstOrNull { it.name == normalized }
                ?: throw IllegalArgumentException("지원하지 않는 entityType입니다: $value")
        }
    }
}
