package com.inkflow.common.kafka.dlq

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.stereotype.Component

/**
 * Kafka 메시지를 DLQ로 전달하는 퍼블리셔.
 */
@Component
class DlqPublisher(
    private val recoverer: DeadLetterPublishingRecoverer
) {
    /**
     * 실패한 레코드를 DLQ 토픽으로 전송한다.
     */
    fun publish(record: ConsumerRecord<String, String>, exception: Exception) {
        // DeadLetterPublishingRecoverer의 표준 헤더 구성을 활용한다.
        recoverer.accept(record, exception)
    }
}
