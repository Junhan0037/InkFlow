package com.inkflow.indexing.infra.elasticsearch

import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.indexing.application.IndexingProperties
import com.inkflow.indexing.domain.AssetIndexDocument
import com.inkflow.indexing.domain.EpisodeIndexDocument
import com.inkflow.indexing.domain.IndexingGateway
import com.inkflow.indexing.domain.WorkIndexDocument
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

/**
 * Elasticsearch REST API를 사용하는 색인 게이트웨이 구현체.
 */
@Component
class ElasticsearchIndexingGateway(
    webClientBuilder: WebClient.Builder,
    private val properties: IndexingProperties
) : IndexingGateway {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val webClient: WebClient = webClientBuilder.baseUrl(properties.baseUrl).build()

    /**
     * Work 문서를 UPSERT한다.
     */
    override fun upsertWork(document: WorkIndexDocument) {
        upsertDocument(properties.indices.works, document.id, document, "Work")
    }

    /**
     * Episode 문서를 UPSERT한다.
     */
    override fun upsertEpisode(document: EpisodeIndexDocument) {
        upsertDocument(properties.indices.episodes, document.id, document, "Episode")
    }

    /**
     * Asset 문서를 UPSERT한다.
     */
    override fun upsertAsset(document: AssetIndexDocument) {
        upsertDocument(properties.indices.assets, document.id, document, "Asset")
    }

    /**
     * Work 문서를 삭제한다.
     */
    override fun deleteWork(workId: Long) {
        deleteDocument(properties.indices.works, workId, "Work")
    }

    /**
     * Episode 문서를 삭제한다.
     */
    override fun deleteEpisode(episodeId: Long) {
        deleteDocument(properties.indices.episodes, episodeId, "Episode")
    }

    /**
     * Asset 문서를 삭제한다.
     */
    override fun deleteAsset(assetId: Long) {
        deleteDocument(properties.indices.assets, assetId, "Asset")
    }

    /**
     * Elasticsearch에 문서를 UPSERT한다.
     */
    private fun upsertDocument(indexName: String, documentId: Long, document: Any, entityName: String) {
        try {
            webClient.put()
                .uri("/{index}/_doc/{id}", indexName, documentId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(document)
                .retrieve()
                .onStatus({ status -> status.isError }, { response ->
                    response.bodyToMono(String::class.java)
                        .defaultIfEmpty("")
                        .flatMap { body ->
                            Mono.error(buildDependencyFailure(entityName, indexName, documentId, body))
                        }
                })
                .toBodilessEntity()
                .block()
            logger.info("Elasticsearch UPSERT 완료. entity={}, index={}, id={}", entityName, indexName, documentId)
        } catch (exception: SystemException) {
            // 이미 래핑된 시스템 예외는 그대로 전파한다.
            throw exception
        } catch (exception: Exception) {
            throw SystemException(
                errorCode = ErrorCode.DEPENDENCY_FAILURE,
                details = mapOf("index" to indexName, "id" to documentId.toString(), "entity" to entityName),
                message = "Elasticsearch UPSERT 요청에 실패했습니다.",
                cause = exception
            )
        }
    }

    /**
     * Elasticsearch에서 문서를 삭제한다.
     */
    private fun deleteDocument(indexName: String, documentId: Long, entityName: String) {
        try {
            webClient.delete()
                .uri("/{index}/_doc/{id}", indexName, documentId)
                .retrieve()
                // 삭제는 멱등 처리이므로 404는 정상으로 간주한다.
                .onStatus({ status -> status.isError && status != HttpStatus.NOT_FOUND }, { response ->
                    response.bodyToMono(String::class.java)
                        .defaultIfEmpty("")
                        .flatMap { body ->
                            Mono.error(buildDependencyFailure(entityName, indexName, documentId, body))
                        }
                })
                .toBodilessEntity()
                .block()
            logger.info("Elasticsearch DELETE 완료. entity={}, index={}, id={}", entityName, indexName, documentId)
        } catch (exception: SystemException) {
            // 이미 래핑된 시스템 예외는 그대로 전파한다.
            throw exception
        } catch (exception: Exception) {
            throw SystemException(
                errorCode = ErrorCode.DEPENDENCY_FAILURE,
                details = mapOf("index" to indexName, "id" to documentId.toString(), "entity" to entityName),
                message = "Elasticsearch DELETE 요청에 실패했습니다.",
                cause = exception
            )
        }
    }

    /**
     * Elasticsearch 오류 응답을 시스템 예외로 변환한다.
     */
    private fun buildDependencyFailure(
        entityName: String,
        indexName: String,
        documentId: Long,
        responseBody: String
    ): SystemException {
        return SystemException(
            errorCode = ErrorCode.DEPENDENCY_FAILURE,
            details = mapOf(
                "entity" to entityName,
                "index" to indexName,
                "id" to documentId.toString(),
                "response" to responseBody
            ),
            message = "Elasticsearch 응답 오류가 발생했습니다."
        )
    }
}
