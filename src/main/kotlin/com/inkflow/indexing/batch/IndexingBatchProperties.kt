package com.inkflow.indexing.batch

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 대량 재색인/백필 배치 실행 옵션을 관리한다.
 */
@ConfigurationProperties("inkflow.indexing.batch")
data class IndexingBatchProperties(
    val enabled: Boolean = true,
    val runOnStartup: Boolean = false,
    val defaultTarget: IndexingBatchTarget = IndexingBatchTarget.ALL,
    val chunkSize: Int = 200,
    val pageSize: Int = 200
) {
    init {
        // 배치 처리량 설정의 기본 유효성을 보장한다.
        require(chunkSize > 0) { "chunkSize는 1 이상이어야 합니다." }
        require(pageSize > 0) { "pageSize는 1 이상이어야 합니다." }
    }
}
