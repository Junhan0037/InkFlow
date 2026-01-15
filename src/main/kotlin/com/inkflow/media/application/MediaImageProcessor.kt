package com.inkflow.media.application

/**
 * Media 이미지 처리 결과를 전달한다.
 */
data class MediaThumbnailResult(
    val bytes: ByteArray,
    val contentType: String,
    val format: String,
    val width: Int,
    val height: Int
) {
    init {
        require(bytes.isNotEmpty()) { "bytes는 비어 있을 수 없습니다." }
        require(contentType.isNotBlank()) { "contentType은 비어 있을 수 없습니다." }
        require(format.isNotBlank()) { "format은 비어 있을 수 없습니다." }
        require(width > 0) { "width는 0보다 커야 합니다." }
        require(height > 0) { "height는 0보다 커야 합니다." }
    }
}

/**
 * Media 이미지 처리 파이프라인의 계약을 정의한다.
 */
interface MediaImageProcessor {
    /**
     * 원본 이미지를 썸네일로 변환한다.
     */
    fun createThumbnail(originalBytes: ByteArray, spec: MediaJobSpec): MediaThumbnailResult
}
