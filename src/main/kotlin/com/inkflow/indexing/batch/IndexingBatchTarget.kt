package com.inkflow.indexing.batch

/**
 * 재색인/백필 배치의 대상 엔티티 유형을 정의.
 */
enum class IndexingBatchTarget {
    ALL,
    WORK,
    EPISODE,
    ASSET;

    companion object {
        /**
         * 문자열 입력을 배치 대상 타입으로 변환한다.
         */
        fun from(raw: String?): IndexingBatchTarget {
            val normalized = raw?.trim()?.uppercase()
            if (normalized.isNullOrBlank()) {
                return ALL
            }
            return entries.firstOrNull { it.name == normalized }
                ?: throw IllegalArgumentException("지원하지 않는 배치 대상입니다: $raw")
        }
    }
}
