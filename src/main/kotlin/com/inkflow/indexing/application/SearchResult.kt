package com.inkflow.indexing.application

/**
 * 검색 결과를 표준 페이징 형태로 반환.
 */
data class SearchResult<T>(
    val items: List<T>,
    val total: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int
) {
    companion object {
        /**
         * 전체 건수를 기반으로 페이징 정보를 계산해 결과를 생성한다.
         */
        fun <T> of(items: List<T>, total: Long, page: Int, size: Int): SearchResult<T> {
            val totalPages = if (size == 0) 0 else ((total + size - 1) / size).toInt()
            return SearchResult(
                items = items,
                total = total,
                page = page,
                size = size,
                totalPages = totalPages
            )
        }
    }

    /**
     * 결과 아이템을 변환한다.
     */
    fun <R> map(transform: (T) -> R): SearchResult<R> {
        return SearchResult(
            items = items.map(transform),
            total = total,
            page = page,
            size = size,
            totalPages = totalPages
        )
    }
}
