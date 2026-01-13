package com.inkflow.common.outbox.application

import com.inkflow.common.error.InkFlowException
import com.inkflow.common.outbox.domain.OutboxEvent
import com.inkflow.common.outbox.domain.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Outbox 이벤트를 조회해 발행하고 상태를 갱신하는 애플리케이션 서비스.
 */
@Service
class OutboxRelayService(
    private val outboxEventRepository: OutboxEventRepository,
    private val outboxEventPublisher: OutboxEventPublisher,
    private val properties: OutboxRelayProperties,
    private val transactionTemplate: TransactionTemplate
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Outbox 이벤트를 배치로 폴링하고 발행한다.
     */
    fun relayPendingEvents() {
        val now = Instant.now()
        val lockExpiredBefore = now.minus(properties.lockTimeout)
        val pendingEvents = claimPendingEvents(now, lockExpiredBefore)
        if (pendingEvents.isEmpty()) {
            return
        }

        // 동일 배치 내 이벤트는 오래된 순서로 처리해 지연을 줄인다.
        pendingEvents.forEach { event ->
            handleEvent(event)
        }
    }

    /**
     * 전송 대상 이벤트를 잠금 처리해 중복 발행을 방지한다.
     */
    private fun claimPendingEvents(now: Instant, lockExpiredBefore: Instant): List<OutboxEvent> {
        return transactionTemplate.execute { _ ->
            val events = outboxEventRepository.findPendingEventsForUpdate(
                limit = properties.batchSize,
                now = now,
                lockExpiredBefore = lockExpiredBefore
            )
            if (events.isEmpty()) {
                return@execute emptyList()
            }
            events.forEach { event ->
                outboxEventRepository.markSending(event.id, now)
            }
            events
        } ?: emptyList()
    }

    /**
     * 이벤트 발행 결과에 따라 상태를 갱신한다.
     */
    private fun handleEvent(event: OutboxEvent) {
        try {
            outboxEventPublisher.publish(event)
            transactionTemplate.executeWithoutResult {
                outboxEventRepository.markSent(event.id, Instant.now())
            }
            logger.info(
                "Outbox 이벤트 발행 완료. eventId={}, eventType={}, aggregateType={}, aggregateId={}",
                event.id,
                event.eventType,
                event.aggregateType,
                event.aggregateId
            )
        } catch (exception: Exception) {
            handlePublishFailure(event, exception)
        }
    }

    /**
     * 발행 실패 시 재시도 정책에 따라 상태를 갱신한다.
     */
    private fun handlePublishFailure(event: OutboxEvent, exception: Exception) {
        val retryCount = event.retryCount + 1
        val lastError = buildLastError(exception)

        if (!isRetryable(exception) || retryCount > properties.maxRetries) {
            transactionTemplate.executeWithoutResult {
                outboxEventRepository.markFailed(event.id, lastError)
            }
            logger.error(
                "Outbox 이벤트 발행 실패(종료). eventId={}, eventType={}, aggregateType={}, aggregateId={}, retryCount={}",
                event.id,
                event.eventType,
                event.aggregateType,
                event.aggregateId,
                retryCount,
                exception
            )
            return
        }

        val nextRetryAt = calculateNextRetryAt(Instant.now(), retryCount)
        transactionTemplate.executeWithoutResult {
            outboxEventRepository.markRetry(event.id, retryCount, nextRetryAt, lastError)
        }
        logger.warn(
            "Outbox 이벤트 발행 실패(재시도 예정). eventId={}, eventType={}, aggregateType={}, aggregateId={}, retryCount={}, nextRetryAt={}",
            event.id,
            event.eventType,
            event.aggregateType,
            event.aggregateId,
            retryCount,
            nextRetryAt,
            exception
        )
    }

    /**
     * 재시도 가능 여부를 판단한다.
     */
    private fun isRetryable(exception: Exception): Boolean {
        return when (exception) {
            is InkFlowException -> exception.errorCode.retryable
            else -> true
        }
    }

    /**
     * 재시도 지연 시간을 계산한다.
     */
    private fun calculateNextRetryAt(now: Instant, retryCount: Int): Instant {
        val baseDelayMillis = properties.retryInitialDelay.toMillis().toDouble()
        val multiplier = properties.retryMultiplier
        val backoffMillis = baseDelayMillis * multiplier.pow((retryCount - 1).toDouble())
        val cappedMillis = min(backoffMillis, properties.retryMaxDelay.toMillis().toDouble())
        val jitteredMillis = applyJitter(cappedMillis, properties.retryJitterRatio)
        return now.plusMillis(jitteredMillis.toLong())
    }

    /**
     * 지터를 적용해 동시에 재시도하는 상황을 완화한다.
     */
    private fun applyJitter(delayMillis: Double, jitterRatio: Double): Double {
        if (jitterRatio == 0.0) {
            return delayMillis
        }
        val jitterRange = delayMillis * jitterRatio
        val randomOffset = Random.nextDouble(-jitterRange, jitterRange)
        val jittered = delayMillis + randomOffset
        return maxOf(TimeUnit.MILLISECONDS.toMillis(1).toDouble(), jittered)
    }

    /**
     * 예외 메시지를 Outbox 저장용으로 정리한다.
     */
    private fun buildLastError(exception: Exception): String {
        val rawMessage = exception.message ?: exception::class.simpleName ?: "UNKNOWN"
        val sanitized = rawMessage.trim()
        return if (sanitized.length <= 1000) sanitized else sanitized.take(1000)
    }
}
