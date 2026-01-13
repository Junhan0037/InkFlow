package com.inkflow.common.kafka.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Kafka 토픽/파티션 전략을 설정 값으로 정의한다.
 */
@ConfigurationProperties("inkflow.kafka")
data class InkflowKafkaProperties(
    val enabled: Boolean = true,
    val replicationFactor: Int = 1,
    val topics: Topics = Topics(),
    val partitions: Partitions = Partitions()
) {
    init {
        // 토픽 생성 시 파티션/복제 계수는 1 이상이어야 한다.
        require(replicationFactor > 0) { "replicationFactor는 1 이상이어야 합니다." }
        require(partitions.assetEvents > 0) { "assetEvents 파티션 수는 1 이상이어야 합니다." }
        require(partitions.workflowEvents > 0) { "workflowEvents 파티션 수는 1 이상이어야 합니다." }
        require(partitions.mediaJobs > 0) { "mediaJobs 파티션 수는 1 이상이어야 합니다." }
        require(partitions.mediaResults > 0) { "mediaResults 파티션 수는 1 이상이어야 합니다." }
        require(partitions.indexEvents > 0) { "indexEvents 파티션 수는 1 이상이어야 합니다." }
        require(partitions.dlq > 0) { "dlq 파티션 수는 1 이상이어야 합니다." }
        require(topics.dlqPrefix.isNotBlank()) { "dlqPrefix는 비어 있을 수 없습니다." }
    }

    /**
     * InkFlow 도메인별 토픽 이름을 정의한다.
     */
    data class Topics(
        val assetEvents: String = "asset.events",
        val workflowEvents: String = "workflow.events",
        val mediaJobs: String = "media.jobs",
        val mediaResults: String = "media.results",
        val indexEvents: String = "index.events",
        val dlqPrefix: String = "dlq"
    )

    /**
     * 토픽별 파티션 수를 정의한다.
     */
    data class Partitions(
        val assetEvents: Int = 3,
        val workflowEvents: Int = 3,
        val mediaJobs: Int = 6,
        val mediaResults: Int = 3,
        val indexEvents: Int = 3,
        val dlq: Int = 1
    )
}
