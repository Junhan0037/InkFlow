package com.inkflow.publish.application

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.events.EventObjectMapperFactory
import com.inkflow.common.outbox.domain.OutboxEvent
import com.inkflow.common.outbox.domain.OutboxEventRepository
import com.inkflow.publish.domain.PublishPolicy
import com.inkflow.publish.domain.PublishPolicyRepository
import com.inkflow.publish.domain.PublishPolicyStatus
import com.inkflow.publish.domain.PublishSnapshot
import com.inkflow.publish.domain.PublishSnapshotRepository
import com.inkflow.publish.domain.PublishSnapshotStatus
import com.inkflow.publish.domain.PublishVersion
import com.inkflow.publish.domain.PublishVersionRepository
import com.inkflow.publish.domain.PublishVersionStatus
import com.inkflow.workflow.domain.EpisodeQueryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * 퍼블리시 스냅샷 생성/롤백 애플리케이션 서비스의 주요 흐름을 검증.
 */
class PublishSnapshotApplicationServiceTest {
    private val baseTime: Instant = Instant.parse("2024-01-01T00:00:00Z")
    private val clock: Clock = Clock.fixed(baseTime, ZoneOffset.UTC)
    private val objectMapper = EventObjectMapperFactory.defaultObjectMapper()

    /**
     * 신규 스냅샷 생성 시 버전/스냅샷/Outbox 이벤트가 기록되는지 확인한다.
     */
    @Test
    fun createSnapshot_createsSnapshotAndOutboxEvent() {
        // 준비: 서비스와 기본 저장소를 구성한다.
        val versionRepository = InMemoryPublishVersionRepository()
        val snapshotRepository = InMemoryPublishSnapshotRepository()
        val episodeRepository = InMemoryEpisodeQueryRepository(mapOf(1L to 10L))
        val outboxRepository = InMemoryOutboxEventRepository()
        val service = buildService(versionRepository, snapshotRepository, episodeRepository, outboxRepository)
        val command = CreateSnapshotCommand(
            episodeId = 1L,
            region = "KR",
            language = "ko",
            requestId = "req-1"
        )

        // 실행: 스냅샷 생성 요청을 처리한다.
        val result = service.createSnapshot(command)

        // 검증: 퍼블리시 버전과 스냅샷이 정상 저장된다.
        val storedVersion = versionRepository.findByEpisodeIdAndVersion(1L, 1L)
        val storedSnapshot = snapshotRepository.findBySnapshotId(result.snapshotId)
        assertNotNull(storedVersion)
        assertNotNull(storedSnapshot)
        assertEquals(PublishVersionStatus.ACTIVE, storedVersion!!.status)
        assertEquals(PublishSnapshotStatus.ACTIVE, storedSnapshot!!.status)
        assertEquals(1, outboxRepository.saved.size)
        val outboxEvent = outboxRepository.saved.first()
        assertEquals("EPISODE", outboxEvent.aggregateType)
        assertEquals("1", outboxEvent.aggregateId)
        assertEquals(PublishEventTypes.PUBLISH_SNAPSHOT_CREATED.asString(), outboxEvent.eventType)
    }

    /**
     * 기존 활성 스냅샷이 있으면 SUPERSEDED로 전환되는지 확인한다.
     */
    @Test
    fun createSnapshot_supersedesActiveVersionAndSnapshot() {
        // 준비: 기존 활성 버전/스냅샷을 저장한다.
        val versionRepository = InMemoryPublishVersionRepository()
        val snapshotRepository = InMemoryPublishSnapshotRepository()
        val episodeRepository = InMemoryEpisodeQueryRepository(mapOf(1L to 10L))
        val outboxRepository = InMemoryOutboxEventRepository()
        val existingVersion = PublishVersion.create(
            episodeId = 1L,
            version = 1L,
            snapshotId = "snap-1",
            region = "KR",
            language = "ko",
            requestId = "req-0",
            now = baseTime
        )
        versionRepository.save(existingVersion)
        snapshotRepository.save(
            PublishSnapshot.create(
                snapshotId = "snap-1",
                episodeId = 1L,
                publishVersion = 1L,
                region = "KR",
                language = "ko",
                now = baseTime
            )
        )
        val service = buildService(versionRepository, snapshotRepository, episodeRepository, outboxRepository)

        // 실행: 새 스냅샷을 생성한다.
        val result = service.createSnapshot(
            CreateSnapshotCommand(
                episodeId = 1L,
                region = "KR",
                language = "ko",
                requestId = "req-1"
            )
        )

        // 검증: 기존 버전/스냅샷이 SUPERSEDED로 전환된다.
        val supersededVersion = versionRepository.findByEpisodeIdAndVersion(1L, 1L)!!
        val supersededSnapshot = snapshotRepository.findBySnapshotId("snap-1")!!
        assertEquals(PublishVersionStatus.SUPERSEDED, supersededVersion.status)
        assertEquals(PublishSnapshotStatus.SUPERSEDED, supersededSnapshot.status)
        val newVersion = versionRepository.findByEpisodeIdAndVersion(1L, 2L)!!
        val newSnapshot = snapshotRepository.findBySnapshotId(result.snapshotId)!!
        assertEquals(PublishVersionStatus.ACTIVE, newVersion.status)
        assertEquals(PublishSnapshotStatus.ACTIVE, newSnapshot.status)
    }

    /**
     * 동일 requestId로 다른 파라미터가 요청되면 충돌 예외가 발생하는지 확인한다.
     */
    @Test
    fun createSnapshot_throwsConflictWhenRequestIdDifferentParams() {
        // 준비: requestId가 동일한 기존 버전을 구성한다.
        val versionRepository = InMemoryPublishVersionRepository()
        val snapshotRepository = InMemoryPublishSnapshotRepository()
        val episodeRepository = InMemoryEpisodeQueryRepository(mapOf(1L to 10L))
        val outboxRepository = InMemoryOutboxEventRepository()
        val existingVersion = PublishVersion.create(
            episodeId = 1L,
            version = 1L,
            snapshotId = "snap-1",
            region = "KR",
            language = "ko",
            requestId = "req-1",
            now = baseTime
        )
        versionRepository.save(existingVersion)
        val service = buildService(versionRepository, snapshotRepository, episodeRepository, outboxRepository)

        // 실행 및 검증: 다른 파라미터 요청 시 CONFLICT 예외가 발생한다.
        val exception = assertThrows(BusinessException::class.java) {
            service.createSnapshot(
                CreateSnapshotCommand(
                    episodeId = 1L,
                    region = "JP",
                    language = "ja",
                    requestId = "req-1"
                )
            )
        }
        assertEquals(ErrorCode.CONFLICT, exception.errorCode)
    }

    /**
     * 롤백 요청 시 대상 버전이 활성화되고 Outbox 이벤트가 기록되는지 확인한다.
     */
    @Test
    fun rollback_activatesTargetVersionAndRecordsOutbox() {
        // 준비: 활성 버전과 롤백 대상 버전을 저장한다.
        val versionRepository = InMemoryPublishVersionRepository()
        val snapshotRepository = InMemoryPublishSnapshotRepository()
        val episodeRepository = InMemoryEpisodeQueryRepository(mapOf(1L to 10L))
        val outboxRepository = InMemoryOutboxEventRepository()
        val version1 = PublishVersion.create(
            episodeId = 1L,
            version = 1L,
            snapshotId = "snap-1",
            region = "KR",
            language = "ko",
            requestId = "req-1",
            now = baseTime
        ).markSuperseded(baseTime)
        val version2 = PublishVersion.create(
            episodeId = 1L,
            version = 2L,
            snapshotId = "snap-2",
            region = "KR",
            language = "ko",
            requestId = "req-2",
            now = baseTime
        )
        versionRepository.save(version1)
        versionRepository.save(version2)
        snapshotRepository.save(
            PublishSnapshot.create(
                snapshotId = "snap-1",
                episodeId = 1L,
                publishVersion = 1L,
                region = "KR",
                language = "ko",
                now = baseTime
            ).markSuperseded(baseTime)
        )
        snapshotRepository.save(
            PublishSnapshot.create(
                snapshotId = "snap-2",
                episodeId = 1L,
                publishVersion = 2L,
                region = "KR",
                language = "ko",
                now = baseTime
            )
        )
        val service = buildService(versionRepository, snapshotRepository, episodeRepository, outboxRepository)

        // 실행: 롤백을 수행한다.
        val result = service.rollback(
            RollbackSnapshotCommand(
                episodeId = 1L,
                publishVersion = 1L,
                requestId = "req-rollback"
            )
        )

        // 검증: 버전/스냅샷 상태가 업데이트되고 Outbox 이벤트가 생성된다.
        val updatedActive = versionRepository.findByEpisodeIdAndVersion(1L, 1L)!!
        val updatedRolledBack = versionRepository.findByEpisodeIdAndVersion(1L, 2L)!!
        val snapshot1 = snapshotRepository.findBySnapshotId("snap-1")!!
        val snapshot2 = snapshotRepository.findBySnapshotId("snap-2")!!
        assertEquals(1L, result.activeVersion)
        assertEquals(PublishVersionStatus.ACTIVE, updatedActive.status)
        assertEquals(PublishVersionStatus.ROLLED_BACK, updatedRolledBack.status)
        assertEquals(PublishSnapshotStatus.ACTIVE, snapshot1.status)
        assertEquals(PublishSnapshotStatus.ROLLED_BACK, snapshot2.status)
        assertEquals(1, outboxRepository.saved.size)
        assertEquals(PublishEventTypes.PUBLISH_SNAPSHOT_ROLLED_BACK.asString(), outboxRepository.saved.first().eventType)
    }

    /**
     * 활성 버전과 동일한 롤백 요청은 멱등하게 처리되는지 확인한다.
     */
    @Test
    fun rollback_isIdempotentWhenTargetIsActive() {
        // 준비: 활성 버전을 저장한다.
        val versionRepository = InMemoryPublishVersionRepository()
        val snapshotRepository = InMemoryPublishSnapshotRepository()
        val episodeRepository = InMemoryEpisodeQueryRepository(mapOf(1L to 10L))
        val outboxRepository = InMemoryOutboxEventRepository()
        val activeVersion = PublishVersion.create(
            episodeId = 1L,
            version = 1L,
            snapshotId = "snap-1",
            region = "KR",
            language = "ko",
            requestId = "req-1",
            now = baseTime
        )
        versionRepository.save(activeVersion)
        snapshotRepository.save(
            PublishSnapshot.create(
                snapshotId = "snap-1",
                episodeId = 1L,
                publishVersion = 1L,
                region = "KR",
                language = "ko",
                now = baseTime
            )
        )
        val service = buildService(versionRepository, snapshotRepository, episodeRepository, outboxRepository)

        // 실행: 동일 버전으로 롤백 요청을 수행한다.
        val result = service.rollback(
            RollbackSnapshotCommand(
                episodeId = 1L,
                publishVersion = 1L,
                requestId = "req-rollback"
            )
        )

        // 검증: Outbox 이벤트는 생성되지 않고 활성 버전이 유지된다.
        val storedVersion = versionRepository.findByEpisodeIdAndVersion(1L, 1L)!!
        assertEquals(true, result.success)
        assertEquals(1L, result.activeVersion)
        assertEquals(PublishVersionStatus.ACTIVE, storedVersion.status)
        assertEquals(0, outboxRepository.saved.size)
    }

    /**
     * 테스트용 서비스를 구성한다.
     */
    private fun buildService(
        versionRepository: PublishVersionRepository,
        snapshotRepository: PublishSnapshotRepository,
        episodeRepository: EpisodeQueryRepository,
        outboxRepository: OutboxEventRepository
    ): PublishSnapshotApplicationService {
        // 테스트에서는 모든 지역/언어를 허용하는 정책을 사용한다.
        val policyService = PublishPolicyService(AllowAllPublishPolicyRepository())
        return PublishSnapshotApplicationService(
            publishVersionRepository = versionRepository,
            publishSnapshotRepository = snapshotRepository,
            episodeQueryRepository = episodeRepository,
            outboxEventRepository = outboxRepository,
            publishPolicyService = policyService,
            objectMapper = objectMapper,
            clock = clock
        )
    }

    /**
     * 테스트 전용 퍼블리시 버전 저장소.
     */
    private class InMemoryPublishVersionRepository : PublishVersionRepository {
        private val items: MutableList<PublishVersion> = mutableListOf()
        private var sequence: Long = 0L

        /**
         * 버전을 저장하거나 갱신한다.
         */
        override fun save(version: PublishVersion): PublishVersion {
            val id = version.id ?: ++sequence
            val stored = version.copy(id = id)
            items.removeIf { it.id == id }
            items.add(stored)
            return stored
        }

        /**
         * 에피소드 최신 버전을 조회한다.
         */
        override fun findLatestByEpisodeId(episodeId: Long): PublishVersion? {
            return items.filter { it.episodeId == episodeId }
                .maxByOrNull { it.version }
        }

        /**
         * 에피소드 활성 버전을 조회한다.
         */
        override fun findActiveByEpisodeId(episodeId: Long): PublishVersion? {
            return items.firstOrNull { it.episodeId == episodeId && it.status == PublishVersionStatus.ACTIVE }
        }

        /**
         * 에피소드/버전으로 조회한다.
         */
        override fun findByEpisodeIdAndVersion(episodeId: Long, version: Long): PublishVersion? {
            return items.firstOrNull { it.episodeId == episodeId && it.version == version }
        }

        /**
         * 에피소드/requestId로 조회한다.
         */
        override fun findByEpisodeIdAndRequestId(episodeId: Long, requestId: String): PublishVersion? {
            return items.firstOrNull { it.episodeId == episodeId && it.requestId == requestId }
        }
    }

    /**
     * 테스트 전용 퍼블리시 스냅샷 저장소.
     */
    private class InMemoryPublishSnapshotRepository : PublishSnapshotRepository {
        private val items: MutableList<PublishSnapshot> = mutableListOf()
        private var sequence: Long = 0L

        /**
         * 스냅샷을 저장하거나 갱신한다.
         */
        override fun save(snapshot: PublishSnapshot): PublishSnapshot {
            val id = snapshot.id ?: "snapshot-${++sequence}"
            val stored = snapshot.copy(id = id)
            items.removeIf { it.snapshotId == stored.snapshotId }
            items.add(stored)
            return stored
        }

        /**
         * snapshotId로 스냅샷을 조회한다.
         */
        override fun findBySnapshotId(snapshotId: String): PublishSnapshot? {
            return items.firstOrNull { it.snapshotId == snapshotId }
        }
    }

    /**
     * 테스트 전용 에피소드 조회 저장소.
     */
    private class InMemoryEpisodeQueryRepository(
        private val data: Map<Long, Long>
    ) : EpisodeQueryRepository {
        /**
         * episodeId에 해당하는 workId를 반환한다.
         */
        override fun findWorkIdByEpisodeId(episodeId: Long): Long? {
            return data[episodeId]
        }
    }

    /**
     * 테스트 전용 Outbox 저장소.
     */
    private class InMemoryOutboxEventRepository : OutboxEventRepository {
        val saved: MutableList<OutboxEvent> = mutableListOf()

        /**
         * 이벤트를 저장한다.
         */
        override fun save(event: OutboxEvent): OutboxEvent {
            saved.add(event)
            return event
        }

        /**
         * 테스트에서는 미사용이므로 빈 목록을 반환한다.
         */
        override fun findPendingEventsForUpdate(
            limit: Int,
            now: Instant,
            lockExpiredBefore: Instant
        ): List<OutboxEvent> {
            return emptyList()
        }

        /**
         * 테스트에서는 사용하지 않는 메서드다.
         */
        override fun markSending(eventId: UUID, lockedAt: Instant) {
            // 테스트에서는 상태 갱신을 생략한다.
        }

        /**
         * 테스트에서는 사용하지 않는 메서드다.
         */
        override fun markSent(eventId: UUID, sentAt: Instant) {
            // 테스트에서는 상태 갱신을 생략한다.
        }

        /**
         * 테스트에서는 사용하지 않는 메서드다.
         */
        override fun markRetry(eventId: UUID, retryCount: Int, nextRetryAt: Instant, lastError: String?) {
            // 테스트에서는 상태 갱신을 생략한다.
        }

        /**
         * 테스트에서는 사용하지 않는 메서드다.
         */
        override fun markFailed(eventId: UUID, lastError: String?) {
            // 테스트에서는 상태 갱신을 생략한다.
        }
    }

    /**
     * 테스트 전용 퍼블리시 정책 저장소로 모든 요청을 허용한다.
     */
    private class AllowAllPublishPolicyRepository : PublishPolicyRepository {
        /**
         * 요청된 지역/언어에 대해 활성 정책을 반환한다.
         */
        override fun findPolicy(region: String, language: String): PublishPolicy {
            return PublishPolicy.defaultPolicy(
                region = region,
                language = language,
                status = PublishPolicyStatus.ACTIVE
            )
        }
    }
}
