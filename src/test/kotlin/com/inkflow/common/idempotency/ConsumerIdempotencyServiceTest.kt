package com.inkflow.common.idempotency

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * ConsumerIdempotencyService 멱등 처리 로직을 검증한다.
 */
class ConsumerIdempotencyServiceTest {
    private val baseTime: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val clock: Clock = Clock.fixed(baseTime, ZoneOffset.UTC)

    /**
     * 신규 이벤트는 처리 시작 상태로 기록된다.
     */
    @Test
    fun tryBegin_startsWhenNoRecord() {
        val repository = InMemoryIdempotencyKeyRepository()
        val service = buildService(repository)
        val eventId = UUID.fromString("00000000-0000-0000-0000-000000000010")

        val decision = service.tryBegin("consumer-1", eventId)

        assertEquals(IdempotencyDecision.STARTED, decision)
        val key = "consumer:consumer-1:$eventId"
        val stored = repository.find(key)
        assertEquals(IdempotencyStatus.IN_PROGRESS, stored!!.status)
    }

    /**
     * 이미 처리 완료된 이벤트는 재처리하지 않는다.
     */
    @Test
    fun tryBegin_returnsAlreadyCompletedWhenCompletedRecord() {
        val repository = InMemoryIdempotencyKeyRepository()
        val service = buildService(repository)
        val eventId = UUID.fromString("00000000-0000-0000-0000-000000000011")
        val key = "consumer:consumer-1:$eventId"
        repository.save(
            IdempotencyRecord(
                key = key,
                status = IdempotencyStatus.COMPLETED,
                payload = null,
                createdAt = baseTime,
                updatedAt = baseTime
            ),
            ConsumerIdempotencyProperties().completedTtl
        )

        val decision = service.tryBegin("consumer-1", eventId)

        assertEquals(IdempotencyDecision.ALREADY_COMPLETED, decision)
    }

    /**
     * 처리 중인 이벤트는 중복 수행되지 않는다.
     */
    @Test
    fun tryBegin_returnsInProgressWhenRecordExists() {
        val repository = InMemoryIdempotencyKeyRepository()
        val service = buildService(repository)
        val eventId = UUID.fromString("00000000-0000-0000-0000-000000000012")
        val key = "consumer:consumer-1:$eventId"
        repository.save(
            IdempotencyRecord(
                key = key,
                status = IdempotencyStatus.IN_PROGRESS,
                payload = null,
                createdAt = baseTime,
                updatedAt = baseTime
            ),
            ConsumerIdempotencyProperties().processingTtl
        )

        val decision = service.tryBegin("consumer-1", eventId)

        assertEquals(IdempotencyDecision.IN_PROGRESS, decision)
    }

    /**
     * 처리 완료 기록이 저장되는지 확인한다.
     */
    @Test
    fun markCompleted_storesCompletedRecord() {
        val repository = InMemoryIdempotencyKeyRepository()
        val service = buildService(repository)
        val eventId = UUID.fromString("00000000-0000-0000-0000-000000000013")
        val key = "consumer:consumer-1:$eventId"

        service.markCompleted("consumer-1", eventId)

        val stored = repository.find(key)
        assertEquals(IdempotencyStatus.COMPLETED, stored!!.status)
    }

    /**
     * 실패 시 기록을 삭제해 재처리를 허용하는지 확인한다.
     */
    @Test
    fun markFailed_deletesRecord() {
        val repository = InMemoryIdempotencyKeyRepository()
        val service = buildService(repository)
        val eventId = UUID.fromString("00000000-0000-0000-0000-000000000014")
        val key = "consumer:consumer-1:$eventId"
        repository.save(
            IdempotencyRecord(
                key = key,
                status = IdempotencyStatus.IN_PROGRESS,
                payload = null,
                createdAt = baseTime,
                updatedAt = baseTime
            ),
            ConsumerIdempotencyProperties().processingTtl
        )

        service.markFailed("consumer-1", eventId)

        assertNull(repository.find(key))
    }

    /**
     * consumerName 값이 비어 있으면 예외가 발생한다.
     */
    @Test
    fun tryBegin_rejectsBlankConsumerName() {
        val service = buildService(InMemoryIdempotencyKeyRepository())
        val eventId = UUID.fromString("00000000-0000-0000-0000-000000000015")

        assertThrows(IllegalArgumentException::class.java) {
            service.tryBegin("", eventId)
        }
    }

    /**
     * 테스트용 ConsumerIdempotencyService를 생성한다.
     */
    private fun buildService(
        repository: IdempotencyKeyRepository
    ): ConsumerIdempotencyService {
        return ConsumerIdempotencyService(
            idempotencyKeyRepository = repository,
            properties = ConsumerIdempotencyProperties(),
            clock = clock
        )
    }
}
