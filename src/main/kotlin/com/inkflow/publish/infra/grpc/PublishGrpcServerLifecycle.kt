package com.inkflow.publish.infra.grpc

import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import io.grpc.Server
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * gRPC 서버를 Spring 라이프사이클에 맞춰 기동/종료한다.
 */
class PublishGrpcServerLifecycle(
    private val server: Server,
    private val shutdownTimeout: Duration,
    private val logger: Logger = LoggerFactory.getLogger(PublishGrpcServerLifecycle::class.java)
) : SmartLifecycle {
    @Volatile
    private var running: Boolean = false

    /**
     * gRPC 서버를 시작한다.
     */
    override fun start() {
        try {
            server.start()
            running = true
            logger.info("publish.grpc.server.started port={}", server.port)
        } catch (exception: IOException) {
            throw SystemException(
                errorCode = ErrorCode.INTERNAL_ERROR,
                message = "gRPC 서버 시작에 실패했습니다.",
                cause = exception
            )
        }
    }

    /**
     * gRPC 서버를 종료한다.
     */
    override fun stop() {
        server.shutdown()
        val terminated = server.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)
        if (!terminated) {
            server.shutdownNow()
        }
        running = false
        logger.info("publish.grpc.server.stopped")
    }

    /**
     * 종료 콜백을 지원한다.
     */
    override fun stop(callback: Runnable) {
        stop()
        callback.run()
    }

    /**
     * 현재 실행 여부를 반환한다.
     */
    override fun isRunning(): Boolean = running

    /**
     * Spring이 자동으로 시작하도록 한다.
     */
    override fun isAutoStartup(): Boolean = true

    /**
     * 가장 마지막 단계에서 기동하도록 우선순위를 높인다.
     */
    override fun getPhase(): Int = Int.MAX_VALUE
}
