package com.inkflow.publish.infra.grpc

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 퍼블리시 gRPC 서버 설정.
 */
@ConfigurationProperties(prefix = "inkflow.publish.grpc")
data class PublishGrpcProperties(
    val port: Int = 9091,
    val shutdownTimeout: Duration = Duration.ofSeconds(5),
    val tls: Tls = Tls()
) {
    /**
     * gRPC TLS/mTLS 설정을 정의한다.
     */
    data class Tls(
        val enabled: Boolean = false,
        val certChainFile: String = "",
        val privateKeyFile: String = "",
        val trustCertCollectionFile: String? = null,
        val clientAuth: ClientAuthMode = ClientAuthMode.NONE
    )

    /**
     * mTLS 클라이언트 인증 정책을 정의한다.
     */
    enum class ClientAuthMode {
        NONE,
        OPTIONAL,
        REQUIRE
    }
}
