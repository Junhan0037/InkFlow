package com.inkflow.common.observability

/**
 * Trace 컨텍스트를 MDC에 바인딩하는 유틸리티다.
 */
object TraceMdcBinder {
    /**
     * Trace 컨텍스트를 MDC에 적용하고, 종료 시 이전 상태로 복원한다.
     */
    fun bind(
        traceContext: TraceContext?,
        requestId: String? = null,
        serviceName: String? = null
    ): MdcScope {
        val entries = mutableMapOf<String, String>()

        if (!requestId.isNullOrBlank()) {
            entries[MdcKeys.REQUEST_ID] = requestId
        }

        if (!serviceName.isNullOrBlank()) {
            entries[MdcKeys.SERVICE] = serviceName
        }

        if (traceContext != null) {
            entries[MdcKeys.TRACE_ID] = traceContext.traceId
            if (!traceContext.spanId.isNullOrBlank()) {
                entries[MdcKeys.SPAN_ID] = traceContext.spanId
            }
        }

        return MdcContext.open(entries)
    }
}
