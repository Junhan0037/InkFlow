package com.inkflow.common.kafka.config

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

/**
 * Kafka 토픽/파티션 전략을 실제 토픽으로 생성하는 구성.
 */
@Configuration
@EnableConfigurationProperties(InkflowKafkaProperties::class)
class KafkaTopicConfig(
    private val properties: InkflowKafkaProperties
) {
    /**
     * Asset 이벤트 토픽을 생성한다.
     */
    @Bean
    fun assetEventsTopic(): NewTopic {
        return buildTopic(
            name = properties.topics.assetEvents,
            partitions = properties.partitions.assetEvents
        )
    }

    /**
     * Workflow 이벤트 토픽을 생성한다.
     */
    @Bean
    fun workflowEventsTopic(): NewTopic {
        return buildTopic(
            name = properties.topics.workflowEvents,
            partitions = properties.partitions.workflowEvents
        )
    }

    /**
     * Media 작업 요청 토픽을 생성한다.
     */
    @Bean
    fun mediaJobsTopic(): NewTopic {
        return buildTopic(
            name = properties.topics.mediaJobs,
            partitions = properties.partitions.mediaJobs
        )
    }

    /**
     * Media 작업 결과 토픽을 생성한다.
     */
    @Bean
    fun mediaResultsTopic(): NewTopic {
        return buildTopic(
            name = properties.topics.mediaResults,
            partitions = properties.partitions.mediaResults
        )
    }

    /**
     * Index 이벤트 토픽을 생성한다.
     */
    @Bean
    fun indexEventsTopic(): NewTopic {
        return buildTopic(
            name = properties.topics.indexEvents,
            partitions = properties.partitions.indexEvents
        )
    }

    /**
     * Asset 이벤트 DLQ 토픽을 생성한다.
     */
    @Bean
    fun dlqAssetEventsTopic(): NewTopic {
        return buildDlqTopic(properties.topics.assetEvents)
    }

    /**
     * Workflow 이벤트 DLQ 토픽을 생성한다.
     */
    @Bean
    fun dlqWorkflowEventsTopic(): NewTopic {
        return buildDlqTopic(properties.topics.workflowEvents)
    }

    /**
     * Media 작업 요청 DLQ 토픽을 생성한다.
     */
    @Bean
    fun dlqMediaJobsTopic(): NewTopic {
        return buildDlqTopic(properties.topics.mediaJobs)
    }

    /**
     * Media 작업 결과 DLQ 토픽을 생성한다.
     */
    @Bean
    fun dlqMediaResultsTopic(): NewTopic {
        return buildDlqTopic(properties.topics.mediaResults)
    }

    /**
     * Index 이벤트 DLQ 토픽을 생성한다.
     */
    @Bean
    fun dlqIndexEventsTopic(): NewTopic {
        return buildDlqTopic(properties.topics.indexEvents)
    }

    /**
     * 공통 토픽 생성 로직을 캡슐화한다.
     */
    private fun buildTopic(name: String, partitions: Int): NewTopic {
        return TopicBuilder.name(name)
            .partitions(partitions)
            .replicas(properties.replicationFactor)
            .build()
    }

    /**
     * DLQ 토픽은 `dlqPrefix.{baseTopic}` 규칙으로 생성한다.
     */
    private fun buildDlqTopic(baseTopic: String): NewTopic {
        val dlqName = "${properties.topics.dlqPrefix}.${baseTopic}"
        return buildTopic(
            name = dlqName,
            partitions = properties.partitions.dlq
        )
    }
}
