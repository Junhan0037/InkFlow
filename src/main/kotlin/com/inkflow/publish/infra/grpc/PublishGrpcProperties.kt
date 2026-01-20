package com.inkflow.publish.infra.grpc

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 퍼블리시 gRPC 서버 설정.
 */
@ConfigurationProperties(prefix = "inkflow.publish.grpc")
data class PublishGrpcProperties(
    val port: Int = 9091,
    val shutdownTimeout: Duration = Duration.ofSeconds(5)
)
