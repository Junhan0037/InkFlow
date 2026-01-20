package com.inkflow.publish.application

/**
 * 퍼블리시 스냅샷 생성 요청 커맨드.
 */
data class CreateSnapshotCommand(
    val episodeId: Long,
    val region: String,
    val language: String,
    val requestId: String
) {
    init {
        require(episodeId > 0) { "episodeId는 1 이상이어야 합니다." }
        require(region.isNotBlank()) { "region은 비어 있을 수 없습니다." }
        require(language.isNotBlank()) { "language는 비어 있을 수 없습니다." }
        require(requestId.isNotBlank()) { "requestId는 비어 있을 수 없습니다." }
    }
}

/**
 * 퍼블리시 롤백 요청 커맨드.
 */
data class RollbackSnapshotCommand(
    val episodeId: Long,
    val publishVersion: Long,
    val requestId: String
) {
    init {
        require(episodeId > 0) { "episodeId는 1 이상이어야 합니다." }
        require(publishVersion > 0) { "publishVersion은 1 이상이어야 합니다." }
        require(requestId.isNotBlank()) { "requestId는 비어 있을 수 없습니다." }
    }
}
