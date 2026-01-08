package com.inkflow.common.outbox.infra.jpa

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Outbox 이벤트를 조회하기 위한 JPA Repository.
 */
interface OutboxEventJpaRepository : JpaRepository<OutboxEventEntity, UUID> {
}
