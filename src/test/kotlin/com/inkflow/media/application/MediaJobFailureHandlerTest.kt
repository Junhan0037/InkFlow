package com.inkflow.media.application

import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.media.domain.MediaJobFailureLog
import com.inkflow.media.domain.MediaJobFailureLogRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * MediaJobFailureHandler의 실패 로그 기록과 재시도 판단을 검증한다.
 */
class MediaJobFailureHandlerTest {
    private val baseTime: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val clock: Clock = Clock.fixed(baseTime, ZoneOffset.UTC)

    /**
     * 재시도 가능한 오류에서 실패 로그가 저장되고 재시도 판단이 반환되는지 확인한다.
     */
    @Test
    fun handleFailure_recordsFailureLogAndReturnsDecision() {
        // 준비: 실패 로그 저장소와 재시도 정책을 구성한다.
        val repository = InMemoryMediaJobFailureLogRepository(mapOf("job-1" to 1L))
        val policy = MediaJobRetryPolicy(MediaJobRetryProperties(maxAttempts = 3))
        val handler = MediaJobFailureHandler(
            failureLogRepository = repository,
            retryPolicy = policy,
            clock = clock
        )
        val command = MediaJobCommand(
            jobId = "job-1",
            assetId = 10L,
            derivativeType = "THUMBNAIL",
            spec = MediaJobSpec(width = 120, height = 90, format = "jpg")
        )
        val metadata = MediaJobMessageMetadata(
            eventId = UUID.fromString("00000000-0000-0000-0000-000000000333"),
            traceId = "trace-1",
            idempotencyKey = "idem-1"
        )
        val exception = SystemException(
            errorCode = ErrorCode.DEPENDENCY_FAILURE,
            details = mapOf("target" to "minio"),
            message = "연결 실패"
        )

        // 실행: 실패 로그 기록을 수행한다.
        val decision = handler.handleFailure(command, metadata, exception)

        // 검증: 재시도 판단이 true이고 실패 로그가 저장되는지 확인한다.
        assertTrue(decision.shouldRetry)
        assertEquals(1, repository.saved.size)
        val log = repository.saved.first()
        assertEquals("job-1", log.jobId)
        assertEquals(2, log.retryCount)
        assertEquals(ErrorCode.DEPENDENCY_FAILURE.code, log.errorCode)
        assertEquals("연결 실패", log.errorMessage)
        assertEquals(mapOf("target" to "minio"), log.errorDetails)
        assertEquals(metadata.eventId.toString(), log.eventId)
        assertEquals(metadata.traceId, log.traceId)
        assertEquals(metadata.idempotencyKey, log.idempotencyKey)
    }

    /**
     * Media 작업 실패 로그 저장을 위한 테스트용 저장소.
     */
    private class InMemoryMediaJobFailureLogRepository(
        initialCounts: Map<String, Long>
    ) : MediaJobFailureLogRepository {
        private val counts = initialCounts.toMutableMap()
        val saved = mutableListOf<MediaJobFailureLog>()

        /**
         * 실패 로그를 저장하고 건수를 갱신한다.
         */
        override fun save(log: MediaJobFailureLog): MediaJobFailureLog {
            saved.add(log)
            counts[log.jobId] = (counts[log.jobId] ?: 0L) + 1L
            return log
        }

        /**
         * jobId 기준 실패 로그 건수를 반환한다.
         */
        override fun countByJobId(jobId: String): Long {
            return counts[jobId] ?: 0L
        }
    }
}
