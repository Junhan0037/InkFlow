package com.inkflow.media.domain

/**
 * Media 작업 실패 로그를 저장/조회하기 위한 저장소 계약.
 */
interface MediaJobFailureLogRepository {
    /**
     * 실패 로그를 저장한다.
     */
    fun save(log: MediaJobFailureLog): MediaJobFailureLog

    /**
     * jobId 기준 실패 로그 건수를 반환한다.
     */
    fun countByJobId(jobId: String): Long
}
