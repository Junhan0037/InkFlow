package com.inkflow.metadata.infra

import com.fasterxml.jackson.databind.ObjectMapper
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import org.springframework.stereotype.Component

/**
 * 태그 목록을 JSON 문자열로 변환/복원하는 코덱.
 */
@Component
class MetadataTagCodec(
    private val objectMapper: ObjectMapper
) {
    /**
     * 태그 목록을 JSON 문자열로 직렬화한다.
     */
    fun encode(tags: List<String>): String {
        return try {
            objectMapper.writeValueAsString(tags)
        } catch (exception: Exception) {
            throw SystemException(
                errorCode = ErrorCode.INTERNAL_ERROR,
                message = "메타 태그 직렬화에 실패했습니다.",
                cause = exception
            )
        }
    }

    /**
     * JSON 문자열을 태그 목록으로 역직렬화한다.
     */
    fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            objectMapper.readerForListOf(String::class.java).readValue(raw)
        } catch (exception: Exception) {
            throw SystemException(
                errorCode = ErrorCode.INTERNAL_ERROR,
                details = mapOf("raw" to raw.take(120)),
                message = "메타 태그 역직렬화에 실패했습니다.",
                cause = exception
            )
        }
    }
}
