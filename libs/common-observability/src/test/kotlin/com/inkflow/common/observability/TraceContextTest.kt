package com.inkflow.common.observability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.slf4j.MDC

/**
 * Trace 컨텍스트와 MDC 유틸리티 동작을 검증한다.
 */
class TraceContextTest {
    /**
     * TraceContext 생성 시 유효하지 않은 traceId는 예외를 발생시킨다.
     */
    @Test
    fun traceContext_rejectsInvalidTraceId() {
        assertThrows(IllegalArgumentException::class.java) {
            TraceContext(traceId = "invalid-trace-id")
        }
    }

    /**
     * W3C Trace Context 헤더 주입/추출이 일관되는지 확인한다.
     */
    @Test
    fun w3cTraceContext_injectsAndExtracts() {
        val context = TraceContext(
            traceId = "0123456789abcdef0123456789abcdef",
            spanId = "0123456789abcdef",
            sampled = true
        )
        val headers = mutableMapOf<String, String>()

        W3CTraceContextPropagator.inject(headers, context)
        val extracted = W3CTraceContextPropagator.extract(headers)

        requireNotNull(extracted) { "추출된 TraceContext가 null이면 안 됩니다." }
        assertEquals(context.traceId, extracted.traceId)
        assertEquals(context.spanId, extracted.spanId)
        assertEquals(context.sampled, extracted.sampled)
    }

    /**
     * 잘못된 traceparent 헤더는 추출되지 않는다.
     */
    @Test
    fun w3cTraceContext_returnsNullForInvalidHeader() {
        val headers = mapOf("traceparent" to "invalid-header")

        val extracted = W3CTraceContextPropagator.extract(headers)

        assertNull(extracted)
    }

    /**
     * TraceContext에서 spanId 형식 오류는 예외를 발생시킨다.
     */
    @Test
    fun traceContext_rejectsInvalidSpanId() {
        assertThrows(IllegalArgumentException::class.java) {
            TraceContext(
                traceId = "0123456789abcdef0123456789abcdef",
                spanId = "invalid-span"
            )
        }
    }

    /**
     * MDC 컨텍스트가 스코프 종료 시 원복되는지 확인한다.
     */
    @Test
    fun mdcContext_restoresPreviousValues() {
        MDC.put(MdcKeys.REQUEST_ID, "req-before")

        val scope = MdcContext.open(
            mapOf(
                MdcKeys.REQUEST_ID to "req-after",
                MdcKeys.TRACE_ID to "trace-1"
            )
        )

        assertEquals("req-after", MDC.get(MdcKeys.REQUEST_ID))
        assertEquals("trace-1", MDC.get(MdcKeys.TRACE_ID))

        scope.close()

        assertEquals("req-before", MDC.get(MdcKeys.REQUEST_ID))
        assertNull(MDC.get(MdcKeys.TRACE_ID))
        MDC.clear()
    }

    /**
     * TraceMdcBinder가 requestId와 traceId를 MDC에 주입하는지 확인한다.
     */
    @Test
    fun traceMdcBinder_bindsRequestAndTrace() {
        val context = TraceContext(
            traceId = "0123456789abcdef0123456789abcdef",
            spanId = "0123456789abcdef"
        )

        val scope = TraceMdcBinder.bind(context, requestId = "req-1", serviceName = "inkflow")

        assertEquals("req-1", MDC.get(MdcKeys.REQUEST_ID))
        assertEquals("inkflow", MDC.get(MdcKeys.SERVICE))
        assertEquals(context.traceId, MDC.get(MdcKeys.TRACE_ID))

        scope.close()
        MDC.clear()
    }
}
