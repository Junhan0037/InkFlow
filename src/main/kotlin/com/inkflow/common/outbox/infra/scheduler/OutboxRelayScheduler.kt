package com.inkflow.common.outbox.infra.scheduler

import com.inkflow.common.outbox.application.OutboxRelayProperties
import com.inkflow.common.outbox.application.OutboxRelayService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Outbox Relay를 주기적으로 실행하는 스케줄러.
 */
@Component
class OutboxRelayScheduler(
    private val outboxRelayService: OutboxRelayService,
    private val properties: OutboxRelayProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 설정된 주기로 Outbox 이벤트를 폴링한다.
     */
    @Scheduled(
        fixedDelayString = "\${inkflow.outbox.relay.fixed-delay:PT2S}",
        initialDelayString = "\${inkflow.outbox.relay.initial-delay:PT1S}"
    )
    fun pollOutboxEvents() {
        if (!properties.enabled) {
            return
        }

        try {
            outboxRelayService.relayPendingEvents()
        } catch (exception: Exception) {
            // 스케줄러 루프가 중단되지 않도록 오류를 로깅한다.
            logger.error("Outbox Relay 실행 중 예외가 발생했습니다.", exception)
        }
    }
}
