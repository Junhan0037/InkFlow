package com.inkflow.dlq.domain

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * DLQ 메시지 저장/조회에 대한 저장소 계약.
 */
interface DlqMessageRepository {
    /**
     * DLQ 메시지를 저장한다.
     */
    fun save(message: DlqMessage): DlqMessage

    /**
     * ID 기준으로 DLQ 메시지를 조회한다.
     */
    fun findById(id: String): DlqMessage?

    /**
     * 원본 이벤트 식별 키 기준으로 메시지를 조회한다.
     */
    fun findBySourceKey(sourceKey: String): DlqMessage?

    /**
     * 상태/토픽 필터와 페이징 조건으로 메시지를 검색한다.
     */
    fun search(status: DlqMessageStatus?, originalTopic: String?, pageable: Pageable): Page<DlqMessage>
}
