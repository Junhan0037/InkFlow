package com.inkflow.indexing.batch

import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.job.flow.FlowExecutionStatus
import org.springframework.batch.core.job.flow.JobExecutionDecider

/**
 * 배치 실행 파라미터에 따라 대상 Step을 선택한다.
 */
class IndexingBatchJobDecider : JobExecutionDecider {
    /**
     * target 파라미터를 기준으로 분기 상태를 반환한다.
     */
    override fun decide(jobExecution: JobExecution, stepExecution: StepExecution?): FlowExecutionStatus {
        val target = IndexingBatchTarget.from(jobExecution.jobParameters.getString("target"))
        return FlowExecutionStatus(target.name)
    }
}
