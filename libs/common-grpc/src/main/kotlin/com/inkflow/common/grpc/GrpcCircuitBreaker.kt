package com.inkflow.common.grpc

import io.grpc.Status
import java.time.Clock

/**
 * gRPC 호출 보호를 위한 간단한 서킷브레이커 구현체.
 */
class GrpcCircuitBreaker(
    private val name: String,
    private val policy: GrpcCircuitBreakerPolicy,
    private val clock: Clock = Clock.systemUTC()
) {
    private val lock = Any()
    private var state: State = State.CLOSED
    private var failureCount: Int = 0
    private var openUntilMillis: Long = 0
    private var halfOpenInFlight: Int = 0

    /**
     * 호출 허용 여부를 반환한다.
     */
    fun allowCall(): Boolean {
        if (!policy.enabled) {
            return true
        }
        val now = clock.millis()
        synchronized(lock) {
            return when (state) {
                State.CLOSED -> true
                State.OPEN -> {
                    if (now >= openUntilMillis) {
                        transitionToHalfOpen()
                        tryAcquireHalfOpenSlot()
                    } else {
                        false
                    }
                }
                State.HALF_OPEN -> tryAcquireHalfOpenSlot()
            }
        }
    }

    /**
     * 호출 성공을 기록한다.
     */
    fun onSuccess() {
        if (!policy.enabled) {
            return
        }
        synchronized(lock) {
            when (state) {
                State.CLOSED -> failureCount = 0
                State.HALF_OPEN -> {
                    releaseHalfOpenSlot()
                    // 절반 개방 상태에서 성공하면 즉시 회복한다.
                    transitionToClosed()
                }
                State.OPEN -> {
                    // OPEN 상태에서는 성공/실패 판정을 무시한다.
                }
            }
        }
    }

    /**
     * 호출 실패를 기록한다.
     */
    fun onFailure(status: Status) {
        if (!policy.enabled) {
            return
        }
        if (!policy.failureStatusCodes.contains(status.code)) {
            return
        }
        val now = clock.millis()
        synchronized(lock) {
            when (state) {
                State.CLOSED -> {
                    failureCount += 1
                    if (failureCount >= policy.maxFailures) {
                        transitionToOpen(now)
                    }
                }
                State.HALF_OPEN -> {
                    releaseHalfOpenSlot()
                    transitionToOpen(now)
                }
                State.OPEN -> {
                    // OPEN 상태에서는 실패를 누적하지 않는다.
                }
            }
        }
    }

    /**
     * 서킷브레이커 상태를 반환한다.
     */
    fun currentState(): String = state.name

    /**
     * HALF_OPEN 상태의 허용 슬롯을 확보한다.
     */
    private fun tryAcquireHalfOpenSlot(): Boolean {
        if (halfOpenInFlight >= policy.halfOpenMaxCalls) {
            return false
        }
        halfOpenInFlight += 1
        return true
    }

    /**
     * HALF_OPEN 호출 종료 시 슬롯을 반환한다.
     */
    private fun releaseHalfOpenSlot() {
        if (halfOpenInFlight > 0) {
            halfOpenInFlight -= 1
        }
    }

    /**
     * CLOSED 상태로 복귀한다.
     */
    private fun transitionToClosed() {
        state = State.CLOSED
        failureCount = 0
        openUntilMillis = 0
        halfOpenInFlight = 0
    }

    /**
     * OPEN 상태로 전환하고 복구 시간까지 차단한다.
     */
    private fun transitionToOpen(nowMillis: Long) {
        state = State.OPEN
        failureCount = 0
        openUntilMillis = nowMillis + policy.openDuration.toMillis()
        halfOpenInFlight = 0
    }

    /**
     * HALF_OPEN 상태로 전환한다.
     */
    private fun transitionToHalfOpen() {
        state = State.HALF_OPEN
        failureCount = 0
        openUntilMillis = 0
        halfOpenInFlight = 0
    }

    /**
     * 서킷브레이커 상태를 정의한다.
     */
    private enum class State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }
}
