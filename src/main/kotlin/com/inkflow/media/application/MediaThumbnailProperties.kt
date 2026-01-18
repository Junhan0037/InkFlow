package com.inkflow.media.application

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 썸네일 생성 파이프라인 기본 설정 값.
 */
@ConfigurationProperties("inkflow.media.thumbnail")
data class MediaThumbnailProperties(
    val storageBucket: String? = null,
    val storageKeyPrefix: String = "derivatives/thumbnails/",
    val jpegQuality: Double = 0.85,
    val backgroundColor: String = "#FFFFFF",
    val allowedFormats: List<String> = listOf("jpg", "jpeg", "png")
) {
    init {
        require(storageKeyPrefix.isNotBlank()) { "storageKeyPrefix는 비어 있을 수 없습니다." }
        require(jpegQuality in 0.0..1.0) { "jpegQuality는 0.0에서 1.0 사이여야 합니다." }
        require(backgroundColor.isNotBlank()) { "backgroundColor는 비어 있을 수 없습니다." }
    }
}
