package com.inkflow.dlq.infra.mongo

import com.inkflow.dlq.domain.DlqMessageStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository

/**
 * Spring Data Mongo 기반 DLQ 메시지 저장소.
 */
interface DlqMessageMongoRepository : MongoRepository<DlqMessageDocument, String> {
    /**
     * sourceKey 기준으로 DLQ 메시지를 조회한다.
     */
    fun findBySourceKey(sourceKey: String): DlqMessageDocument?

    /**
     * 상태 기준으로 DLQ 메시지를 조회한다.
     */
    fun findAllByStatus(status: DlqMessageStatus, pageable: Pageable): Page<DlqMessageDocument>

    /**
     * 원본 토픽 기준으로 DLQ 메시지를 조회한다.
     */
    fun findAllByOriginalTopic(originalTopic: String, pageable: Pageable): Page<DlqMessageDocument>

    /**
     * 상태와 원본 토픽 기준으로 DLQ 메시지를 조회한다.
     */
    fun findAllByStatusAndOriginalTopic(
        status: DlqMessageStatus,
        originalTopic: String,
        pageable: Pageable
    ): Page<DlqMessageDocument>
}
