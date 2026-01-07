package com.inkflow.common.observability

import org.slf4j.MDC

/**
 * MDC 컨텍스트를 안전하게 적용하고 복원하는 유틸리티다.
 */
object MdcContext {
    /**
     * MDC에 값을 추가하고, 종료 시 이전 컨텍스트로 복원한다.
     */
    fun open(entries: Map<String, String>): MdcScope {
        val previous = MDC.getCopyOfContextMap()
        val sanitized = entries.filterValues { it.isNotBlank() }

        if (sanitized.isNotEmpty()) {
            val merged = (previous ?: emptyMap()).toMutableMap()
            merged.putAll(sanitized)
            // 기존 MDC를 덮어쓰되, 종료 시 원복할 수 있도록 이전 값을 저장한다.
            MDC.setContextMap(merged)
        }

        return MdcScope(previous)
    }

    /**
     * MDC에 값을 추가한 뒤 지정한 블록을 실행한다.
     */
    inline fun <T> withContext(entries: Map<String, String>, block: () -> T): T {
        val scope = open(entries)
        return try {
            block()
        } finally {
            scope.close()
        }
    }
}

/**
 * MDC 컨텍스트를 원복하기 위한 스코프 객체다.
 */
class MdcScope internal constructor(
    private val previous: Map<String, String>?
) : AutoCloseable {
    /**
     * 종료 시 이전 MDC 컨텍스트를 복원한다.
     */
    override fun close() {
        if (previous == null) {
            MDC.clear()
        } else {
            MDC.setContextMap(previous)
        }
    }
}
