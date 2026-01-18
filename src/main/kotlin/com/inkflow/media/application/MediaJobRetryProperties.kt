package com.inkflow.media.application

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Media 작업 재시도 정책을 위한 설정 값을 바인딩한다.
 */
@ConfigurationProperties("inkflow.media.retry")
data class MediaJobRetryProperties(
    val maxAttempts: Int = 3
) {
    init {
        require(maxAttempts >= 0) { "maxAttempts는 0 이상이어야 합니다." }
    }
}
