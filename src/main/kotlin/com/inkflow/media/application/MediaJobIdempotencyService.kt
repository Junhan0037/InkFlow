package com.inkflow.media.application

import com.inkflow.common.idempotency.IdempotencyDecision
import com.inkflow.common.idempotency.IdempotencyKeyRepository
import com.inkflow.common.idempotency.IdempotencyRecord
import com.inkflow.common.idempotency.IdempotencyStatus
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * Media 작업 중복 처리를 방지하기 위한 멱등성 서비스.
 */
@Service
class MediaJobIdempotencyService(
    private val idempotencyKeyRepository: IdempotencyKeyRepository,
    private val properties: MediaJobIdempotencyProperties,
    private val clock: Clock
) {
    /**
     * Media 작업 처리 시작 가능 여부를 판단하고 처리 중 상태를 기록한다.
     */
    fun tryBegin(idempotencyKey: String): IdempotencyDecision {
        require(idempotencyKey.isNotBlank()) { "idempotencyKey는 비어 있을 수 없습니다." }

        val key = resolveKey(idempotencyKey)
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
     * Media 작업이 완료되었음을 기록한다.
     */
    fun markCompleted(idempotencyKey: String) {
        val key = resolveKey(idempotencyKey)
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
     * 실패한 작업은 재시도할 수 있도록 기록을 제거한다.
     */
    fun markFailed(idempotencyKey: String) {
        val key = resolveKey(idempotencyKey)
        idempotencyKeyRepository.delete(key)
    }

    /**
     * Media 작업 전용 키 prefix를 적용한다.
     */
    private fun resolveKey(idempotencyKey: String): String {
        return "${properties.keyPrefix}$idempotencyKey"
    }
}
