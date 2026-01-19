package com.inkflow.indexing.domain

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * 색인 작업 유형(UPSERT/DELETE)을 정의.
 */
enum class IndexOperation {
    UPSERT,
    DELETE;

    /**
     * JSON 직렬화 시 enum 이름을 그대로 사용한다.
     */
    @JsonValue
    fun asString(): String = name

    companion object {
        /**
         * 대소문자를 허용하며 작업 유형을 파싱한다.
         */
        @JvmStatic
        @JsonCreator
        fun from(value: String): IndexOperation {
            val normalized = value.trim().uppercase()
            return entries.firstOrNull { it.name == normalized }
                ?: throw IllegalArgumentException("지원하지 않는 operation입니다: $value")
        }
    }
}
