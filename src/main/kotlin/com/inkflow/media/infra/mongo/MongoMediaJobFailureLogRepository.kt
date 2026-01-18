package com.inkflow.media.infra.mongo

import com.inkflow.media.domain.MediaJobFailureLog
import com.inkflow.media.domain.MediaJobFailureLogRepository
import org.springframework.stereotype.Repository

/**
 * MongoDB 기반 Media 작업 실패 로그 저장소 구현체.
 */
@Repository
class MongoMediaJobFailureLogRepository(
    private val mediaJobFailureLogMongoRepository: MediaJobFailureLogMongoRepository
) : MediaJobFailureLogRepository {
    /**
     * 실패 로그를 저장하고 저장 결과를 도메인 모델로 반환한다.
     */
    override fun save(log: MediaJobFailureLog): MediaJobFailureLog {
        val document = MediaJobFailureLogDocument.fromDomain(log)
        return mediaJobFailureLogMongoRepository.save(document).toDomain()
    }

    /**
     * jobId 기준 실패 로그 건수를 반환한다.
     */
    override fun countByJobId(jobId: String): Long {
        return mediaJobFailureLogMongoRepository.countByJobId(jobId)
    }
}
