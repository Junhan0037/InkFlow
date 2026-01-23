package com.inkflow.publish.infra.grpc

import com.inkflow.common.grpc.GrpcLoggingServerInterceptor
import io.grpc.Server
import io.grpc.ServerInterceptors
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File

/**
 * 퍼블리시 gRPC 서버 구성을 제공한다.
 */
@Configuration
@EnableConfigurationProperties(PublishGrpcProperties::class)
class PublishGrpcServerConfig(
    private val publishGrpcService: PublishGrpcService,
    private val publishGrpcProperties: PublishGrpcProperties
) {
    /**
     * gRPC 서버 인스턴스를 구성한다.
     */
    @Bean
    fun publishGrpcServer(): Server {
        val interceptor = GrpcLoggingServerInterceptor(serviceName = "publish-service")
        val service = ServerInterceptors.intercept(publishGrpcService, interceptor)

        val builder = NettyServerBuilder.forPort(publishGrpcProperties.port)
            .addService(service) // gRPC 서비스와 기본 로깅 인터셉터를 등록한다.

        val sslContext = buildSslContext(publishGrpcProperties.tls)
        if (sslContext != null) {
            builder.sslContext(sslContext) // TLS/mTLS가 활성화된 경우 보안 컨텍스트를 적용한다.
        }

        return builder.build()
    }

    /**
     * gRPC 서버를 애플리케이션 라이프사이클에 연결한다.
     */
    @Bean
    fun publishGrpcServerLifecycle(server: Server): PublishGrpcServerLifecycle {
        return PublishGrpcServerLifecycle(server, publishGrpcProperties.shutdownTimeout)
    }

    /**
     * TLS 설정이 활성화된 경우 SSL 컨텍스트를 생성한다.
     */
    private fun buildSslContext(tls: PublishGrpcProperties.Tls): SslContext? {
        if (!tls.enabled) {
            return null
        }

        val certChainPath = tls.certChainFile.trim()
        val privateKeyPath = tls.privateKeyFile.trim()
        require(certChainPath.isNotBlank()) { "gRPC TLS 인증서 체인 파일 경로가 필요합니다." }
        require(privateKeyPath.isNotBlank()) { "gRPC TLS 개인키 파일 경로가 필요합니다." }

        val builder = GrpcSslContexts.forServer(File(certChainPath), File(privateKeyPath))

        val clientAuth = when (tls.clientAuth) {
            PublishGrpcProperties.ClientAuthMode.NONE -> ClientAuth.NONE
            PublishGrpcProperties.ClientAuthMode.OPTIONAL -> ClientAuth.OPTIONAL
            PublishGrpcProperties.ClientAuthMode.REQUIRE -> ClientAuth.REQUIRE
        }

        if (clientAuth != ClientAuth.NONE) {
            val trustCertPath = tls.trustCertCollectionFile?.trim().orEmpty()
            require(trustCertPath.isNotBlank()) { "mTLS 사용 시 trustCertCollectionFile이 필요합니다." }

            // 클라이언트 인증서 검증을 위해 신뢰 CA를 설정한다.
            builder.trustManager(File(trustCertPath))
        }

        builder.clientAuth(clientAuth)
        return builder.build()
    }
}
