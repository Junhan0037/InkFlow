package com.inkflow.metadata.domain

/**
 * 메타 자동 생성에 사용하는 에피소드 원천 정보.
 */
data class EpisodeMetadataSource(
    val episodeId: Long,
    val workId: Long,
    val episodeTitle: String,
    val workTitle: String,
    val defaultLanguage: String,
    val creatorId: String
) {
    init {
        require(episodeId > 0) { "episodeId는 0보다 커야 합니다." }
        require(workId > 0) { "workId는 0보다 커야 합니다." }
        require(episodeTitle.isNotBlank()) { "episodeTitle은 비어 있을 수 없습니다." }
        require(workTitle.isNotBlank()) { "workTitle은 비어 있을 수 없습니다." }
        require(defaultLanguage.isNotBlank()) { "defaultLanguage는 비어 있을 수 없습니다." }
        require(creatorId.isNotBlank()) { "creatorId는 비어 있을 수 없습니다." }
    }
}
