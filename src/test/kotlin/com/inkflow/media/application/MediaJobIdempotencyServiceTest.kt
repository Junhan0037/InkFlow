package com.inkflow.media.application

import com.inkflow.common.idempotency.IdempotencyDecision
import com.inkflow.common.idempotency.IdempotencyRecord
import com.inkflow.common.idempotency.IdempotencyStatus
import com.inkflow.common.idempotency.InMemoryIdempotencyKeyRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * MediaJobIdempotencyService 멱등 처리 로직을 검증한다.
 */
class MediaJobIdempotencyServiceTest {
    private val baseTime: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val clock: Clock = Clock.fixed(baseTime, ZoneOffset.UTC)

    /**
     * 신규 작업은 처리 시작 상태로 기록된다.
     */
    @Test
    fun tryBegin_startsWhenNoRecord() {
        val repository = InMemoryIdempotencyKeyRepository()
        val service = buildService(repository)
        val idempotencyKey = "job-1"

        val decision = service.tryBegin(idempotencyKey)

        assertEquals(IdempotencyDecision.STARTED, decision)
        val stored = repository.find("media:job:$idempotencyKey")
        assertEquals(IdempotencyStatus.IN_PROGRESS, stored!!.status)
    }

    /**
     * 이미 완료된 작업은 재처리하지 않는다.
     */
    @Test
    fun tryBegin_returnsAlreadyCompletedWhenCompletedRecord() {
        val repository = InMemoryIdempotencyKeyRepository()
        val service = buildService(repository)
        val idempotencyKey = "job-2"
        repository.save(
            IdempotencyRecord(
                key = "media:job:$idempotencyKey",
                status = IdempotencyStatus.COMPLETED,
                payload = null,
                createdAt = baseTime,
                updatedAt = baseTime
            ),
            MediaJobIdempotencyProperties().completedTtl
        )

        val decision = service.tryBegin(idempotencyKey)

        assertEquals(IdempotencyDecision.ALREADY_COMPLETED, decision)
    }

    /**
     * 실패 시 기록을 삭제해 재처리를 허용한다.
     */
    @Test
    fun markFailed_deletesRecord() {
        val repository = InMemoryIdempotencyKeyRepository()
        val service = buildService(repository)
        val idempotencyKey = "job-3"
        repository.save(
            IdempotencyRecord(
                key = "media:job:$idempotencyKey",
                status = IdempotencyStatus.IN_PROGRESS,
                payload = null,
                createdAt = baseTime,
                updatedAt = baseTime
            ),
            MediaJobIdempotencyProperties().processingTtl
        )

        service.markFailed(idempotencyKey)

        assertNull(repository.find("media:job:$idempotencyKey"))
    }

    /**
     * idempotencyKey가 비어 있으면 예외가 발생한다.
     */
    @Test
    fun tryBegin_rejectsBlankKey() {
        val service = buildService(InMemoryIdempotencyKeyRepository())

        assertThrows(IllegalArgumentException::class.java) {
            service.tryBegin("")
        }
    }

    /**
     * 테스트용 MediaJobIdempotencyService를 생성한다.
     */
    private fun buildService(
        repository: InMemoryIdempotencyKeyRepository
    ): MediaJobIdempotencyService {
        return MediaJobIdempotencyService(
            idempotencyKeyRepository = repository,
            properties = MediaJobIdempotencyProperties(),
            clock = clock
        )
    }
}
