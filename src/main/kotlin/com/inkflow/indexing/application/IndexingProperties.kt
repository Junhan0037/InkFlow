package com.inkflow.indexing.application

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Elasticsearch 연동에 필요한 색인 설정을 관리한다.
 */
@ConfigurationProperties("inkflow.indexing")
data class IndexingProperties(
    val enabled: Boolean = true,
    val baseUrl: String = "http://localhost:9200",
    val indices: Indices = Indices()
) {
    init {
        // 색인 서비스 기본 설정을 검증한다.
        require(baseUrl.isNotBlank()) { "baseUrl은 비어 있을 수 없습니다." }
        require(indices.works.isNotBlank()) { "works 인덱스 이름은 비어 있을 수 없습니다." }
        require(indices.episodes.isNotBlank()) { "episodes 인덱스 이름은 비어 있을 수 없습니다." }
        require(indices.assets.isNotBlank()) { "assets 인덱스 이름은 비어 있을 수 없습니다." }
    }

    /**
     * 엔티티별 인덱스 이름을 정의한다.
     */
    data class Indices(
        val works: String = "works_write",
        val episodes: String = "episodes_write",
        val assets: String = "assets_write"
    )
}
