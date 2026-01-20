package com.inkflow.dlq.infra.mongo

import com.inkflow.dlq.domain.DlqMessage
import com.inkflow.dlq.domain.DlqMessageRepository
import com.inkflow.dlq.domain.DlqMessageStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

/**
 * MongoDB 기반 DLQ 메시지 저장소 구현체.
 */
@Repository
class MongoDlqMessageRepository(
    private val dlqMessageMongoRepository: DlqMessageMongoRepository
) : DlqMessageRepository {
    /**
     * DLQ 메시지를 저장한다.
     */
    override fun save(message: DlqMessage): DlqMessage {
        val document = DlqMessageDocument.fromDomain(message)
        return dlqMessageMongoRepository.save(document).toDomain()
    }

    /**
     * ID 기준으로 DLQ 메시지를 조회한다.
     */
    override fun findById(id: String): DlqMessage? {
        return dlqMessageMongoRepository.findById(id).orElse(null)?.toDomain()
    }

    /**
     * sourceKey 기준으로 DLQ 메시지를 조회한다.
     */
    override fun findBySourceKey(sourceKey: String): DlqMessage? {
        return dlqMessageMongoRepository.findBySourceKey(sourceKey)?.toDomain()
    }

    /**
     * 상태/원본 토픽 필터 조건으로 DLQ 메시지를 검색한다.
     */
    override fun search(
        status: DlqMessageStatus?,
        originalTopic: String?,
        pageable: Pageable
    ): Page<DlqMessage> {
        val page = when {
            status != null && !originalTopic.isNullOrBlank() ->
                dlqMessageMongoRepository.findAllByStatusAndOriginalTopic(status, originalTopic, pageable)
            status != null -> dlqMessageMongoRepository.findAllByStatus(status, pageable)
            !originalTopic.isNullOrBlank() -> dlqMessageMongoRepository.findAllByOriginalTopic(originalTopic, pageable)
            else -> dlqMessageMongoRepository.findAll(pageable)
        }
        return page.map { it.toDomain() }
    }
}
