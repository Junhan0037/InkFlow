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
    var initialDelay: Duration = Duration.ofSeconds(1),
    /**
     * Outbox 재시도 최대 횟수를 지정한다.
     */
    var maxRetries: Int = 5,
    /**
     * 재시도 지연 시간의 기본값을 지정한다.
     */
    var retryInitialDelay: Duration = Duration.ofSeconds(2),
    /**
     * 재시도 지연 시간의 상한을 지정한다.
     */
    var retryMaxDelay: Duration = Duration.ofMinutes(1),
    /**
     * 재시도 지연 시간 증가 배율을 지정한다.
     */
    var retryMultiplier: Double = 2.0,
    /**
     * 지연 시간에 적용할 지터 비율(0~1)을 지정한다.
     */
    var retryJitterRatio: Double = 0.2
) {
    init {
        require(batchSize > 0) { "batchSize는 1 이상이어야 합니다." }
        require(maxRetries >= 0) { "maxRetries는 0 이상이어야 합니다." }
        require(retryInitialDelay.toMillis() > 0) { "retryInitialDelay는 0보다 커야 합니다." }
        require(retryMaxDelay >= retryInitialDelay) { "retryMaxDelay는 retryInitialDelay 이상이어야 합니다." }
        require(retryMultiplier >= 1.0) { "retryMultiplier는 1.0 이상이어야 합니다." }
        require(retryJitterRatio in 0.0..1.0) { "retryJitterRatio는 0.0~1.0 범위여야 합니다." }
    }
}
