package com.inkflow.e2e

import com.inkflow.indexing.application.AssetSearchCommand
import com.inkflow.indexing.application.IndexSearchApplicationService
import com.inkflow.indexing.application.IndexSearchClient
import com.inkflow.indexing.application.IndexingApplicationService
import com.inkflow.indexing.application.IndexingCommand
import com.inkflow.indexing.application.IndexingMessageMetadata
import com.inkflow.indexing.application.SearchPageRequest
import com.inkflow.indexing.application.SearchResult
import com.inkflow.indexing.application.WorkSearchCommand
import com.inkflow.indexing.application.EpisodeSearchCommand
import com.inkflow.indexing.domain.AssetIndexDocument
import com.inkflow.indexing.domain.EpisodeIndexDocument
import com.inkflow.indexing.domain.IndexEntityType
import com.inkflow.indexing.domain.IndexOperation
import com.inkflow.indexing.domain.IndexingGateway
import com.inkflow.indexing.domain.WorkIndexDocument
import com.inkflow.media.application.MediaDerivativeResultService
import com.inkflow.media.application.MediaJobCommand
import com.inkflow.media.application.MediaJobMessageMetadata
import com.inkflow.media.application.MediaJobSpec
import com.inkflow.media.application.MediaThumbnailResult
import com.inkflow.media.domain.DerivativeMetadataRepository
import com.inkflow.media.domain.DerivativeStatus
import com.inkflow.publish.application.CreateSnapshotCommand
import com.inkflow.publish.application.PublishSnapshotApplicationService
import com.inkflow.publish.domain.PublishSnapshotRepository
import com.inkflow.upload.application.CompleteUploadSessionCommand
import com.inkflow.upload.application.CompletedPart
import com.inkflow.upload.application.CreateUploadSessionCommand
import com.inkflow.upload.application.UploadSessionApplicationService
import com.inkflow.upload.domain.AssetStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 업로드 → 파생 → 퍼블리시 → 검색 E2E 시나리오를 통합 검증한다.
 */
@SpringBootTest
@Testcontainers
class UploadToSearchE2eTest {
    @Autowired
    private lateinit var uploadSessionApplicationService: UploadSessionApplicationService

    @Autowired
    private lateinit var mediaDerivativeResultService: MediaDerivativeResultService

    @Autowired
    private lateinit var publishSnapshotApplicationService: PublishSnapshotApplicationService

    @Autowired
    private lateinit var publishSnapshotRepository: PublishSnapshotRepository

    @Autowired
    private lateinit var derivativeMetadataRepository: DerivativeMetadataRepository

    @Autowired
    private lateinit var indexingApplicationService: IndexingApplicationService

    @Autowired
    private lateinit var indexSearchApplicationService: IndexSearchApplicationService

    @Autowired
    private lateinit var jdbcTemplate: NamedParameterJdbcTemplate

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    /**
     * 테스트 간 데이터 충돌을 방지하기 위해 저장소를 초기화한다.
     */
    @BeforeEach
    fun resetData() {
        jdbcTemplate.jdbcTemplate.execute(
            """
            TRUNCATE TABLE outbox_event, derivative, publish_version, asset, upload_session, episode, work
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )

        if (mongoTemplate.collectionExists("publish_snapshots")) {
            mongoTemplate.remove(Query(), "publish_snapshots")
        }

        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
    }

    /**
     * 업로드 → 파생 → 퍼블리시 → 검색 흐름을 E2E로 검증한다.
     */
    @Test
    fun uploadToDerivativePublishSearch_e2e() {
        seedWorkAndEpisode(workId = 1L, episodeId = 1L, creatorId = "creator-1")

        val creationResult = uploadSessionApplicationService.createSession(
            CreateUploadSessionCommand(
                episodeId = 1L,
                creatorId = "creator-1",
                fileName = "image.png",
                contentType = "image/png",
                size = 20L,
                checksum = "checksum-1",
                totalParts = 1
            )
        )

        val completionResult = uploadSessionApplicationService.completeSession(
            CompleteUploadSessionCommand(
                uploadId = creationResult.uploadId,
                creatorId = "creator-1",
                uploadedParts = listOf(CompletedPart(partNumber = 1, etag = "etag-1")),
                checksum = "checksum-1"
            )
        )

        val derivative = mediaDerivativeResultService.recordThumbnailResult(
            command = MediaJobCommand(
                jobId = "job-1",
                assetId = completionResult.assetId,
                derivativeType = "THUMBNAIL",
                spec = MediaJobSpec(width = 320, height = 320, format = "jpg")
            ),
            metadata = MediaJobMessageMetadata(
                eventId = UUID.randomUUID(),
                traceId = "trace-e2e",
                idempotencyKey = "media-job-1"
            ),
            storageKey = "derivatives/thumbnails/asset-${completionResult.assetId}.jpg",
            thumbnailResult = MediaThumbnailResult(
                bytes = byteArrayOf(1, 2, 3),
                contentType = "image/jpeg",
                format = "jpg",
                width = 320,
                height = 320
            ),
            durationMs = 12L
        )

        assertEquals(DerivativeStatus.READY, derivative.status)
        assertNotNull(
            derivativeMetadataRepository.findBySpec(
                assetId = completionResult.assetId,
                type = derivative.type,
                width = derivative.width,
                height = derivative.height,
                format = derivative.format
            )
        )

        val snapshotResult = publishSnapshotApplicationService.createSnapshot(
            CreateSnapshotCommand(
                episodeId = 1L,
                region = "KR",
                language = "ko",
                requestId = "publish-request-1"
            )
        )

        val snapshot = publishSnapshotRepository.findBySnapshotId(snapshotResult.snapshotId)
        assertNotNull(snapshot)

        val indexingMetadata = IndexingMessageMetadata(
            eventId = UUID.randomUUID(),
            traceId = "trace-e2e",
            idempotencyKey = "indexing-1"
        )

        indexingApplicationService.handleIndexRequest(
            IndexingCommand(IndexEntityType.WORK, 1L, IndexOperation.UPSERT),
            indexingMetadata
        )
        indexingApplicationService.handleIndexRequest(
            IndexingCommand(IndexEntityType.EPISODE, 1L, IndexOperation.UPSERT),
            indexingMetadata
        )
        indexingApplicationService.handleIndexRequest(
            IndexingCommand(IndexEntityType.ASSET, completionResult.assetId, IndexOperation.UPSERT),
            indexingMetadata
        )

        val searchResult = indexSearchApplicationService.searchAssets(
            AssetSearchCommand(
                keyword = "image",
                episodeId = 1L,
                status = AssetStatus.STORED.name,
                contentType = "image/png",
                pageRequest = SearchPageRequest(page = 0, size = 10)
            )
        )

        assertEquals(1L, searchResult.total)
        assertEquals(completionResult.assetId, searchResult.items.first().id)
        assertEquals("image.png", searchResult.items.first().fileName)
    }

    /**
     * Work/Episode 초기 데이터를 삽입한다.
     */
    private fun seedWorkAndEpisode(workId: Long, episodeId: Long, creatorId: String) {
        val now = Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"))
        jdbcTemplate.update(
            """
            INSERT INTO work (id, title, creator_id, status, default_language, created_at, updated_at)
            VALUES (:id, :title, :creatorId, :status, :defaultLanguage, :createdAt, :updatedAt)
            """.trimIndent(),
            mapOf(
                "id" to workId,
                "title" to "테스트 작품",
                "creatorId" to creatorId,
                "status" to "ACTIVE",
                "defaultLanguage" to "ko",
                "createdAt" to now,
                "updatedAt" to now
            )
        )
        jdbcTemplate.update(
            """
            INSERT INTO episode (id, work_id, title, seq, published_at, created_at, updated_at)
            VALUES (:id, :workId, :title, :seq, :publishedAt, :createdAt, :updatedAt)
            """.trimIndent(),
            mapOf(
                "id" to episodeId,
                "workId" to workId,
                "title" to "테스트 회차",
                "seq" to 1,
                "publishedAt" to null,
                "createdAt" to now,
                "updatedAt" to now
            )
        )
    }

    /**
     * 테스트에서 사용하는 인메모리 색인 저장소를 구성한다.
     */
    @TestConfiguration
    class InMemoryIndexingTestConfig {
        @Bean
        @Primary
        fun indexStore(): InMemoryIndexStore = InMemoryIndexStore()

        @Bean
        @Primary
        fun indexingGateway(store: InMemoryIndexStore): IndexingGateway {
            return InMemoryIndexingGateway(store)
        }

        @Bean
        @Primary
        fun indexSearchClient(store: InMemoryIndexStore): IndexSearchClient {
            return InMemoryIndexSearchClient(store)
        }
    }

    /**
     * 색인 결과를 보관하는 인메모리 저장소.
     */
    class InMemoryIndexStore {
        private val workDocuments = ConcurrentHashMap<Long, WorkIndexDocument>()
        private val episodeDocuments = ConcurrentHashMap<Long, EpisodeIndexDocument>()
        private val assetDocuments = ConcurrentHashMap<Long, AssetIndexDocument>()

        /**
         * Work 문서를 저장한다.
         */
        fun upsertWork(document: WorkIndexDocument) {
            workDocuments[document.id] = document
        }

        /**
         * Episode 문서를 저장한다.
         */
        fun upsertEpisode(document: EpisodeIndexDocument) {
            episodeDocuments[document.id] = document
        }

        /**
         * Asset 문서를 저장한다.
         */
        fun upsertAsset(document: AssetIndexDocument) {
            assetDocuments[document.id] = document
        }

        /**
         * Work 문서를 삭제한다.
         */
        fun deleteWork(workId: Long) {
            workDocuments.remove(workId)
        }

        /**
         * Episode 문서를 삭제한다.
         */
        fun deleteEpisode(episodeId: Long) {
            episodeDocuments.remove(episodeId)
        }

        /**
         * Asset 문서를 삭제한다.
         */
        fun deleteAsset(assetId: Long) {
            assetDocuments.remove(assetId)
        }

        /**
         * Work 문서 목록을 조회한다.
         */
        fun listWorks(): List<WorkIndexDocument> = workDocuments.values.toList()

        /**
         * Episode 문서 목록을 조회한다.
         */
        fun listEpisodes(): List<EpisodeIndexDocument> = episodeDocuments.values.toList()

        /**
         * Asset 문서 목록을 조회한다.
         */
        fun listAssets(): List<AssetIndexDocument> = assetDocuments.values.toList()
    }

    /**
     * 인메모리 색인 게이트웨이 구현.
     */
    class InMemoryIndexingGateway(
        private val store: InMemoryIndexStore
    ) : IndexingGateway {
        /**
         * Work 문서를 저장한다.
         */
        override fun upsertWork(document: WorkIndexDocument) {
            store.upsertWork(document)
        }

        /**
         * Episode 문서를 저장한다.
         */
        override fun upsertEpisode(document: EpisodeIndexDocument) {
            store.upsertEpisode(document)
        }

        /**
         * Asset 문서를 저장한다.
         */
        override fun upsertAsset(document: AssetIndexDocument) {
            store.upsertAsset(document)
        }

        /**
         * Work 문서를 삭제한다.
         */
        override fun deleteWork(workId: Long) {
            store.deleteWork(workId)
        }

        /**
         * Episode 문서를 삭제한다.
         */
        override fun deleteEpisode(episodeId: Long) {
            store.deleteEpisode(episodeId)
        }

        /**
         * Asset 문서를 삭제한다.
         */
        override fun deleteAsset(assetId: Long) {
            store.deleteAsset(assetId)
        }
    }

    /**
     * 인메모리 검색 클라이언트 구현.
     */
    class InMemoryIndexSearchClient(
        private val store: InMemoryIndexStore
    ) : IndexSearchClient {
        /**
         * Work 검색을 수행한다.
         */
        override fun searchWorks(command: WorkSearchCommand): SearchResult<WorkIndexDocument> {
            val keyword = command.keyword?.takeIf { it.isNotBlank() }
            val filtered = store.listWorks()
                .filter { document ->
                    val keywordOk = keyword == null ||
                        document.title.contains(keyword, ignoreCase = true)
                    val statusOk = command.status == null || document.status == command.status
                    val languageOk = command.language == null || document.defaultLanguage == command.language
                    val creatorOk = command.creatorId == null || document.creatorId == command.creatorId
                    keywordOk && statusOk && languageOk && creatorOk
                }
                .sortedByDescending { it.updatedAt }

            return paginate(filtered, command.pageRequest)
        }

        /**
         * Episode 검색을 수행한다.
         */
        override fun searchEpisodes(command: EpisodeSearchCommand): SearchResult<EpisodeIndexDocument> {
            val keyword = command.keyword?.takeIf { it.isNotBlank() }
            val filtered = store.listEpisodes()
                .filter { document ->
                    val keywordOk = keyword == null ||
                        document.title.contains(keyword, ignoreCase = true)
                    val workOk = command.workId == null || document.workId == command.workId
                    keywordOk && workOk
                }
                .sortedByDescending { it.updatedAt }

            return paginate(filtered, command.pageRequest)
        }

        /**
         * Asset 검색을 수행한다.
         */
        override fun searchAssets(command: AssetSearchCommand): SearchResult<AssetIndexDocument> {
            val keyword = command.keyword?.takeIf { it.isNotBlank() }
            val filtered = store.listAssets()
                .filter { document ->
                    val keywordOk = keyword == null ||
                        document.fileName.contains(keyword, ignoreCase = true)
                    val episodeOk = command.episodeId == null || document.episodeId == command.episodeId
                    val statusOk = command.status == null || document.status == command.status
                    val contentTypeOk = command.contentType == null || document.contentType == command.contentType
                    keywordOk && episodeOk && statusOk && contentTypeOk
                }
                .sortedByDescending { it.updatedAt }

            return paginate(filtered, command.pageRequest)
        }

        /**
         * 인메모리 목록을 페이지 형태로 변환한다.
         */
        private fun <T> paginate(items: List<T>, pageRequest: SearchPageRequest): SearchResult<T> {
            val start = pageRequest.offset()
            val end = (start + pageRequest.size).coerceAtMost(items.size)
            val pageItems = if (start >= items.size) emptyList() else items.subList(start, end)
            return SearchResult.of(pageItems, items.size.toLong(), pageRequest.page, pageRequest.size)
        }
    }

    companion object {
        @Container
        private val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
            withDatabaseName("inkflow_test")
            withUsername("inkflow")
            withPassword("inkflow_pw")
        }

        @Container
        private val mongo = MongoDBContainer("mongo:7.0")

        @Container
        private val redis = GenericContainer<Nothing>("redis:7-alpine").apply {
            withExposedPorts(6379)
        }

        /**
         * Testcontainers 접속 정보를 스프링 환경 속성으로 등록한다.
         */
        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.mongodb.uri") { mongo.replicaSetUrl }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("inkflow.kafka.enabled") { "false" }
        }
    }
}
