package com.inkflow.indexing.application

/**
 * 검색 페이징 요청 정보.
 */
data class SearchPageRequest(
    val page: Int = 0,
    val size: Int = 20
) {
    init {
        // 검색 API에서 허용하는 페이징 범위를 검증한다.
        require(page >= 0) { "page는 0 이상이어야 합니다." }
        require(size in 1..100) { "size는 1~100 범위여야 합니다." }
    }

    /**
     * Elasticsearch from 값을 계산한다.
     */
    fun offset(): Int = page * size
}

/**
 * Work 검색 명령 DTO.
 */
data class WorkSearchCommand(
    val keyword: String?,
    val status: String?,
    val language: String?,
    val creatorId: String?,
    val pageRequest: SearchPageRequest
) {
    init {
        // 검색 조건의 문자열 입력을 정리한다.
        require(creatorId == null || creatorId.isNotBlank()) { "creatorId는 빈 문자열일 수 없습니다." }
        require(status == null || status.isNotBlank()) { "status는 빈 문자열일 수 없습니다." }
        require(language == null || language.isNotBlank()) { "language는 빈 문자열일 수 없습니다." }
    }
}

/**
 * Episode 검색 명령 DTO.
 */
data class EpisodeSearchCommand(
    val keyword: String?,
    val workId: Long?,
    val pageRequest: SearchPageRequest
) {
    init {
        // 검색 조건의 숫자 입력을 검증한다.
        require(workId == null || workId > 0) { "workId는 0보다 커야 합니다." }
    }
}

/**
 * Asset 검색 명령 DTO.
 */
data class AssetSearchCommand(
    val keyword: String?,
    val episodeId: Long?,
    val status: String?,
    val contentType: String?,
    val pageRequest: SearchPageRequest
) {
    init {
        // 검색 조건의 입력 값을 검증한다.
        require(episodeId == null || episodeId > 0) { "episodeId는 0보다 커야 합니다." }
        require(status == null || status.isNotBlank()) { "status는 빈 문자열일 수 없습니다." }
        require(contentType == null || contentType.isNotBlank()) { "contentType은 빈 문자열일 수 없습니다." }
    }
}
