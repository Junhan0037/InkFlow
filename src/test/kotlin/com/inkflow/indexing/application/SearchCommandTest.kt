package com.inkflow.indexing.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * 검색 명령 DTO의 입력 검증을 확인한다.
 */
class SearchCommandTest {
    /**
     * 페이지 범위가 잘못되면 예외가 발생하는지 확인한다.
     */
    @Test
    fun searchPageRequest_throwsWhenOutOfRange() {
        assertThrows(IllegalArgumentException::class.java) {
            SearchPageRequest(page = -1, size = 20)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SearchPageRequest(page = 0, size = 0)
        }
    }

    /**
     * 페이지 오프셋 계산이 올바른지 확인한다.
     */
    @Test
    fun searchPageRequest_calculatesOffset() {
        val pageRequest = SearchPageRequest(page = 2, size = 10)

        assertEquals(20, pageRequest.offset())
    }

    /**
     * Work 검색 명령의 문자열 입력 검증을 확인한다.
     */
    @Test
    fun workSearchCommand_rejectsBlankValues() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkSearchCommand(
                keyword = null,
                status = " ",
                language = "ko",
                creatorId = "creator-1",
                pageRequest = SearchPageRequest(page = 0, size = 20)
            )
        }
    }

    /**
     * Episode 검색 명령의 workId 검증을 확인한다.
     */
    @Test
    fun episodeSearchCommand_rejectsNonPositiveWorkId() {
        assertThrows(IllegalArgumentException::class.java) {
            EpisodeSearchCommand(
                keyword = "test",
                workId = 0L,
                pageRequest = SearchPageRequest(page = 0, size = 20)
            )
        }
    }

    /**
     * Asset 검색 명령의 입력 검증을 확인한다.
     */
    @Test
    fun assetSearchCommand_rejectsInvalidFilters() {
        assertThrows(IllegalArgumentException::class.java) {
            AssetSearchCommand(
                keyword = null,
                episodeId = -1L,
                status = "STORED",
                contentType = "image/png",
                pageRequest = SearchPageRequest(page = 0, size = 20)
            )
        }
    }
}
