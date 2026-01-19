package com.inkflow.indexing.api

import java.time.Instant

/**
 * 검색 결과를 표준 페이지 응답.
 */
data class SearchPageResponse<T>(
    val items: List<T>,
    val total: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int
)

/**
 * Work 검색 결과 항목.
 */
data class WorkSearchResponse(
    val id: Long,
    val title: String,
    val creatorId: String,
    val status: String,
    val defaultLanguage: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Episode 검색 결과 항목.
 */
data class EpisodeSearchResponse(
    val id: Long,
    val workId: Long,
    val title: String,
    val seq: Int,
    val publishedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Asset 검색 결과 항목.
 */
data class AssetSearchResponse(
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
)
