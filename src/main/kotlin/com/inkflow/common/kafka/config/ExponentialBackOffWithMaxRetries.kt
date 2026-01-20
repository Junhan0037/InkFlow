package com.inkflow.common.kafka.config

import org.springframework.util.backoff.BackOff
import org.springframework.util.backoff.BackOffExecution
import kotlin.math.max
import kotlin.math.min

/**
 * 지수 백오프를 적용하면서 최대 재시도 횟수를 제한하는 BackOff 구현체.
 */
class ExponentialBackOffWithMaxRetries(
    private val maxRetries: Int
) : BackOff {
    var initialInterval: Long = 1000L
    var maxInterval: Long = 30_000L
    var multiplier: Double = 2.0

    init {
        require(maxRetries >= 0) { "maxRetries는 0 이상이어야 합니다." }
    }

    /**
     * 재시도 실행 컨텍스트를 생성한다.
     */
    override fun start(): BackOffExecution {
        validate()
        return ExponentialExecution(
            maxRetries = maxRetries,
            initialInterval = initialInterval,
            maxInterval = maxInterval,
            multiplier = multiplier
        )
    }

    /**
     * 설정 값을 검증한다.
     */
    private fun validate() {
        require(initialInterval > 0) { "initialInterval은 0보다 커야 합니다." }
        require(maxInterval >= initialInterval) { "maxInterval은 initialInterval 이상이어야 합니다." }
        require(multiplier >= 1.0) { "multiplier는 1.0 이상이어야 합니다." }
    }

    /**
     * 지수 백오프 재시도 계산을 담당한다.
     */
    private class ExponentialExecution(
        private val maxRetries: Int,
        initialInterval: Long,
        private val maxInterval: Long,
        private val multiplier: Double
    ) : BackOffExecution {
        private var attempt: Int = 0
        private var currentInterval: Long = max(1L, initialInterval)

        /**
         * 다음 재시도까지의 대기 시간을 반환한다.
         */
        override fun nextBackOff(): Long {
            if (attempt >= maxRetries) {
                return BackOffExecution.STOP
            }
            val interval = currentInterval
            currentInterval = min((currentInterval * multiplier).toLong(), maxInterval)
            attempt += 1
            return interval
        }
    }
}
