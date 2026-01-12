package com.inkflow.common.outbox.application

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Outbox Relay의 동작 파라미터를 바인딩.
 */
@ConfigurationProperties("inkflow.outbox.relay")
data class OutboxRelayProperties(
    /**
     * Relay 활성화 여부를 제어한다.
     */
    var enabled: Boolean = true,
    /**
     * 한 번에 처리할 Outbox 이벤트 개수를 제한한다.
     */
    var batchSize: Int = 100,
    /**
     * 폴러 실행 간격을 지정한다.
     */
    var fixedDelay: Duration = Duration.ofSeconds(2),
    /**
     * 최초 실행까지의 지연 시간을 지정한다.
     */
    var initialDelay: Duration = Duration.ofSeconds(1)
) {
    init {
        require(batchSize > 0) { "batchSize는 1 이상이어야 합니다." }
    }
}
