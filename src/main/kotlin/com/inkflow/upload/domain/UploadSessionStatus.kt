package com.inkflow.upload.domain

/**
 * 업로드 세션의 상태.
 */
enum class UploadSessionStatus {
    /**
     * 세션이 생성된 직후 상태.
     */
    CREATED,

    /**
     * 업로드 진행 중 상태.
     */
    UPLOADING,

    /**
     * 업로드가 완료된 상태.
     */
    COMPLETED,

    /**
     * 업로드 유효 시간이 만료된 상태.
     */
    EXPIRED,

    /**
     * 사용자가 업로드를 중단한 상태.
     */
    ABORTED
}
