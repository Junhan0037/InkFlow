package com.inkflow.upload.application

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * 업로드/다운로드 관련 설정 프로퍼티를 활성화한다.
 */
@Configuration
@EnableConfigurationProperties(
    UploadSessionProperties::class,
    UploadValidationProperties::class,
    UploadPresignProperties::class,
    AssetDownloadProperties::class,
    IdempotencyProperties::class
)
class UploadSessionConfig
