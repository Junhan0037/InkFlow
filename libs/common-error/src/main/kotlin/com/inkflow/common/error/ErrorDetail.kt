package com.inkflow.common.error

/**
 * 에러 응답 데이터의 상세 정보를 정의한다.
 */
data class ErrorDetail(
    val details: Map<String, String> = emptyMap(),
    val retryable: Boolean
)
