package com.inkflow.upload.domain

/**
 * 업로드 세션을 영속 저장소에 저장/조회하기 위한 저장소 계약.
 */
interface UploadSessionRepository {
    /**
     * 업로드 세션을 저장한다.
     */
    fun save(session: UploadSession): UploadSession

    /**
     * 업로드 ID로 세션을 조회한다.
     */
    fun findByUploadId(uploadId: String): UploadSession?
}
