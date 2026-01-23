package com.inkflow.indexing.batch

import com.inkflow.indexing.application.IndexingProperties
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * 설정에 따라 재색인/백필 배치를 자동 실행.
 */
@Component
class IndexingBatchRunner(
    private val jobLauncher: JobLauncher,
    @Qualifier("indexingBackfillJob") private val job: Job,
    private val properties: IndexingBatchProperties,
    private val indexingProperties: IndexingProperties
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 애플리케이션 시작 시 배치 자동 실행 여부를 판단한다.
     */
    override fun run(args: ApplicationArguments) {
        if (!properties.enabled || !properties.runOnStartup) {
            logger.info("재색인 배치 자동 실행이 비활성화되어 있습니다.")
            return
        }
        if (!indexingProperties.enabled) {
            logger.info("색인 기능이 비활성화되어 배치를 실행하지 않습니다.")
            return
        }

        val parameters = JobParametersBuilder()
            // 요청 대상과 실행 시각을 파라미터로 기록해 재실행 구분에 활용한다.
            .addString("target", properties.defaultTarget.name)
            .addLong("requestedAt", Instant.now().toEpochMilli())
            .toJobParameters()

        logger.info("재색인 배치 실행 시작. target={}", properties.defaultTarget)
        jobLauncher.run(job, parameters)
    }
}
