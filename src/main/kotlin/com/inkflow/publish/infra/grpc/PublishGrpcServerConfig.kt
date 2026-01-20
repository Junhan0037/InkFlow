package com.inkflow.publish.infra.grpc

import com.inkflow.common.grpc.GrpcLoggingServerInterceptor
import io.grpc.Server
import io.grpc.ServerInterceptors
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

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

        return NettyServerBuilder.forPort(publishGrpcProperties.port)
            .addService(service)
            .build()
    }

    /**
     * gRPC 서버를 애플리케이션 라이프사이클에 연결한다.
     */
    @Bean
    fun publishGrpcServerLifecycle(server: Server): PublishGrpcServerLifecycle {
        return PublishGrpcServerLifecycle(server, publishGrpcProperties.shutdownTimeout)
    }
}
