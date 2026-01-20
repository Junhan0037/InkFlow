package com.inkflow.dlq.api

/**
 * DLQ 재처리 요청 DTO.
 */
data class DlqReprocessRequest(
    val reason: String? = null
)
