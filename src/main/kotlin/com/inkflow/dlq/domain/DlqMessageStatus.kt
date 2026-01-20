package com.inkflow.dlq.domain

/**
 * DLQ 메시지 처리 상태를 정의.
 */
enum class DlqMessageStatus {
    /**
     * DLQ에 적재되어 재처리를 대기 중인 상태.
     */
    PENDING,

    /**
     * 재처리를 진행 중인 상태.
     */
    REPROCESSING,

    /**
     * 재처리가 완료된 상태.
     */
    REPROCESSED,

    /**
     * 재처리 시도 후 실패한 상태.
     */
    FAILED
}
