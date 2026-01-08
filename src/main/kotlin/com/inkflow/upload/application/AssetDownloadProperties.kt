package com.inkflow.upload.application

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 다운로드 presigned URL 발급 정책을 정의하는 설정 값.
 */
@ConfigurationProperties("inkflow.upload.download")
data class AssetDownloadProperties(
    val ttl: Duration = Duration.ofMinutes(10)
)
