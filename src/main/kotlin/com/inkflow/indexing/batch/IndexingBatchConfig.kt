package com.inkflow.indexing.batch

import com.inkflow.indexing.domain.AssetIndexDocument
import com.inkflow.indexing.domain.EpisodeIndexDocument
import com.inkflow.indexing.domain.IndexingGateway
import com.inkflow.indexing.domain.WorkIndexDocument
import com.inkflow.indexing.infra.jpa.AssetIndexEntity
import com.inkflow.indexing.infra.jpa.EpisodeIndexEntity
import com.inkflow.indexing.infra.jpa.WorkIndexEntity
import jakarta.persistence.EntityManagerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.flow.JobExecutionDecider
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemWriter
import org.springframework.batch.item.database.JpaPagingItemReader
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.transaction.PlatformTransactionManager

/**
 * 대량 재색인/백필 배치 Job을 구성.
 */
@Configuration
@EnableBatchProcessing
@EnableConfigurationProperties(IndexingBatchProperties::class)
class IndexingBatchConfig {
    /**
     * 배치 대상 분기를 위한 Decider를 제공한다.
     */
    @Bean
    fun indexingBatchDecider(): JobExecutionDecider {
        return IndexingBatchJobDecider()
    }

    /**
     * 재색인/백필 배치 Job을 정의한다.
     */
    @Bean
    fun indexingBackfillJob(
        jobRepository: JobRepository,
        indexingBatchDecider: JobExecutionDecider,
        workReindexStep: Step,
        episodeReindexStep: Step,
        assetReindexStep: Step
    ): Job {
        return JobBuilder("indexingBackfillJob", jobRepository)
            .incrementer(RunIdIncrementer())
            .start(indexingBatchDecider)
            .on(IndexingBatchTarget.ALL.name).to(workReindexStep)
            .next(episodeReindexStep)
            .next(assetReindexStep)
            .from(indexingBatchDecider)
            .on(IndexingBatchTarget.WORK.name).to(workReindexStep)
            .from(indexingBatchDecider)
            .on(IndexingBatchTarget.EPISODE.name).to(episodeReindexStep)
            .from(indexingBatchDecider)
            .on(IndexingBatchTarget.ASSET.name).to(assetReindexStep)
            .end()
            .build()
    }

    /**
     * Work 재색인 Step을 구성한다.
     */
    @Bean
    fun workReindexStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        workIndexReader: JpaPagingItemReader<WorkIndexEntity>,
        workIndexProcessor: ItemProcessor<WorkIndexEntity, WorkIndexDocument>,
        workIndexWriter: ItemWriter<WorkIndexDocument>,
        properties: IndexingBatchProperties
    ): Step {
        return StepBuilder("workReindexStep", jobRepository)
            .chunk<WorkIndexEntity, WorkIndexDocument>(properties.chunkSize, transactionManager)
            .reader(workIndexReader)
            .processor(workIndexProcessor)
            .writer(workIndexWriter)
            .build()
    }

    /**
     * Episode 재색인 Step을 구성한다.
     */
    @Bean
    fun episodeReindexStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        episodeIndexReader: JpaPagingItemReader<EpisodeIndexEntity>,
        episodeIndexProcessor: ItemProcessor<EpisodeIndexEntity, EpisodeIndexDocument>,
        episodeIndexWriter: ItemWriter<EpisodeIndexDocument>,
        properties: IndexingBatchProperties
    ): Step {
        return StepBuilder("episodeReindexStep", jobRepository)
            .chunk<EpisodeIndexEntity, EpisodeIndexDocument>(properties.chunkSize, transactionManager)
            .reader(episodeIndexReader)
            .processor(episodeIndexProcessor)
            .writer(episodeIndexWriter)
            .build()
    }

    /**
     * Asset 재색인 Step을 구성한다.
     */
    @Bean
    fun assetReindexStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        assetIndexReader: JpaPagingItemReader<AssetIndexEntity>,
        assetIndexProcessor: ItemProcessor<AssetIndexEntity, AssetIndexDocument>,
        assetIndexWriter: ItemWriter<AssetIndexDocument>,
        properties: IndexingBatchProperties
    ): Step {
        return StepBuilder("assetReindexStep", jobRepository)
            .chunk<AssetIndexEntity, AssetIndexDocument>(properties.chunkSize, transactionManager)
            .reader(assetIndexReader)
            .processor(assetIndexProcessor)
            .writer(assetIndexWriter)
            .build()
    }

    /**
     * Work 원천 데이터를 읽어오는 Reader를 제공한다.
     */
    @Bean
    @StepScope
    fun workIndexReader(
        entityManagerFactory: EntityManagerFactory,
        properties: IndexingBatchProperties
    ): JpaPagingItemReader<WorkIndexEntity> {
        return JpaPagingItemReaderBuilder<WorkIndexEntity>()
            .name("workIndexReader")
            .entityManagerFactory(entityManagerFactory)
            .queryString("select w from WorkIndexEntity w order by w.id")
            .pageSize(properties.pageSize)
            .build()
    }

    /**
     * Episode 원천 데이터를 읽어오는 Reader를 제공한다.
     */
    @Bean
    @StepScope
    fun episodeIndexReader(
        entityManagerFactory: EntityManagerFactory,
        properties: IndexingBatchProperties
    ): JpaPagingItemReader<EpisodeIndexEntity> {
        return JpaPagingItemReaderBuilder<EpisodeIndexEntity>()
            .name("episodeIndexReader")
            .entityManagerFactory(entityManagerFactory)
            .queryString("select e from EpisodeIndexEntity e order by e.id")
            .pageSize(properties.pageSize)
            .build()
    }

    /**
     * Asset 원천 데이터를 읽어오는 Reader를 제공한다.
     */
    @Bean
    @StepScope
    fun assetIndexReader(
        entityManagerFactory: EntityManagerFactory,
        properties: IndexingBatchProperties
    ): JpaPagingItemReader<AssetIndexEntity> {
        return JpaPagingItemReaderBuilder<AssetIndexEntity>()
            .name("assetIndexReader")
            .entityManagerFactory(entityManagerFactory)
            .queryString("select a from AssetIndexEntity a order by a.id")
            .pageSize(properties.pageSize)
            .build()
    }

    /**
     * Work 데이터를 색인 문서로 변환하는 Processor를 제공한다.
     */
    @Bean
    fun workIndexProcessor(): ItemProcessor<WorkIndexEntity, WorkIndexDocument> {
        return ItemProcessor { entity ->
            // Work 엔티티를 Elasticsearch 문서로 매핑한다.
            WorkIndexDocument(
                id = entity.id,
                title = entity.title,
                creatorId = entity.creatorId,
                status = entity.status,
                defaultLanguage = entity.defaultLanguage,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
        }
    }

    /**
     * Episode 데이터를 색인 문서로 변환하는 Processor를 제공한다.
     */
    @Bean
    fun episodeIndexProcessor(): ItemProcessor<EpisodeIndexEntity, EpisodeIndexDocument> {
        return ItemProcessor { entity ->
            // Episode 엔티티를 Elasticsearch 문서로 매핑한다.
            EpisodeIndexDocument(
                id = entity.id,
                workId = entity.workId,
                title = entity.title,
                seq = entity.seq,
                publishedAt = entity.publishedAt,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
        }
    }

    /**
     * Asset 데이터를 색인 문서로 변환하는 Processor를 제공한다.
     */
    @Bean
    fun assetIndexProcessor(): ItemProcessor<AssetIndexEntity, AssetIndexDocument> {
        return ItemProcessor { entity ->
            // Asset 엔티티를 Elasticsearch 문서로 매핑한다.
            AssetIndexDocument(
                id = entity.id,
                episodeId = entity.episodeId,
                fileName = entity.fileName,
                contentType = entity.contentType,
                size = entity.size,
                checksum = entity.checksum,
                storageKey = entity.storageKey,
                status = entity.status,
                creatorId = entity.creatorId,
                uploadId = entity.uploadId,
                storageBucket = entity.storageBucket,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt
            )
        }
    }

    /**
     * Work 문서를 Elasticsearch에 적재하는 Writer를 제공한다.
     */
    @Bean
    fun workIndexWriter(indexingGateway: IndexingGateway): ItemWriter<WorkIndexDocument> {
        return ItemWriter { items ->
            // 배치 단위로 Work 문서를 업서트한다.
            items.forEach { indexingGateway.upsertWork(it) }
        }
    }

    /**
     * Episode 문서를 Elasticsearch에 적재하는 Writer를 제공한다.
     */
    @Bean
    fun episodeIndexWriter(indexingGateway: IndexingGateway): ItemWriter<EpisodeIndexDocument> {
        return ItemWriter { items ->
            // 배치 단위로 Episode 문서를 업서트한다.
            items.forEach { indexingGateway.upsertEpisode(it) }
        }
    }

    /**
     * Asset 문서를 Elasticsearch에 적재하는 Writer를 제공한다.
     */
    @Bean
    fun assetIndexWriter(indexingGateway: IndexingGateway): ItemWriter<AssetIndexDocument> {
        return ItemWriter { items ->
            // 배치 단위로 Asset 문서를 업서트한다.
            items.forEach { indexingGateway.upsertAsset(it) }
        }
    }
}
