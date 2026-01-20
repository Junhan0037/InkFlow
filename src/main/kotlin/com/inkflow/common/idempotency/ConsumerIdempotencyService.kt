package com.inkflow.common.idempotency

import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Kafka 컨슈머의 이벤트 중복 처리를 방지하는 멱등성 서비스.
 */
@Service
class ConsumerIdempotencyService(
    private val idempotencyKeyRepository: IdempotencyKeyRepository,
    private val properties: ConsumerIdempotencyProperties,
    private val clock: Clock
) {
    /**
     * 이벤트 처리 시작 가능 여부를 판단하고 처리 중 상태를 기록한다.
     */
    fun tryBegin(consumerName: String, eventId: UUID): IdempotencyDecision {
        require(consumerName.isNotBlank()) { "consumerName은 비어 있을 수 없습니다." }

        val key = buildKey(consumerName, eventId)
        val now = Instant.now(clock)
        val inProgress = IdempotencyRecord(
            key = key,
            status = IdempotencyStatus.IN_PROGRESS,
            payload = null,
            createdAt = now,
            updatedAt = now
        )

        val saved = idempotencyKeyRepository.saveIfAbsent(inProgress, properties.processingTtl)
        if (saved) {
            return IdempotencyDecision.STARTED
        }

        // 이미 기록된 상태를 확인해 중복 처리를 결정한다.
        val existing = idempotencyKeyRepository.find(key) ?: return IdempotencyDecision.IN_PROGRESS
        return when (existing.status) {
            IdempotencyStatus.COMPLETED -> IdempotencyDecision.ALREADY_COMPLETED
            IdempotencyStatus.IN_PROGRESS -> IdempotencyDecision.IN_PROGRESS
        }
    }

    /**
     * 이벤트 처리가 완료되었음을 기록한다.
     */
    fun markCompleted(consumerName: String, eventId: UUID) {
        val key = buildKey(consumerName, eventId)
        val now = Instant.now(clock)
        val completed = IdempotencyRecord(
            key = key,
            status = IdempotencyStatus.COMPLETED,
            payload = null,
            createdAt = now,
            updatedAt = now
        )
        idempotencyKeyRepository.save(completed, properties.completedTtl)
    }

    /**
     * 처리 실패 시 재시도를 허용하도록 기록을 제거한다.
     */
    fun markFailed(consumerName: String, eventId: UUID) {
        val key = buildKey(consumerName, eventId)
        idempotencyKeyRepository.delete(key)
    }

    /**
     * 컨슈머 단위의 Idempotency 키를 구성한다.
     */
    private fun buildKey(consumerName: String, eventId: UUID): String {
        return "${properties.keyPrefix}$consumerName:$eventId"
    }
}
