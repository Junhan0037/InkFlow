package com.inkflow.upload.domain

/**
 * Asset의 저장 상태.
 */
enum class AssetStatus {
    /**
     * 업로드 완료 전 대기 상태.
     */
    PENDING,

    /**
     * 저장 완료 상태.
     */
    STORED,

    /**
     * 저장 실패 상태.
     */
    FAILED,

    /**
     * 삭제 완료 상태.
     */
    DELETED
}
