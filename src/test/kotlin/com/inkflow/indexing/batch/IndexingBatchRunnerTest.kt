package com.inkflow.indexing.batch

import com.inkflow.indexing.application.IndexingProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobInstance
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.boot.DefaultApplicationArguments

/**
 * IndexingBatchRunner 자동 실행 조건을 검증한다.
 */
class IndexingBatchRunnerTest {
    /**
     * 배치 기능이 비활성화되면 실행하지 않는다.
     */
    @Test
    fun run_skipsWhenBatchDisabled() {
        val jobLauncher = RecordingJobLauncher()
        val runner = IndexingBatchRunner(
            jobLauncher = jobLauncher,
            job = NoOpJob("indexingBackfillJob"),
            properties = IndexingBatchProperties(enabled = false, runOnStartup = true),
            indexingProperties = IndexingProperties(enabled = true)
        )

        // 실행: 애플리케이션 시작 훅을 호출한다.
        runner.run(DefaultApplicationArguments())

        // 검증: 배치 실행이 발생하지 않는다.
        assertEquals(0, jobLauncher.runCount)
    }

    /**
     * 자동 실행 플래그가 꺼져 있으면 실행하지 않는다.
     */
    @Test
    fun run_skipsWhenRunOnStartupDisabled() {
        val jobLauncher = RecordingJobLauncher()
        val runner = IndexingBatchRunner(
            jobLauncher = jobLauncher,
            job = NoOpJob("indexingBackfillJob"),
            properties = IndexingBatchProperties(enabled = true, runOnStartup = false),
            indexingProperties = IndexingProperties(enabled = true)
        )

        // 실행: 애플리케이션 시작 훅을 호출한다.
        runner.run(DefaultApplicationArguments())

        // 검증: 배치 실행이 발생하지 않는다.
        assertEquals(0, jobLauncher.runCount)
    }

    /**
     * 색인 기능이 비활성화되면 배치 실행을 하지 않는다.
     */
    @Test
    fun run_skipsWhenIndexingDisabled() {
        val jobLauncher = RecordingJobLauncher()
        val runner = IndexingBatchRunner(
            jobLauncher = jobLauncher,
            job = NoOpJob("indexingBackfillJob"),
            properties = IndexingBatchProperties(enabled = true, runOnStartup = true),
            indexingProperties = IndexingProperties(enabled = false)
        )

        // 실행: 애플리케이션 시작 훅을 호출한다.
        runner.run(DefaultApplicationArguments())

        // 검증: 배치 실행이 발생하지 않는다.
        assertEquals(0, jobLauncher.runCount)
    }

    /**
     * 모든 조건이 충족되면 배치가 실행되어야 한다.
     */
    @Test
    fun run_launchesJobWhenEnabled() {
        val jobLauncher = RecordingJobLauncher()
        val job = NoOpJob("indexingBackfillJob")
        val properties = IndexingBatchProperties(
            enabled = true,
            runOnStartup = true,
            defaultTarget = IndexingBatchTarget.ASSET
        )
        val runner = IndexingBatchRunner(
            jobLauncher = jobLauncher,
            job = job,
            properties = properties,
            indexingProperties = IndexingProperties(enabled = true)
        )

        // 실행: 애플리케이션 시작 훅을 호출한다.
        runner.run(DefaultApplicationArguments())

        // 검증: 배치 실행과 파라미터 구성을 확인한다.
        assertEquals(1, jobLauncher.runCount)
        assertEquals(job, jobLauncher.lastJob)
        val parameters = jobLauncher.lastParameters
        assertNotNull(parameters)
        assertEquals(properties.defaultTarget.name, parameters!!.getString("target"))
        assertNotNull(parameters.getLong("requestedAt"))
        assertTrue(parameters.getLong("requestedAt")!! > 0)
    }

    /**
     * 배치 실행 요청을 기록하는 JobLauncher 테스트 더블.
     */
    private class RecordingJobLauncher : JobLauncher {
        var runCount: Int = 0
        var lastJob: Job? = null
        var lastParameters: JobParameters? = null

        /**
         * 실행 요청을 기록하고 기본 JobExecution을 반환한다.
         */
        override fun run(job: Job, jobParameters: JobParameters): JobExecution {
            runCount += 1
            lastJob = job
            lastParameters = jobParameters
            return JobExecution(JobInstance(1L, job.name), jobParameters)
        }
    }

    /**
     * 실행 로직이 없는 Job 테스트 더블.
     */
    private class NoOpJob(
        private val jobName: String
    ) : Job {
        /**
         * 배치 Job 이름을 반환한다.
         */
        override fun getName(): String = jobName

        /**
         * 테스트 목적상 실제 실행 로직은 생략한다.
         */
        override fun execute(execution: JobExecution) {
            // 테스트용으로 수행할 작업 없음
        }
    }
}
