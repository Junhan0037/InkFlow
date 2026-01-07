package com.inkflow.common.security

import org.springframework.http.HttpHeaders

/**
 * 헤더 조회와 파싱을 표준화하는 유틸리티.
 */
object HeaderUtils {
    /**
     * HttpHeaders를 단일 값 맵으로 변환한다.
     */
    fun toSingleValueMap(headers: HttpHeaders): Map<String, String> {
        return headers.entries.associate { (key, values) -> key to values.joinToString(",") }
    }

    /**
     * 헤더 이름을 대소문자 구분 없이 조회한다.
     */
    fun getFirst(headers: Map<String, String>, name: String): String? {
        return headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }

    /**
     * 콤마로 구분된 값을 역할 집합으로 변환한다.
     */
    fun splitCommaSeparated(value: String?): Set<String> {
        if (value.isNullOrBlank()) {
            return emptySet()
        }
        return value.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }
}
