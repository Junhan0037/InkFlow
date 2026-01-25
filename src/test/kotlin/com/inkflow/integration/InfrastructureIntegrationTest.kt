package com.inkflow.integration

import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveBucketArgs
import io.minio.RemoveObjectArgs
import io.minio.StatObjectArgs
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.io.ByteArrayInputStream
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Testcontainers 기반 인프라(Postgres/Mongo/Redis/Kafka/MinIO) 연동을 통합 검증한다.
 */
@SpringBootTest
@Testcontainers
class InfrastructureIntegrationTest {
    @Autowired
    private lateinit var jdbcTemplate: NamedParameterJdbcTemplate

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var minioClient: MinioClient

    /**
     * Postgres 연결이 정상 동작하는지 기본 쿼리로 확인한다.
     */
    @Test
    fun postgresConnection_isHealthy() {
        val result = jdbcTemplate.jdbcTemplate.queryForObject("SELECT 1", Int::class.java)
        assertEquals(1, result)
    }

    /**
     * MongoDB 연결이 정상 동작하는지 문서 삽입/조회로 확인한다.
     */
    @Test
    fun mongoConnection_isHealthy() {
        val collection = "integration_health"
        val document = Document(
            mapOf(
                "key" to "mongo",
                "createdAt" to Instant.now().toString()
            )
        )

        val inserted = mongoTemplate.insert(document, collection)
        val loaded = mongoTemplate.findById(
            inserted.getObjectId("_id"),
            Document::class.java,
            collection
        )

        assertNotNull(loaded)
        assertEquals("mongo", loaded!!.getString("key"))
    }

    /**
     * Redis 연결이 정상 동작하는지 키-값 쓰기/읽기로 확인한다.
     */
    @Test
    fun redisConnection_isHealthy() {
        val key = "integration:redis:${UUID.randomUUID()}"
        redisTemplate.opsForValue().set(key, "pong")
        val value = redisTemplate.opsForValue().get(key)

        assertEquals("pong", value)
    }

    /**
     * Kafka 연결이 정상 동작하는지 토픽 생성 후 produce/consume으로 확인한다.
     */
    @Test
    fun kafkaConnection_isHealthy() {
        val topic = createKafkaTestTopic()
        kafkaTemplate.send(topic, "health", "ok").get(5, TimeUnit.SECONDS)

        createKafkaConsumer().use { consumer ->
            consumer.subscribe(listOf(topic))
            val records = consumer.poll(Duration.ofSeconds(5))
            val matched = records.any { record -> record.value() == "ok" }
            assertTrue(matched)
        }
    }

    /**
     * MinIO 연결이 정상 동작하는지 버킷 생성/객체 업로드/조회로 확인한다.
     */
    @Test
    fun minioConnection_isHealthy() {
        val bucketName = createMinioBucket()
        val objectName = "health.txt"
        val payload = "ok".toByteArray()

        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(bucketName)
                .`object`(objectName)
                .stream(ByteArrayInputStream(payload), payload.size.toLong(), -1)
                .contentType("text/plain")
                .build()
        )

        val stat = minioClient.statObject(
            StatObjectArgs.builder()
                .bucket(bucketName)
                .`object`(objectName)
                .build()
        )

        assertEquals(payload.size.toLong(), stat.size())

        minioClient.removeObject(
            RemoveObjectArgs.builder()
                .bucket(bucketName)
                .`object`(objectName)
                .build()
        )
        minioClient.removeBucket(
            RemoveBucketArgs.builder()
                .bucket(bucketName)
                .build()
        )
    }

    /**
     * Kafka 테스트용 토픽을 생성해 반환한다.
     */
    private fun createKafkaTestTopic(): String {
        val topic = "integration.health.${UUID.randomUUID().toString().replace("-", "")}"
        AdminClient.create(
            mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers)
        ).use { admin ->
            admin.createTopics(listOf(NewTopic(topic, 1, 1))).all().get(10, TimeUnit.SECONDS)
        }
        return topic
    }

    /**
     * Kafka 테스트 컨슈머를 생성해 반환한다.
     */
    private fun createKafkaConsumer(): KafkaConsumer<String, String> {
        val consumerProps = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "integration-test-${UUID.randomUUID()}",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false"
        )
        return KafkaConsumer(consumerProps, StringDeserializer(), StringDeserializer())
    }

    /**
     * MinIO 테스트 버킷을 생성하고 존재 여부를 검증한 뒤 이름을 반환한다.
     */
    private fun createMinioBucket(): String {
        val bucketName = "integration-${UUID.randomUUID().toString().replace("-", "")}"
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build())
        val exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())
        assertTrue(exists)
        return bucketName
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

        @Container
        private val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))

        @Container
        private val minio = GenericContainer<Nothing>(
            DockerImageName.parse("minio/minio:RELEASE.2024-01-16T16-07-38Z")
        ).apply {
            withEnv("MINIO_ROOT_USER", "inkflow")
            withEnv("MINIO_ROOT_PASSWORD", "inkflow_pw")
            withCommand("server", "/data")
            withExposedPorts(9000)
            waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000))
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
            registry.add("spring.kafka.bootstrap-servers") { kafka.bootstrapServers }
            registry.add("inkflow.media.storage.endpoint") { "http://${minio.host}:${minio.getMappedPort(9000)}" }
            registry.add("inkflow.media.storage.access-key") { "inkflow" }
            registry.add("inkflow.media.storage.secret-key") { "inkflow_pw" }
        }
    }
}
