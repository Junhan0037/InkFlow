package com.inkflow.common.observability

/**
 * 헤더 기반 Trace 컨텍스트 전파 규약을 정의한다.
 */
interface TraceContextPropagator {
    /**
     * 헤더에서 Trace 컨텍스트를 추출한다.
     */
    fun extract(headers: Map<String, String>): TraceContext?

    /**
     * 헤더에 Trace 컨텍스트를 주입한다.
     */
    fun inject(headers: MutableMap<String, String>, context: TraceContext)
}

/**
 * W3C Trace Context(traceparent) 전파 규약을 구현한다.
 */
object W3CTraceContextPropagator : TraceContextPropagator {
    private const val TRACEPARENT_HEADER = "traceparent"
    private val TRACEPARENT_PATTERN =
        Regex("^(?i)([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$")

    /**
     * traceparent 헤더를 파싱해 Trace 컨텍스트를 추출한다.
     */
    override fun extract(headers: Map<String, String>): TraceContext? {
        val traceparent = findHeader(headers, TRACEPARENT_HEADER) ?: return null
        val match = TRACEPARENT_PATTERN.matchEntire(traceparent.trim()) ?: return null
        val traceId = match.groupValues[2]
        val spanId = match.groupValues[3]
        val flags = match.groupValues[4]
        val sampled = flags.toInt(16) and 0x01 == 1
        return TraceContext(traceId = traceId, spanId = spanId, sampled = sampled)
    }

    /**
     * traceparent 헤더를 생성해 Trace 컨텍스트를 주입한다.
     */
    override fun inject(headers: MutableMap<String, String>, context: TraceContext) {
        val spanId = context.spanId
            ?: throw IllegalArgumentException("traceparent 주입에는 spanId가 필요합니다.")
        val flags = if (context.sampled == true) "01" else "00"
        val traceparent = "00-${context.traceId.lowercase()}-${spanId.lowercase()}-$flags"
        headers[TRACEPARENT_HEADER] = traceparent
    }

    /**
     * 헤더 이름을 대소문자 구분 없이 조회한다.
     */
    private fun findHeader(headers: Map<String, String>, name: String): String? {
        return headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }
}
