package com.inkflow.indexing.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * SearchResult의 페이징 계산과 변환을 검증한다.
 */
class SearchResultTest {
    /**
     * 전체 건수로부터 totalPages가 계산되는지 확인한다.
     */
    @Test
    fun of_calculatesTotalPages() {
        val result = SearchResult.of(items = listOf("a", "b"), total = 41, page = 0, size = 20)

        assertEquals(3, result.totalPages)
    }

    /**
     * map 변환 시 메타 정보가 유지되는지 확인한다.
     */
    @Test
    fun map_preservesPagingMetadata() {
        val result = SearchResult.of(items = listOf(1, 2), total = 2, page = 1, size = 10)

        val mapped = result.map { "item-$it" }

        assertEquals(listOf("item-1", "item-2"), mapped.items)
        assertEquals(result.total, mapped.total)
        assertEquals(result.page, mapped.page)
        assertEquals(result.size, mapped.size)
        assertEquals(result.totalPages, mapped.totalPages)
    }
}
