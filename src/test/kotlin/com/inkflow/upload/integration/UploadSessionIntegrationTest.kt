package com.inkflow.upload.integration

import com.inkflow.common.outbox.infra.jpa.OutboxEventJpaRepository
import com.inkflow.upload.application.CompleteUploadSessionCommand
import com.inkflow.upload.application.CreateUploadSessionCommand
import com.inkflow.upload.application.UploadSessionApplicationService
import com.inkflow.upload.domain.AssetMetadataRepository
import com.inkflow.upload.domain.AssetStatus
import com.inkflow.upload.domain.UploadSessionCacheRepository
import com.inkflow.upload.domain.UploadSessionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Timestamp
import java.time.Instant

/**
 * PostgreSQL/Redis Testcontainers 환경에서 업로드 세션 흐름을 통합 검증한다.
 */
@SpringBootTest
@Testcontainers
class UploadSessionIntegrationTest {
    @Autowired
    private lateinit var uploadSessionApplicationService: UploadSessionApplicationService

    @Autowired
    private lateinit var uploadSessionRepository: UploadSessionRepository

    @Autowired
    private lateinit var uploadSessionCacheRepository: UploadSessionCacheRepository

    @Autowired
    private lateinit var assetMetadataRepository: AssetMetadataRepository

    @Autowired
    private lateinit var outboxEventJpaRepository: OutboxEventJpaRepository

    @Autowired
    private lateinit var jdbcTemplate: NamedParameterJdbcTemplate

    /**
     * 테스트마다 테이블을 정리해 독립성을 보장한다.
     */
    @BeforeEach
    fun resetData() {
        jdbcTemplate.jdbcTemplate.execute(
            "TRUNCATE TABLE outbox_event, asset, upload_session, episode, work RESTART IDENTITY CASCADE"
        )
    }

    /**
     * 업로드 세션 생성/완료가 DB/Redis에 정상 반영되는지 확인한다.
     */
    @Test
    fun createAndCompleteSession_persistsAndCaches() {
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

        val storedSession = uploadSessionRepository.findByUploadId(creationResult.uploadId)
        val cachedSession = uploadSessionCacheRepository.findByUploadId(creationResult.uploadId)
        assertNotNull(storedSession)
        assertNotNull(cachedSession)

        val completionResult = uploadSessionApplicationService.completeSession(
            CompleteUploadSessionCommand(
                uploadId = creationResult.uploadId,
                creatorId = "creator-1",
                uploadedParts = listOf(
                    com.inkflow.upload.application.CompletedPart(partNumber = 1, etag = "etag-1")
                ),
                checksum = "checksum-1"
            )
        )

        val asset = assetMetadataRepository.findById(completionResult.assetId)
        assertNotNull(asset)
        assertEquals(AssetStatus.STORED, asset!!.status)
        assertEquals(1L, outboxEventJpaRepository.count())
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

    companion object {
        @Container
        private val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
            withDatabaseName("inkflow_test")
            withUsername("inkflow")
            withPassword("inkflow_pw")
        }

        @Container
        private val redis = GenericContainer<Nothing>("redis:7-alpine").apply {
            withExposedPorts(6379)
        }

        /**
         * Testcontainers 정보를 스프링 설정으로 전달한다.
         */
        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }
}
