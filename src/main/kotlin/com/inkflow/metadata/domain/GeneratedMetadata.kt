package com.inkflow.metadata.domain

/**
 * 메타 자동 생성 결과를 담는 도메인 모델.
 */
data class GeneratedMetadata(
    val summary: String,
    val tags: List<String>,
    val generator: String
) {
    init {
        require(summary.isNotBlank()) { "summary는 비어 있을 수 없습니다." }
        require(generator.isNotBlank()) { "generator는 비어 있을 수 없습니다." }
    }
}
