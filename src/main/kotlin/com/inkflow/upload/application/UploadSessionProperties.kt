package com.inkflow.upload.application

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 업로드 세션 생성 정책을 정의하는 설정 값.
 */
@ConfigurationProperties("inkflow.upload.session")
data class UploadSessionProperties(
    val ttl: Duration = Duration.ofMinutes(30),
    val minChunkSize: Long = 5 * 1024 * 1024,
    val maxPartCount: Int = 10_000,
    val storageBucket: String = "inkflow-uploads",
    val storageKeyPrefix: String = "uploads/"
)
