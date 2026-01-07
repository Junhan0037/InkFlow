package com.inkflow.common.observability

/**
 * MDC에 저장하는 공통 키를 정의한다.
 */
object MdcKeys {
    /**
     * 서비스 이름 키를 정의한다.
     */
    const val SERVICE = "service"

    /**
     * 요청 식별자 키를 정의한다.
     */
    const val REQUEST_ID = "requestId"

    /**
     * 추적 식별자 키를 정의한다.
     */
    const val TRACE_ID = "traceId"

    /**
     * 스팬 식별자 키를 정의한다.
     */
    const val SPAN_ID = "spanId"
}
