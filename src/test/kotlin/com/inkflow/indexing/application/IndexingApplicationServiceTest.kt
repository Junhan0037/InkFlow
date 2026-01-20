package com.inkflow.indexing.application

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.indexing.domain.AssetIndexDocument
import com.inkflow.indexing.domain.AssetIndexSource
import com.inkflow.indexing.domain.EpisodeIndexDocument
import com.inkflow.indexing.domain.EpisodeIndexSource
import com.inkflow.indexing.domain.IndexEntityType
import com.inkflow.indexing.domain.IndexOperation
import com.inkflow.indexing.domain.IndexSourceRepository
import com.inkflow.indexing.domain.IndexingGateway
import com.inkflow.indexing.domain.WorkIndexDocument
import com.inkflow.indexing.domain.WorkIndexSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * IndexingApplicationService의 색인 분기 로직을 검증한다.
 */
class IndexingApplicationServiceTest {
    private val baseTime: Instant = Instant.parse("2024-01-01T00:00:00Z")

    /**
     * Work UPSERT 요청이 게이트웨이에 전달되는지 확인한다.
     */
    @Test
    fun handleIndexRequest_upsertWork_callsGateway() {
        val source = buildWorkSource(workId = 1L)
        val repository = TrackingIndexSourceRepository(works = listOf(source))
        val gateway = CapturingIndexingGateway()
        val service = IndexingApplicationService(repository, gateway)
        val command = IndexingCommand(
            entityType = IndexEntityType.WORK,
            entityId = 1L,
            operation = IndexOperation.UPSERT
        )

        service.handleIndexRequest(command, buildMetadata())

        assertTrue(repository.findWorkCalled)
        assertEquals(WorkIndexDocument(
            id = source.id,
            title = source.title,
            creatorId = source.creatorId,
            status = source.status,
            defaultLanguage = source.defaultLanguage,
            createdAt = source.createdAt,
            updatedAt = source.updatedAt
        ), gateway.upsertedWork)
    }

    /**
     * Episode DELETE 요청이 저장소 조회 없이 실행되는지 확인한다.
     */
    @Test
    fun handleIndexRequest_deleteEpisode_callsGateway() {
        val repository = TrackingIndexSourceRepository()
        val gateway = CapturingIndexingGateway()
        val service = IndexingApplicationService(repository, gateway)
        val command = IndexingCommand(
            entityType = IndexEntityType.EPISODE,
            entityId = 99L,
            operation = IndexOperation.DELETE
        )

        service.handleIndexRequest(command, buildMetadata())

        assertEquals(99L, gateway.deletedEpisodeId)
        assertFalse(repository.findEpisodeCalled)
    }

    /**
     * 색인 원천이 없으면 NOT_FOUND 오류를 반환하는지 확인한다.
     */
    @Test
    fun handleIndexRequest_throwsNotFound_whenSourceMissing() {
        val repository = TrackingIndexSourceRepository()
        val gateway = CapturingIndexingGateway()
        val service = IndexingApplicationService(repository, gateway)
        val command = IndexingCommand(
            entityType = IndexEntityType.WORK,
            entityId = 999L,
            operation = IndexOperation.UPSERT
        )

        val exception = assertThrows(BusinessException::class.java) {
            service.handleIndexRequest(command, buildMetadata())
        }

        assertEquals(ErrorCode.NOT_FOUND, exception.errorCode)
        assertEquals("WORK", exception.details["entityType"])
        assertEquals("999", exception.details["entityId"])
    }

    /**
     * 테스트용 Work 색인 원천 데이터를 생성한다.
     */
    private fun buildWorkSource(workId: Long): WorkIndexSource {
        return WorkIndexSource(
            id = workId,
            title = "작품-${workId}",
            creatorId = "creator-1",
            status = "DRAFT",
            defaultLanguage = "ko",
            createdAt = baseTime,
            updatedAt = baseTime
        )
    }

    /**
     * 테스트용 메시지 메타데이터를 생성한다.
     */
    private fun buildMetadata(): IndexingMessageMetadata {
        return IndexingMessageMetadata(
            eventId = UUID.fromString("00000000-0000-0000-0000-000000000101"),
            traceId = "trace-1",
            idempotencyKey = "idem-1"
        )
    }

    /**
     * 테스트용 색인 원천 저장소로 조회 여부를 추적한다.
     */
    private class TrackingIndexSourceRepository(
        works: List<WorkIndexSource> = emptyList(),
        episodes: List<EpisodeIndexSource> = emptyList(),
        assets: List<AssetIndexSource> = emptyList()
    ) : IndexSourceRepository {
        private val workStore = works.associateBy { it.id }
        private val episodeStore = episodes.associateBy { it.id }
        private val assetStore = assets.associateBy { it.id }
        var findWorkCalled: Boolean = false
            private set
        var findEpisodeCalled: Boolean = false
            private set
        var findAssetCalled: Boolean = false
            private set

        /**
         * Work 색인 원천을 조회하고 호출 여부를 기록한다.
         */
        override fun findWork(workId: Long): WorkIndexSource? {
            findWorkCalled = true
            return workStore[workId]
        }

        /**
         * Episode 색인 원천을 조회하고 호출 여부를 기록한다.
         */
        override fun findEpisode(episodeId: Long): EpisodeIndexSource? {
            findEpisodeCalled = true
            return episodeStore[episodeId]
        }

        /**
         * Asset 색인 원천을 조회하고 호출 여부를 기록한다.
         */
        override fun findAsset(assetId: Long): AssetIndexSource? {
            findAssetCalled = true
            return assetStore[assetId]
        }
    }

    /**
     * 테스트용 색인 게이트웨이로 호출 값을 저장한다.
     */
    private class CapturingIndexingGateway : IndexingGateway {
        var upsertedWork: WorkIndexDocument? = null
            private set
        var upsertedEpisode: EpisodeIndexDocument? = null
            private set
        var upsertedAsset: AssetIndexDocument? = null
            private set
        var deletedWorkId: Long? = null
            private set
        var deletedEpisodeId: Long? = null
            private set
        var deletedAssetId: Long? = null
            private set

        /**
         * Work UPSERT 요청을 저장한다.
         */
        override fun upsertWork(document: WorkIndexDocument) {
            upsertedWork = document
        }

        /**
         * Episode UPSERT 요청을 저장한다.
         */
        override fun upsertEpisode(document: EpisodeIndexDocument) {
            upsertedEpisode = document
        }

        /**
         * Asset UPSERT 요청을 저장한다.
         */
        override fun upsertAsset(document: AssetIndexDocument) {
            upsertedAsset = document
        }

        /**
         * Work 삭제 요청을 저장한다.
         */
        override fun deleteWork(workId: Long) {
            deletedWorkId = workId
        }

        /**
         * Episode 삭제 요청을 저장한다.
         */
        override fun deleteEpisode(episodeId: Long) {
            deletedEpisodeId = episodeId
        }

        /**
         * Asset 삭제 요청을 저장한다.
         */
        override fun deleteAsset(assetId: Long) {
            deletedAssetId = assetId
        }
    }
}
