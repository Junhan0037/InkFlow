package com.inkflow.dlq.infra.kafka

import com.inkflow.dlq.application.DlqMessageApplicationService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * DLQ 토픽을 구독해 메시지를 저장하는 Kafka 컨슈머.
 */
@Component
@ConditionalOnProperty(prefix = "inkflow.kafka", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class DlqMessageConsumer(
    private val dlqMessageApplicationService: DlqMessageApplicationService
) {
    /**
     * dlq.* 토픽에서 실패 메시지를 수신해 저장한다.
     */
    @KafkaListener(
        topicPattern = "\${inkflow.kafka.topics.dlq-prefix:dlq}\\..*",
        groupId = "\${inkflow.kafka.consumer.dlq-group-id:dlq-consumer}"
    )
    fun consume(record: ConsumerRecord<String, String>) {
        // DLQ 메시지는 저장에 실패하면 재시도가 필요하므로 예외를 전파한다.
        dlqMessageApplicationService.capture(record)
    }
}
