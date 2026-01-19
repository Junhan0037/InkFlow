package com.inkflow.indexing.domain

import java.time.Instant

/**
 * Work 색인에 사용하는 문서 모델.
 */
data class WorkIndexDocument(
    val id: Long,
    val title: String,
    val creatorId: String,
    val status: String,
    val defaultLanguage: String,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        // Work 색인 문서의 기본 필드를 검증한다.
        require(id > 0) { "workId는 0보다 커야 합니다." }
        require(title.isNotBlank()) { "title은 비어 있을 수 없습니다." }
        require(creatorId.isNotBlank()) { "creatorId는 비어 있을 수 없습니다." }
        require(status.isNotBlank()) { "status는 비어 있을 수 없습니다." }
        require(defaultLanguage.isNotBlank()) { "defaultLanguage는 비어 있을 수 없습니다." }
    }
}

/**
 * Episode 색인에 사용하는 문서 모델.
 */
data class EpisodeIndexDocument(
    val id: Long,
    val workId: Long,
    val title: String,
    val seq: Int,
    val publishedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        // Episode 색인 문서의 기본 필드를 검증한다.
        require(id > 0) { "episodeId는 0보다 커야 합니다." }
        require(workId > 0) { "workId는 0보다 커야 합니다." }
        require(title.isNotBlank()) { "title은 비어 있을 수 없습니다." }
        require(seq > 0) { "seq는 0보다 커야 합니다." }
    }
}

/**
 * Asset 색인에 사용하는 문서 모델.
 */
data class AssetIndexDocument(
    val id: Long,
    val episodeId: Long,
    val fileName: String,
    val contentType: String,
    val size: Long,
    val checksum: String,
    val storageKey: String,
    val status: String,
    val creatorId: String,
    val uploadId: String,
    val storageBucket: String,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        // Asset 색인 문서의 기본 필드를 검증한다.
        require(id > 0) { "assetId는 0보다 커야 합니다." }
        require(episodeId > 0) { "episodeId는 0보다 커야 합니다." }
        require(fileName.isNotBlank()) { "fileName은 비어 있을 수 없습니다." }
        require(contentType.isNotBlank()) { "contentType은 비어 있을 수 없습니다." }
        require(size > 0) { "size는 0보다 커야 합니다." }
        require(checksum.isNotBlank()) { "checksum은 비어 있을 수 없습니다." }
        require(storageKey.isNotBlank()) { "storageKey는 비어 있을 수 없습니다." }
        require(status.isNotBlank()) { "status는 비어 있을 수 없습니다." }
        require(creatorId.isNotBlank()) { "creatorId는 비어 있을 수 없습니다." }
        require(uploadId.isNotBlank()) { "uploadId는 비어 있을 수 없습니다." }
        require(storageBucket.isNotBlank()) { "storageBucket는 비어 있을 수 없습니다." }
    }
}
