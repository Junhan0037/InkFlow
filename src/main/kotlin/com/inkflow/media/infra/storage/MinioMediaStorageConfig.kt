package com.inkflow.media.infra.storage

import com.inkflow.media.application.MediaStorageClient
import com.inkflow.media.application.MediaStorageProperties
import io.minio.MinioClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * MinIO 기반 Media 스토리지 클라이언트를 구성.
 */
@Configuration
class MinioMediaStorageConfig {
    /**
     * MinIO SDK 클라이언트를 생성한다.
     */
    @Bean
    fun minioClient(properties: MediaStorageProperties): MinioClient {
        return MinioClient.builder()
            // 설정된 엔드포인트/자격증명을 바탕으로 클라이언트를 구성한다.
            .endpoint(properties.endpoint)
            .credentials(properties.accessKey, properties.secretKey)
            .build()
    }

    /**
     * Media 스토리지 접근 구현체를 제공한다.
     */
    @Bean
    fun mediaStorageClient(minioClient: MinioClient): MediaStorageClient {
        return MinioMediaStorageClient(minioClient)
    }
}
