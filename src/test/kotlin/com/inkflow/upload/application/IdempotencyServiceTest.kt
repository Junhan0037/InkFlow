package com.inkflow.upload.application

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.events.EventObjectMapperFactory
import com.inkflow.common.idempotency.IdempotencyKeyRepository
import com.inkflow.common.idempotency.IdempotencyRecord
import com.inkflow.common.idempotency.IdempotencyStatus
import com.inkflow.common.idempotency.InMemoryIdempotencyKeyRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * IdempotencyService의 저장/중복 처리 로직을 검증한다.
 */
class IdempotencyServiceTest {
    private val baseTime: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val clock: Clock = Clock.fixed(baseTime, ZoneOffset.UTC)
    private val objectMapper = EventObjectMapperFactory.defaultObjectMapper()
    private val properties = IdempotencyProperties(ttl = Duration.ofHours(1))

    /**
     * 멱등 처리 결과가 정상적으로 저장되고 반환되는지 확인한다.
     */
    @Test
    fun execute_storesCompletedResult() {
        val repository = InMemoryIdempotencyKeyRepository()
        val service = buildService(repository)
        val key = "idempotency-key-1"

        val result = service.execute(
            key = key,
            resultClass = SampleResult::class.java,
            actionName = "create-session"
        ) {
            SampleResult(id = 1L, name = "session")
        }

        val stored = repository.find(key)
        assertEquals(SampleResult(1L, "session"), result)
        assertNotNull(stored)
        assertEquals(IdempotencyStatus.COMPLETED, stored!!.status)
        assertNotNull(stored.payload)
    }

    /**
     * 완료 상태가 이미 존재하면 저장된 결과를 반환하는지 확인한다.
     */
    @Test
    fun execute_returnsStoredResultWhenCompleted() {
        val repository = InMemoryIdempotencyKeyRepository()
        val service = buildService(repository)
        val key = "idempotency-key-2"
        val payload = objectMapper.writeValueAsString(SampleResult(2L, "stored"))
        repository.save(
            IdempotencyRecord(
                key = key,
                status = IdempotencyStatus.COMPLETED,
                payload = payload,
                createdAt = baseTime,
                updatedAt = baseTime
            ),
            properties.ttl
        )

        val result = service.execute(
            key = key,
            resultClass = SampleResult::class.java,
            actionName = "create-session"
        ) {
            throw IllegalStateException("실행되면 안 되는 supplier")
        }

        assertEquals(SampleResult(2L, "stored"), result)
    }

    /**
     * 처리 중 상태에서는 충돌 예외가 발생해야 한다.
     */
    @Test
    fun execute_rejectsWhenInProgress() {
        val repository = InMemoryIdempotencyKeyRepository()
        val service = buildService(repository)
        val key = "idempotency-key-3"
        repository.save(
            IdempotencyRecord(
                key = key,
                status = IdempotencyStatus.IN_PROGRESS,
                payload = null,
                createdAt = baseTime,
                updatedAt = baseTime
            ),
            properties.ttl
        )

        val exception = assertThrows(BusinessException::class.java) {
            service.execute(
                key = key,
                resultClass = SampleResult::class.java,
                actionName = "create-session"
            ) {
                SampleResult(3L, "conflict")
            }
        }
        assertEquals(ErrorCode.CONFLICT, exception.errorCode)
    }

    /**
     * saveIfAbsent 경합 시 최신 레코드가 COMPLETED면 결과를 반환한다.
     */
    @Test
    fun execute_returnsCompletedWhenSaveIfAbsentRace() {
        val key = "idempotency-key-4"
        val payload = objectMapper.writeValueAsString(SampleResult(4L, "race"))
        val existing = IdempotencyRecord(
            key = key,
            status = IdempotencyStatus.COMPLETED,
            payload = payload,
            createdAt = baseTime,
            updatedAt = baseTime
        )
        val repository = RaceIdempotencyKeyRepository(existing)
        val service = buildService(repository)

        val result = service.execute(
            key = key,
            resultClass = SampleResult::class.java,
            actionName = "create-session"
        ) {
            SampleResult(4L, "should-not-run")
        }

        assertEquals(SampleResult(4L, "race"), result)
    }

    /**
     * 실행 중 예외가 발생하면 레코드를 삭제해 재시도를 허용한다.
     */
    @Test
    fun execute_deletesRecordWhenSupplierFails() {
        val repository = InMemoryIdempotencyKeyRepository()
        val service = buildService(repository)
        val key = "idempotency-key-5"

        assertThrows(IllegalStateException::class.java) {
            service.execute(
                key = key,
                resultClass = SampleResult::class.java,
                actionName = "create-session"
            ) {
                throw IllegalStateException("실행 실패")
            }
        }

        assertNull(repository.find(key))
    }

    /**
     * 테스트용 결과 DTO.
     */
    data class SampleResult(
        val id: Long,
        val name: String
    )

    /**
     * 테스트 목적의 IdempotencyService를 생성한다.
     */
    private fun buildService(repository: IdempotencyKeyRepository): IdempotencyService {
        return IdempotencyService(
            idempotencyKeyRepository = repository,
            objectMapper = objectMapper,
            properties = properties,
            clock = clock
        )
    }

    /**
     * saveIfAbsent가 실패하는 경합 상황을 시뮬레이션한다.
     */
    private class RaceIdempotencyKeyRepository(
        private val existing: IdempotencyRecord
    ) : IdempotencyKeyRepository {
        private var findCount = 0

        /**
         * 첫 조회는 비어 있다고 응답하고 이후에는 기존 레코드를 반환한다.
         */
        override fun find(key: String): IdempotencyRecord? {
            findCount += 1
            return if (findCount == 1) null else existing
        }

        /**
         * 동시성 경합을 가정해 저장 실패로 응답한다.
         */
        override fun saveIfAbsent(record: IdempotencyRecord, ttl: Duration): Boolean {
            return false
        }

        /**
         * 테스트 범위에서 사용하지 않는 저장 동작.
         */
        override fun save(record: IdempotencyRecord, ttl: Duration) {
            // 경합 시나리오에서는 save가 호출되지 않는다.
        }

        /**
         * 테스트 범위에서 사용하지 않는 삭제 동작.
         */
        override fun delete(key: String) {
            // 경합 시나리오에서는 delete가 호출되지 않는다.
        }
    }
}
