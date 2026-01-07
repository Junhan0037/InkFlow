package com.inkflow.common.observability

/**
 * 추적 전파에 사용할 최소 Trace 컨텍스트를 정의한다.
 */
data class TraceContext(
    val traceId: String,
    val spanId: String? = null,
    val sampled: Boolean? = null
) {
    init {
        // traceId/ spanId 형식은 대소문자를 허용하되 길이를 검증한다.
        require(isValidTraceId(traceId)) { "traceId 형식이 올바르지 않습니다: $traceId" }
        if (spanId != null) {
            require(isValidSpanId(spanId)) { "spanId 형식이 올바르지 않습니다: $spanId" }
        }
    }

    companion object {
        private val TRACE_ID_PATTERN = Regex("^(?i)([0-9a-f]{16}|[0-9a-f]{32})$")
        private val SPAN_ID_PATTERN = Regex("^(?i)[0-9a-f]{16}$")

        /**
         * traceId 유효성을 검사한다.
         */
        fun isValidTraceId(value: String): Boolean = TRACE_ID_PATTERN.matches(value)

        /**
         * spanId 유효성을 검사한다.
         */
        fun isValidSpanId(value: String): Boolean = SPAN_ID_PATTERN.matches(value)
    }
}
