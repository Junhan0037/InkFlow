package com.inkflow.media.application

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Media 워커 설정 프로퍼티를 활성화한다.
 */
@Configuration
@EnableConfigurationProperties(
    MediaStorageProperties::class,
    MediaThumbnailProperties::class,
    MediaJobRetryProperties::class
)
class MediaWorkerConfig
