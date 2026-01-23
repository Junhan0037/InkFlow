package com.inkflow.indexing.batch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * IndexingBatchTarget 변환 규칙을 검증한다.
 */
class IndexingBatchTargetTest {
    /**
     * 파라미터가 비어 있으면 ALL이 기본값으로 사용된다.
     */
    @Test
    fun from_returnsAllWhenBlank() {
        // 실행/검증: null 입력은 ALL로 처리된다.
        assertEquals(IndexingBatchTarget.ALL, IndexingBatchTarget.from(null))
    }

    /**
     * 입력 문자열은 공백 제거/대문자 변환 후 매핑된다.
     */
    @Test
    fun from_normalizesInput() {
        // 실행/검증: 공백과 소문자 입력이 정상 매핑된다.
        assertEquals(IndexingBatchTarget.WORK, IndexingBatchTarget.from(" work "))
    }

    /**
     * 지원하지 않는 값이면 예외가 발생해야 한다.
     */
    @Test
    fun from_throwsWhenUnknown() {
        // 실행/검증: 정의되지 않은 입력은 예외로 처리한다.
        assertThrows(IllegalArgumentException::class.java) {
            IndexingBatchTarget.from("unknown")
        }
    }
}
