package com.inkflow.metadata.domain

import java.util.Locale

/**
 * 메타 태그를 정규화하는 공통 유틸리티.
 */
object MetadataTagNormalizer {
    private const val MAX_TAGS = 10
    private const val MAX_TAG_LENGTH = 30

    /**
     * 태그 입력을 정리하고 중복을 제거한다.
     */
    fun normalize(tags: Collection<String>): List<String> {
        val trimmed = tags
            .map { it.trim() }
            .filter { it.isNotBlank() }
            // 태그 길이를 제한해 저장/검색 시 과도한 노이즈를 방지한다.
            .map { tag -> tag.take(MAX_TAG_LENGTH) }

        val deduplicated = LinkedHashMap<String, String>()
        trimmed.forEach { tag ->
            val key = tag.lowercase(Locale.ROOT)
            if (!deduplicated.containsKey(key)) {
                deduplicated[key] = tag
            }
        }
        return deduplicated.values.take(MAX_TAGS)
    }
}
