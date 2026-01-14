package com.inkflow.common.outbox.application

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.common.outbox.domain.OutboxEvent
import com.inkflow.common.outbox.domain.OutboxEventRepository
import com.inkflow.common.outbox.domain.OutboxEventStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Outbox Relay 서비스의 배치 발행/재시도 로직을 검증한다.
 */
class OutboxRelayServiceTest {
    /**
     * 발행 성공 시 전송 상태가 완료로 갱신되는지 확인한다.
     */
    @Test
    fun relayPendingEvents_marksSendingAndSent_whenPublishSucceeds() {
        // 준비: 발행 가능한 PENDING 이벤트를 구성한다.
        val event = buildOutboxEvent()
        val repository = InMemoryOutboxEventRepository(listOf(event))
        val publisher = TestOutboxEventPublisher()
        val properties = OutboxRelayProperties(
            batchSize = 10,
            retryInitialDelay = Duration.ofMillis(10),
            retryMaxDelay = Duration.ofSeconds(1),
            retryMultiplier = 1.0,
            retryJitterRatio = 0.0
        )
        val service = buildService(repository, publisher, properties)

        // 실행: Outbox Relay 배치를 수행한다.
        service.relayPendingEvents()

        // 검증: 전송 상태가 SENT로 갱신되고 발행이 한 번 수행된다.
        val stored = repository.findById(event.id)!!
        assertEquals(OutboxEventStatus.SENT, stored.status)
        assertNotNull(stored.sentAt)
        assertEquals(1, repository.sendingMarks.size)
        assertEquals(1, repository.sentMarks.size)
        assertEquals(1, publisher.publishedEvents.size)
    }

    /**
     * 재시도 가능한 오류는 retry 정보로 갱신되는지 확인한다.
     */
    @Test
    fun relayPendingEvents_marksRetry_whenPublishFailsAndRetryable() {
        // 준비: 재시도 가능한 예외를 던지는 퍼블리셔를 준비한다.
        val event = buildOutboxEvent()
        val repository = InMemoryOutboxEventRepository(listOf(event))
        val publisher = TestOutboxEventPublisher {
            throw SystemException(
                errorCode = ErrorCode.DEPENDENCY_FAILURE,
                message = "broker down"
            )
        }
        val properties = OutboxRelayProperties(
            maxRetries = 3,
            retryInitialDelay = Duration.ofMillis(5),
            retryMaxDelay = Duration.ofMillis(50),
            retryMultiplier = 1.0,
            retryJitterRatio = 0.0
        )
        val service = buildService(repository, publisher, properties)
        val before = Instant.now()

        // 실행: Outbox Relay 배치를 수행한다.
        service.relayPendingEvents()

        // 검증: retryCount와 nextRetryAt이 갱신된다.
        val stored = repository.findById(event.id)!!
        assertEquals(OutboxEventStatus.PENDING, stored.status)
        assertEquals(1, stored.retryCount)
        assertEquals("broker down", stored.lastError)
        assertNotNull(stored.nextRetryAt)
        assertTrue(stored.nextRetryAt!!.isAfter(before))
    }

    /**
     * 재시도 불가 예외는 FAILED 상태로 종료되는지 확인한다.
     */
    @Test
    fun relayPendingEvents_marksFailed_whenPublishFailsNonRetryable() {
        // 준비: 재시도 불가 예외를 던지는 퍼블리셔를 준비한다.
        val event = buildOutboxEvent()
        val repository = InMemoryOutboxEventRepository(listOf(event))
        val publisher = TestOutboxEventPublisher {
            throw BusinessException(
                errorCode = ErrorCode.INVALID_STATE,
                message = "invalid state"
            )
        }
        val properties = OutboxRelayProperties(
            maxRetries = 2,
            retryJitterRatio = 0.0
        )
        val service = buildService(repository, publisher, properties)

        // 실행: Outbox Relay 배치를 수행한다.
        service.relayPendingEvents()

        // 검증: FAILED 상태로 전환되고 마지막 오류가 기록된다.
        val stored = repository.findById(event.id)!!
        assertEquals(OutboxEventStatus.FAILED, stored.status)
        assertEquals("invalid state", stored.lastError)
        assertEquals(1, repository.failedMarks.size)
    }

    /**
     * 대기 이벤트가 없으면 발행을 수행하지 않는지 확인한다.
     */
    @Test
    fun relayPendingEvents_skips_whenNoPendingEvents() {
        // 준비: 다음 재시도 시간이 미래인 이벤트를 구성한다.
        val futureRetryAt = Instant.now().plusSeconds(60)
        val event = buildOutboxEvent(nextRetryAt = futureRetryAt)
        val repository = InMemoryOutboxEventRepository(listOf(event))
        val publisher = TestOutboxEventPublisher()
        val service = buildService(repository, publisher, OutboxRelayProperties())

        // 실행: Outbox Relay 배치를 수행한다.
        service.relayPendingEvents()

        // 검증: 발행/상태 갱신이 호출되지 않는다.
        assertEquals(0, publisher.publishedEvents.size)
        assertEquals(0, repository.sendingMarks.size)
        assertEquals(0, repository.sentMarks.size)
        assertEquals(0, repository.retryMarks.size)
        assertEquals(0, repository.failedMarks.size)
    }

    /**
     * 테스트용 Outbox Relay 서비스를 구성한다.
     */
    private fun buildService(
        repository: OutboxEventRepository,
        publisher: OutboxEventPublisher,
        properties: OutboxRelayProperties
    ): OutboxRelayService {
        val transactionTemplate = TransactionTemplate(NoopTransactionManager())
        return OutboxRelayService(
            outboxEventRepository = repository,
            outboxEventPublisher = publisher,
            properties = properties,
            transactionTemplate = transactionTemplate
        )
    }

    /**
     * 기본 Outbox 이벤트를 생성한다.
     */
    private fun buildOutboxEvent(
        status: OutboxEventStatus = OutboxEventStatus.PENDING,
        retryCount: Int = 0,
        nextRetryAt: Instant? = null,
        lockedAt: Instant? = null,
        createdAt: Instant = Instant.parse("2024-01-01T00:00:00Z"),
        id: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    ): OutboxEvent {
        return OutboxEvent(
            id = id,
            aggregateType = "ASSET",
            aggregateId = "asset-1",
            eventType = "ASSET_STORED.v1",
            payload = """{"assetId":"asset-1"}""",
            status = status,
            retryCount = retryCount,
            nextRetryAt = nextRetryAt,
            lastError = null,
            lockedAt = lockedAt,
            createdAt = createdAt,
            sentAt = null
        )
    }

    /**
     * 트랜잭션 처리를 단순화하는 테스트용 매니저.
     */
    private class NoopTransactionManager : PlatformTransactionManager {
        /**
         * 항상 새로운 트랜잭션 상태를 반환한다.
         */
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus {
            return SimpleTransactionStatus()
        }

        /**
         * 커밋 동작을 생략한다.
         */
        override fun commit(status: TransactionStatus) {
            // 테스트 환경에서는 실제 트랜잭션 커밋이 필요 없다.
        }

        /**
         * 롤백 동작을 생략한다.
         */
        override fun rollback(status: TransactionStatus) {
            // 테스트 환경에서는 실제 롤백이 필요 없다.
        }
    }

    /**
     * 테스트용 Outbox 이벤트 퍼블리셔.
     */
    private class TestOutboxEventPublisher(
        private val behavior: (OutboxEvent) -> Unit = {}
    ) : OutboxEventPublisher {
        val publishedEvents = mutableListOf<OutboxEvent>()

        /**
         * 이벤트를 기록하고 지정된 동작을 실행한다.
         */
        override fun publish(event: OutboxEvent) {
            publishedEvents.add(event)
            behavior(event)
        }
    }

    /**
     * 테스트용 인메모리 Outbox 이벤트 저장소.
     */
    private class InMemoryOutboxEventRepository(
        initialEvents: List<OutboxEvent> = emptyList()
    ) : OutboxEventRepository {
        private val events: MutableMap<UUID, OutboxEvent> = initialEvents.associateBy { it.id }.toMutableMap()
        val sendingMarks = mutableListOf<UUID>()
        val sentMarks = mutableListOf<UUID>()
        val retryMarks = mutableListOf<UUID>()
        val failedMarks = mutableListOf<UUID>()

        /**
         * Outbox 이벤트를 저장한다.
         */
        override fun save(event: OutboxEvent): OutboxEvent {
            events[event.id] = event
            return event
        }

        /**
         * 대기/만료된 전송 이벤트를 조회한다.
         */
        override fun findPendingEventsForUpdate(
            limit: Int,
            now: Instant,
            lockExpiredBefore: Instant
        ): List<OutboxEvent> {
            // 실제 쿼리 규칙과 동일하게 필터링/정렬한다.
            val candidates = events.values.filter { event ->
                val lockedAt = event.lockedAt
                val nextRetryAt = event.nextRetryAt
                val isPending = event.status == OutboxEventStatus.PENDING
                val isSendingExpired = event.status == OutboxEventStatus.SENDING &&
                    (lockedAt == null || lockedAt <= lockExpiredBefore)
                val retryReady = nextRetryAt == null || !nextRetryAt.isAfter(now)
                (isPending || isSendingExpired) && retryReady
            }.sortedWith(
                compareBy<OutboxEvent> { it.nextRetryAt ?: it.createdAt }
                    .thenBy { it.createdAt }
            )
            return candidates.take(limit)
        }

        /**
         * Outbox 이벤트를 전송 중 상태로 갱신한다.
         */
        override fun markSending(eventId: UUID, lockedAt: Instant) {
            val event = requireNotNull(events[eventId]) { "event not found: $eventId" }
            events[eventId] = event.copy(
                status = OutboxEventStatus.SENDING,
                lockedAt = lockedAt,
                lastError = null
            )
            sendingMarks.add(eventId)
        }

        /**
         * Outbox 이벤트를 전송 완료 상태로 갱신한다.
         */
        override fun markSent(eventId: UUID, sentAt: Instant) {
            val event = requireNotNull(events[eventId]) { "event not found: $eventId" }
            events[eventId] = event.copy(
                status = OutboxEventStatus.SENT,
                sentAt = sentAt,
                nextRetryAt = null,
                lockedAt = null,
                lastError = null
            )
            sentMarks.add(eventId)
        }

        /**
         * Outbox 이벤트를 재시도 대상으로 갱신한다.
         */
        override fun markRetry(eventId: UUID, retryCount: Int, nextRetryAt: Instant, lastError: String?) {
            val event = requireNotNull(events[eventId]) { "event not found: $eventId" }
            events[eventId] = event.copy(
                status = OutboxEventStatus.PENDING,
                retryCount = retryCount,
                nextRetryAt = nextRetryAt,
                lockedAt = null,
                lastError = lastError,
                sentAt = null
            )
            retryMarks.add(eventId)
        }

        /**
         * Outbox 이벤트를 실패 상태로 갱신한다.
         */
        override fun markFailed(eventId: UUID, lastError: String?) {
            val event = requireNotNull(events[eventId]) { "event not found: $eventId" }
            events[eventId] = event.copy(
                status = OutboxEventStatus.FAILED,
                nextRetryAt = null,
                lockedAt = null,
                lastError = lastError
            )
            failedMarks.add(eventId)
        }

        /**
         * 이벤트를 조회한다.
         */
        fun findById(eventId: UUID): OutboxEvent? {
            return events[eventId]
        }
    }
}
