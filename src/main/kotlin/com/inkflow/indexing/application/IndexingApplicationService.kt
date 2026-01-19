package com.inkflow.indexing.application

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.indexing.domain.AssetIndexDocument
import com.inkflow.indexing.domain.AssetIndexSource
import com.inkflow.indexing.domain.EpisodeIndexDocument
import com.inkflow.indexing.domain.EpisodeIndexSource
import com.inkflow.indexing.domain.IndexEntityType
import com.inkflow.indexing.domain.IndexOperation
import com.inkflow.indexing.domain.IndexSourceRepository
import com.inkflow.indexing.domain.IndexingGateway
import com.inkflow.indexing.domain.WorkIndexDocument
import com.inkflow.indexing.domain.WorkIndexSource
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 색인 요청 이벤트를 처리하는 애플리케이션 서비스.
 */
@Service
class IndexingApplicationService(
    private val indexSourceRepository: IndexSourceRepository,
    private val indexingGateway: IndexingGateway
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 색인 요청을 처리하고 외부 인덱스 갱신을 수행한다.
     */
    fun handleIndexRequest(command: IndexingCommand, metadata: IndexingMessageMetadata) {
        logger.info(
            "색인 요청 수신. entityType={}, entityId={}, operation={}, eventId={}, traceId={}",
            command.entityType,
            command.entityId,
            command.operation,
            metadata.eventId,
            metadata.traceId
        )
        when (command.operation) {
            IndexOperation.UPSERT -> handleUpsert(command, metadata)
            IndexOperation.DELETE -> handleDelete(command, metadata)
        }
    }

    /**
     * UPSERT 요청을 엔티티 타입별로 분기한다.
     */
    private fun handleUpsert(command: IndexingCommand, metadata: IndexingMessageMetadata) {
        when (command.entityType) {
            IndexEntityType.WORK -> upsertWork(command.entityId, metadata)
            IndexEntityType.EPISODE -> upsertEpisode(command.entityId, metadata)
            IndexEntityType.ASSET -> upsertAsset(command.entityId, metadata)
        }
    }

    /**
     * DELETE 요청을 엔티티 타입별로 분기한다.
     */
    private fun handleDelete(command: IndexingCommand, metadata: IndexingMessageMetadata) {
        when (command.entityType) {
            IndexEntityType.WORK -> deleteWork(command.entityId, metadata)
            IndexEntityType.EPISODE -> deleteEpisode(command.entityId, metadata)
            IndexEntityType.ASSET -> deleteAsset(command.entityId, metadata)
        }
    }

    /**
     * Work 문서를 갱신한다.
     */
    private fun upsertWork(workId: Long, metadata: IndexingMessageMetadata) {
        val source = indexSourceRepository.findWork(workId)
            ?: throw missingSource(IndexEntityType.WORK, workId)
        val document = source.toDocument()
        indexingGateway.upsertWork(document)
        logger.info(
            "Work 색인 완료. workId={}, eventId={}, traceId={}",
            workId,
            metadata.eventId,
            metadata.traceId
        )
    }

    /**
     * Episode 문서를 갱신한다.
     */
    private fun upsertEpisode(episodeId: Long, metadata: IndexingMessageMetadata) {
        val source = indexSourceRepository.findEpisode(episodeId)
            ?: throw missingSource(IndexEntityType.EPISODE, episodeId)
        val document = source.toDocument()
        indexingGateway.upsertEpisode(document)
        logger.info(
            "Episode 색인 완료. episodeId={}, eventId={}, traceId={}",
            episodeId,
            metadata.eventId,
            metadata.traceId
        )
    }

    /**
     * Asset 문서를 갱신한다.
     */
    private fun upsertAsset(assetId: Long, metadata: IndexingMessageMetadata) {
        val source = indexSourceRepository.findAsset(assetId)
            ?: throw missingSource(IndexEntityType.ASSET, assetId)
        val document = source.toDocument()
        indexingGateway.upsertAsset(document)
        logger.info(
            "Asset 색인 완료. assetId={}, eventId={}, traceId={}",
            assetId,
            metadata.eventId,
            metadata.traceId
        )
    }

    /**
     * Work 문서를 삭제한다.
     */
    private fun deleteWork(workId: Long, metadata: IndexingMessageMetadata) {
        indexingGateway.deleteWork(workId)
        logger.info(
            "Work 색인 삭제 완료. workId={}, eventId={}, traceId={}",
            workId,
            metadata.eventId,
            metadata.traceId
        )
    }

    /**
     * Episode 문서를 삭제한다.
     */
    private fun deleteEpisode(episodeId: Long, metadata: IndexingMessageMetadata) {
        indexingGateway.deleteEpisode(episodeId)
        logger.info(
            "Episode 색인 삭제 완료. episodeId={}, eventId={}, traceId={}",
            episodeId,
            metadata.eventId,
            metadata.traceId
        )
    }

    /**
     * Asset 문서를 삭제한다.
     */
    private fun deleteAsset(assetId: Long, metadata: IndexingMessageMetadata) {
        indexingGateway.deleteAsset(assetId)
        logger.info(
            "Asset 색인 삭제 완료. assetId={}, eventId={}, traceId={}",
            assetId,
            metadata.eventId,
            metadata.traceId
        )
    }

    /**
     * Work 원천 데이터를 색인 문서로 변환한다.
     */
    private fun WorkIndexSource.toDocument(): WorkIndexDocument {
        return WorkIndexDocument(
            id = id,
            title = title,
            creatorId = creatorId,
            status = status,
            defaultLanguage = defaultLanguage,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    /**
     * Episode 원천 데이터를 색인 문서로 변환한다.
     */
    private fun EpisodeIndexSource.toDocument(): EpisodeIndexDocument {
        return EpisodeIndexDocument(
            id = id,
            workId = workId,
            title = title,
            seq = seq,
            publishedAt = publishedAt,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    /**
     * Asset 원천 데이터를 색인 문서로 변환한다.
     */
    private fun AssetIndexSource.toDocument(): AssetIndexDocument {
        return AssetIndexDocument(
            id = id,
            episodeId = episodeId,
            fileName = fileName,
            contentType = contentType,
            size = size,
            checksum = checksum,
            storageKey = storageKey,
            status = status,
            creatorId = creatorId,
            uploadId = uploadId,
            storageBucket = storageBucket,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    /**
     * 색인 원천 데이터를 찾지 못했을 때 사용하는 예외를 생성한다.
     */
    private fun missingSource(entityType: IndexEntityType, entityId: Long): BusinessException {
        return BusinessException(
            errorCode = ErrorCode.NOT_FOUND,
            details = mapOf("entityType" to entityType.name, "entityId" to entityId.toString()),
            message = "색인 대상 데이터를 찾을 수 없습니다."
        )
    }
}
