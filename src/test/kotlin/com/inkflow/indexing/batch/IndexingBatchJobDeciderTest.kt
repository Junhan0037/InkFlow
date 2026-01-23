package com.inkflow.indexing.batch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobInstance
import org.springframework.batch.core.JobParametersBuilder

/**
 * IndexingBatchJobDecider의 분기 로직을 검증한다.
 */
class IndexingBatchJobDeciderTest {
    /**
     * target 파라미터가 지정되면 해당 분기 상태를 반환해야 한다.
     */
    @Test
    fun decide_returnsFlowStatusForTarget() {
        val jobParameters = JobParametersBuilder()
            // 배치 대상 파라미터를 설정한다.
            .addString("target", "episode")
            .toJobParameters()
        val jobExecution = JobExecution(JobInstance(1L, "indexingBackfillJob"), jobParameters)
        val decider = IndexingBatchJobDecider()

        // 실행: Decider를 호출해 분기 결과를 얻는다.
        val status = decider.decide(jobExecution, null)

        // 검증: EPISODE 분기 상태가 반환된다.
        assertEquals(IndexingBatchTarget.EPISODE.name, status.name)
    }
}
