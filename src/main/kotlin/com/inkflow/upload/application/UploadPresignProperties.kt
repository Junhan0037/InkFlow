package com.inkflow.upload.application

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Presigned URL 발급에 필요한 기본 설정을 관리한다.
 */
@ConfigurationProperties("inkflow.upload.presign")
data class UploadPresignProperties(
    val baseUrl: String = "http://localhost:9000"
)
