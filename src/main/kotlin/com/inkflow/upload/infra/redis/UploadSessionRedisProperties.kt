package com.inkflow.upload.infra.redis

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 업로드 세션 Redis 저장소 설정을 관리한다.
 */
@ConfigurationProperties("inkflow.upload.session.redis")
data class UploadSessionRedisProperties(
    val keyPrefix: String = "upload:session:"
)
