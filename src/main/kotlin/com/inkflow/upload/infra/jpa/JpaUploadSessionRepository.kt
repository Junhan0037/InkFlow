package com.inkflow.upload.infra.jpa

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.upload.domain.UploadSession
import com.inkflow.upload.domain.UploadSessionRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository

/**
 * JPA 기반 업로드 세션 저장소 구현체.
 */
@Repository
class JpaUploadSessionRepository(
    private val uploadSessionJpaRepository: UploadSessionJpaRepository
) : UploadSessionRepository {
    /**
     * 업로드 세션을 저장하고 중복 시 CONFLICT 예외를 발생시킨다.
     */
    override fun save(session: UploadSession): UploadSession {
        val entity = UploadSessionEntity.fromDomain(session)
        return try {
            uploadSessionJpaRepository.save(entity).toDomain()
        } catch (exception: DataIntegrityViolationException) {
            throw BusinessException(
                errorCode = ErrorCode.CONFLICT,
                details = mapOf("uploadId" to session.uploadId),
                message = "이미 존재하는 업로드 세션입니다."
            )
        }
    }

    /**
     * 업로드 ID로 세션을 조회한다.
     */
    override fun findByUploadId(uploadId: String): UploadSession? {
        return uploadSessionJpaRepository.findByUploadId(uploadId)?.toDomain()
    }
}
