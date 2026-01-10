package com.inkflow.upload.application

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 업로드 파일 기본 검증 정책을 정의하는 설정 값.
 */
@ConfigurationProperties("inkflow.upload.validation")
data class UploadValidationProperties(
    val minFileSize: Long = 1L,
    val maxFileSize: Long = 5_368_709_120L,
    val allowedExtensions: Set<String> = setOf(
        "jpg",
        "jpeg",
        "png",
        "gif",
        "webp",
        "mp4",
        "mov",
        "pdf",
        "epub"
    )
)
