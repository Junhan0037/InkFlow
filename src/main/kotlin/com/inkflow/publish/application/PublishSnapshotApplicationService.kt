package com.inkflow.publish.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.common.events.EventEnvelope
import com.inkflow.common.outbox.domain.OutboxEvent
import com.inkflow.common.outbox.domain.OutboxEventRepository
import com.inkflow.publish.domain.PublishSnapshot
import com.inkflow.publish.domain.PublishSnapshotRepository
import com.inkflow.publish.domain.PublishVersion
import com.inkflow.publish.domain.PublishVersionRepository
import com.inkflow.workflow.domain.EpisodeQueryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * 퍼블리시 스냅샷 생성/롤백을 처리하는 애플리케이션 서비스.
 */
@Service
class PublishSnapshotApplicationService(
    private val publishVersionRepository: PublishVersionRepository,
    private val publishSnapshotRepository: PublishSnapshotRepository,
    private val episodeQueryRepository: EpisodeQueryRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val publishPolicyService: PublishPolicyService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) {
    companion object {
        private const val EVENT_PRODUCER = "publish-service" // Outbox 이벤트 producer 식별자.
        private const val AGGREGATE_TYPE_EPISODE = "EPISODE" // 에피소드 기준 파티션 키를 유지한다.
    }

    /**
     * 퍼블리시 스냅샷을 생성한다.
     */
    @Transactional
    fun createSnapshot(command: CreateSnapshotCommand): CreateSnapshotResult {
        validateEpisode(command.episodeId)

        val existing = publishVersionRepository.findByEpisodeIdAndRequestId(command.episodeId, command.requestId)
        if (existing != null) {
            validateIdempotentRequest(command, existing)
            return CreateSnapshotResult(snapshotId = existing.snapshotId, publishVersion = existing.version)
        }

        val now = Instant.now(clock)
        val normalizedRegion = normalizeRegion(command.region)
        val normalizedLanguage = normalizeLanguage(command.language)

        // 정책 검증을 통과한 지역/언어만 퍼블리시를 진행한다.
        val policy = publishPolicyService.ensurePublishable(normalizedRegion, normalizedLanguage, now)
        val latest = publishVersionRepository.findLatestByEpisodeId(command.episodeId)
        val nextVersion = (latest?.version ?: 0L) + 1L
        val snapshotId = UUID.randomUUID().toString()

        // 기존 활성 버전이 있으면 SUPERSEDED로 전환한다.
        publishVersionRepository.findActiveByEpisodeId(command.episodeId)?.let { activeVersion ->
            val superseded = publishVersionRepository.save(activeVersion.markSuperseded(now))
            updateSnapshotStatus(superseded.snapshotId) { it.markSuperseded(now) }
        }

        val newVersion = PublishVersion.create(
            episodeId = command.episodeId,
            version = nextVersion,
            snapshotId = snapshotId,
            region = policy.region,
            language = policy.language,
            requestId = command.requestId,
            now = now
        )

        val savedVersion = publishVersionRepository.save(newVersion)
        val snapshot = PublishSnapshot.create(
            snapshotId = snapshotId,
            episodeId = command.episodeId,
            publishVersion = savedVersion.version,
            region = policy.region,
            language = policy.language,
            now = now
        )
        publishSnapshotRepository.save(snapshot)

        recordSnapshotCreatedEvent(command, savedVersion, snapshot, now)

        return CreateSnapshotResult(snapshotId = snapshotId, publishVersion = savedVersion.version)
    }

    /**
     * 퍼블리시 스냅샷을 특정 버전으로 롤백한다.
     */
    @Transactional
    fun rollback(command: RollbackSnapshotCommand): RollbackSnapshotResult {
        validateEpisode(command.episodeId)

        val now = Instant.now(clock)
        val activeVersion = publishVersionRepository.findActiveByEpisodeId(command.episodeId)
            ?: throw BusinessException(
                errorCode = ErrorCode.INVALID_STATE,
                details = mapOf("episodeId" to command.episodeId.toString()),
                message = "활성 퍼블리시 버전이 존재하지 않습니다."
            )

        if (activeVersion.version == command.publishVersion) {
            // 동일 버전 롤백 요청은 멱등하게 성공 처리한다.
            return RollbackSnapshotResult(success = true, activeVersion = activeVersion.version)
        }

        val targetVersion = publishVersionRepository.findByEpisodeIdAndVersion(
            command.episodeId,
            command.publishVersion
        ) ?: throw BusinessException(
            errorCode = ErrorCode.NOT_FOUND,
            details = mapOf(
                "episodeId" to command.episodeId.toString(),
                "publishVersion" to command.publishVersion.toString()
            ),
            message = "롤백 대상 퍼블리시 버전을 찾을 수 없습니다."
        )

        val rolledBack = publishVersionRepository.save(activeVersion.markRolledBack(now))
        val activated = publishVersionRepository.save(targetVersion.activate(now))

        updateSnapshotStatus(rolledBack.snapshotId) { it.markRolledBack(now) }
        updateSnapshotStatus(activated.snapshotId) { it.activate(now) }

        recordSnapshotRolledBackEvent(command, rolledBack, activated, now)

        return RollbackSnapshotResult(success = true, activeVersion = activated.version)
    }

    /**
     * 에피소드 존재 여부를 검증한다.
     */
    private fun validateEpisode(episodeId: Long) {
        if (episodeId <= 0) {
            throw BusinessException(
                errorCode = ErrorCode.INVALID_REQUEST,
                details = mapOf("episodeId" to episodeId.toString()),
                message = "episodeId는 1 이상이어야 합니다."
            )
        }

        val workId = episodeQueryRepository.findWorkIdByEpisodeId(episodeId)
        if (workId == null) {
            throw BusinessException(
                errorCode = ErrorCode.NOT_FOUND,
                details = mapOf("episodeId" to episodeId.toString()),
                message = "에피소드를 찾을 수 없습니다."
            )
        }
    }

    /**
     * 동일 requestId 요청의 파라미터 일치 여부를 확인한다.
     */
    private fun validateIdempotentRequest(command: CreateSnapshotCommand, existing: PublishVersion) {
        // 지역/언어는 표준 형식으로 맞춘 뒤 동일 요청 여부를 판단한다.
        val normalizedRegion = normalizeRegion(command.region)
        val normalizedLanguage = normalizeLanguage(command.language)
        if (normalizeRegion(existing.region) != normalizedRegion || normalizeLanguage(existing.language) != normalizedLanguage) {
            throw BusinessException(
                errorCode = ErrorCode.CONFLICT,
                details = mapOf(
                    "episodeId" to command.episodeId.toString(),
                    "requestId" to command.requestId
                ),
                message = "동일 requestId로 다른 퍼블리시 파라미터가 요청되었습니다."
            )
        }
    }

    /**
     * 지역 코드를 표준 형식(대문자)으로 변환한다.
     */
    private fun normalizeRegion(region: String): String {
        return region.trim().uppercase()
    }

    /**
     * 언어 코드를 표준 형식(소문자)으로 변환한다.
     */
    private fun normalizeLanguage(language: String): String {
        return language.trim().lowercase()
    }

    /**
     * 스냅샷 상태를 갱신한다.
     */
    private fun updateSnapshotStatus(
        snapshotId: String,
        update: (PublishSnapshot) -> PublishSnapshot
    ) {
        val snapshot = publishSnapshotRepository.findBySnapshotId(snapshotId)
            ?: throw SystemException(
                errorCode = ErrorCode.INTERNAL_ERROR,
                details = mapOf("snapshotId" to snapshotId),
                message = "퍼블리시 스냅샷 정보를 찾을 수 없습니다."
            )

        publishSnapshotRepository.save(update(snapshot))
    }

    /**
     * 퍼블리시 스냅샷 생성 이벤트를 Outbox에 기록한다.
     */
    private fun recordSnapshotCreatedEvent(
        command: CreateSnapshotCommand,
        savedVersion: PublishVersion,
        snapshot: PublishSnapshot,
        occurredAt: Instant
    ) {
        val payload = PublishSnapshotCreatedEventPayload(
            episodeId = savedVersion.episodeId,
            snapshotId = snapshot.snapshotId,
            publishVersion = savedVersion.version,
            region = snapshot.region,
            language = snapshot.language,
            occurredAt = occurredAt
        )

        val envelope = EventEnvelope.create(
            eventType = PublishEventTypes.PUBLISH_SNAPSHOT_CREATED,
            producer = EVENT_PRODUCER,
            payload = payload,
            idempotencyKey = command.requestId,
            occurredAt = occurredAt
        )

        val outboxEvent = OutboxEvent.pending(
            aggregateType = AGGREGATE_TYPE_EPISODE,
            aggregateId = savedVersion.episodeId.toString(),
            eventType = envelope.eventType.asString(),
            payload = serializeEnvelope(envelope, savedVersion.episodeId),
            createdAt = occurredAt
        )

        outboxEventRepository.save(outboxEvent)
    }

    /**
     * 퍼블리시 롤백 이벤트를 Outbox에 기록한다.
     */
    private fun recordSnapshotRolledBackEvent(
        command: RollbackSnapshotCommand,
        rolledBack: PublishVersion,
        activated: PublishVersion,
        occurredAt: Instant
    ) {
        val payload = PublishSnapshotRolledBackEventPayload(
            episodeId = command.episodeId,
            targetVersion = activated.version,
            previousVersion = rolledBack.version,
            targetSnapshotId = activated.snapshotId,
            rolledBackSnapshotId = rolledBack.snapshotId,
            occurredAt = occurredAt
        )

        val envelope = EventEnvelope.create(
            eventType = PublishEventTypes.PUBLISH_SNAPSHOT_ROLLED_BACK,
            producer = EVENT_PRODUCER,
            payload = payload,
            idempotencyKey = command.requestId,
            occurredAt = occurredAt
        )

        val outboxEvent = OutboxEvent.pending(
            aggregateType = AGGREGATE_TYPE_EPISODE,
            aggregateId = command.episodeId.toString(),
            eventType = envelope.eventType.asString(),
            payload = serializeEnvelope(envelope, command.episodeId),
            createdAt = occurredAt
        )

        outboxEventRepository.save(outboxEvent)
    }

    /**
     * Outbox payload 직렬화를 공통 처리한다.
     */
    private fun serializeEnvelope(envelope: EventEnvelope<*>, episodeId: Long): String {
        return try {
            objectMapper.writeValueAsString(envelope)
        } catch (exception: Exception) {
            throw SystemException(
                errorCode = ErrorCode.INTERNAL_ERROR,
                details = mapOf("episodeId" to episodeId.toString()),
                message = "Outbox 이벤트 직렬화에 실패했습니다.",
                cause = exception
            )
        }
    }
}
