package com.inkflow.upload.domain

/**
 * 업로드 세션을 Redis TTL 기반으로 저장/조회하기 위한 캐시 저장소 인터페이스.
 */
interface UploadSessionCacheRepository {
    /**
     * 업로드 세션을 저장하고 TTL을 갱신한다.
     */
    fun save(session: UploadSession): UploadSession

    /**
     * 업로드 ID로 세션을 조회한다.
     */
    fun findByUploadId(uploadId: String): UploadSession?

    /**
     * 업로드 ID로 세션을 제거한다.
     */
    fun deleteByUploadId(uploadId: String): Boolean
}
