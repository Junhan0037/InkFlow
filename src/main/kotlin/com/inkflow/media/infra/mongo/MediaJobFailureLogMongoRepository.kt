package com.inkflow.media.infra.mongo

import org.springframework.data.mongodb.repository.MongoRepository

/**
 * MongoDB Media 작업 실패 로그 Spring Data 리포지토리.
 */
interface MediaJobFailureLogMongoRepository : MongoRepository<MediaJobFailureLogDocument, String> {
    /**
     * jobId 기준 실패 로그 건수를 조회한다.
     */
    fun countByJobId(jobId: String): Long
}
