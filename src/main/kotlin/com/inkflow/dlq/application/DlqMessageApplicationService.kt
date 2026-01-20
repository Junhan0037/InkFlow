package com.inkflow.dlq.application

import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.common.kafka.config.InkflowKafkaProperties
import com.inkflow.dlq.domain.DlqMessage
import com.inkflow.dlq.domain.DlqMessageRepository
import com.inkflow.dlq.domain.DlqMessageStatus
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.time.Clock
import java.time.Instant

/**
 * DLQ 메시지 적재/조회/재처리 흐름을 담당하는 애플리케이션 서비스.
 */
@Service
class DlqMessageApplicationService(
    private val repository: DlqMessageRepository,
    private val payloadParser: DlqMessagePayloadParser,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val kafkaProperties: InkflowKafkaProperties,
    private val clock: Clock
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * DLQ 토픽에서 수신한 레코드를 저장한다.
     */
    fun capture(record: ConsumerRecord<String, String>): DlqMessage {
        val payload = record.value()?.takeIf { it.isNotBlank() } ?: "<EMPTY_PAYLOAD>"
        val headerAccessor = DlqHeaderAccessor(record)
        val originalTopic = resolveOriginalTopic(record.topic(), headerAccessor.originalTopic)
        val sourceKey = buildSourceKey(originalTopic, headerAccessor.originalPartition, headerAccessor.originalOffset, record)
        val existing = repository.findBySourceKey(sourceKey)
        if (existing != null) {
            // 동일한 원본 메시지는 중복 적재를 방지한다.
            return existing
        }

        val summary = payloadParser.parse(payload)
        val now = Instant.now(clock)
        val message = DlqMessage(
            sourceKey = sourceKey,
            dlqTopic = record.topic(),
            originalTopic = originalTopic,
            originalPartition = headerAccessor.originalPartition,
            originalOffset = headerAccessor.originalOffset,
            originalTimestamp = headerAccessor.originalTimestamp,
            messageKey = record.key(),
            payload = payload,
            headers = headerAccessor.allHeaders,
            eventId = summary.eventId,
            eventType = summary.eventType,
            producer = summary.producer,
            traceId = summary.traceId,
            idempotencyKey = summary.idempotencyKey,
            occurredAt = summary.occurredAt,
            errorType = headerAccessor.errorType,
            errorMessage = headerAccessor.errorMessage,
            errorStacktrace = headerAccessor.errorStacktrace,
            status = DlqMessageStatus.PENDING,
            reprocessCount = 0,
            lastReprocessedAt = null,
            lastReprocessBy = null,
            lastReprocessReason = null,
            lastReprocessError = null,
            storedAt = now
        )

        return try {
            repository.save(message)
        } catch (exception: DuplicateKeyException) {
            // 동시성 경쟁으로 중복 적재가 발생한 경우 기존 데이터를 재사용한다.
            repository.findBySourceKey(sourceKey) ?: throw exception
        }
    }

    /**
     * DLQ 메시지를 검색 조건에 따라 조회한다.
     */
    fun search(criteria: DlqSearchCriteria): DlqMessagePage {
        validateSearchCriteria(criteria)
        val pageRequest = PageRequest.of(
            criteria.page,
            criteria.size,
            Sort.by(Sort.Direction.DESC, "storedAt")
        )
        val page = repository.search(criteria.status, criteria.originalTopic, pageRequest)
        return DlqMessagePage.from(page)
    }

    /**
     * DLQ 메시지 단건을 조회한다.
     */
    fun get(id: String): DlqMessage {
        return repository.findById(id)
            ?: throw BusinessException(
                errorCode = ErrorCode.NOT_FOUND,
                message = "DLQ 메시지를 찾을 수 없습니다."
            )
    }

    /**
     * DLQ 메시지를 원본 토픽으로 재발행한다.
     */
    fun reprocess(command: DlqReprocessCommand): DlqReprocessResult {
        val message = get(command.messageId)
        if (message.status == DlqMessageStatus.REPROCESSED) {
            throw BusinessException(
                errorCode = ErrorCode.CONFLICT,
                message = "이미 재처리 완료된 DLQ 메시지입니다."
            )
        }

        val now = Instant.now(clock)
        val reprocessing = message.copy(
            status = DlqMessageStatus.REPROCESSING,
            reprocessCount = message.reprocessCount + 1,
            lastReprocessedAt = now,
            lastReprocessBy = command.requestedBy,
            lastReprocessReason = command.reason,
            lastReprocessError = null
        )
        repository.save(reprocessing)

        return try {
            // 메시지 키가 없는 경우에는 토픽/페이로드만 사용해 재발행한다.
            val sendFuture = message.messageKey?.let { key ->
                kafkaTemplate.send(message.originalTopic, key, message.payload)
            } ?: kafkaTemplate.send(message.originalTopic, message.payload)
            sendFuture.get()
            val completed = reprocessing.copy(
                status = DlqMessageStatus.REPROCESSED,
                lastReprocessedAt = Instant.now(clock)
            )
            repository.save(completed)
            DlqReprocessResult(
                messageId = completed.id ?: message.id.orEmpty(),
                status = completed.status,
                reprocessCount = completed.reprocessCount,
                reprocessedAt = completed.lastReprocessedAt,
                errorMessage = null
            )
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            handleReprocessFailure(reprocessing, exception)
        } catch (exception: Exception) {
            handleReprocessFailure(reprocessing, exception)
        }
    }

    /**
     * 재처리 실패 시 상태를 업데이트하고 예외를 전파한다.
     */
    private fun handleReprocessFailure(
        message: DlqMessage,
        exception: Exception
    ): DlqReprocessResult {
        val failed = message.copy(
            status = DlqMessageStatus.FAILED,
            lastReprocessError = exception.message ?: "재처리에 실패했습니다."
        )
        repository.save(failed)
        logger.error(
            "DLQ 메시지 재처리에 실패했습니다. messageId={}, originalTopic={}",
            failed.id,
            failed.originalTopic,
            exception
        )
        throw SystemException(
            errorCode = ErrorCode.DEPENDENCY_FAILURE,
            details = mapOf(
                "messageId" to (failed.id ?: "unknown"),
                "originalTopic" to failed.originalTopic
            ),
            message = "DLQ 메시지 재처리에 실패했습니다.",
            cause = exception
        )
    }

    /**
     * 검색 파라미터 유효성을 검증한다.
     */
    private fun validateSearchCriteria(criteria: DlqSearchCriteria) {
        if (criteria.page < 0) {
            throw BusinessException(ErrorCode.INVALID_REQUEST, message = "page는 0 이상이어야 합니다.")
        }
        if (criteria.size <= 0 || criteria.size > MAX_PAGE_SIZE) {
            throw BusinessException(
                ErrorCode.INVALID_REQUEST,
                message = "size는 1 이상 ${MAX_PAGE_SIZE} 이하이어야 합니다."
            )
        }
    }

    /**
     * DLQ 토픽명에서 원본 토픽명을 유추한다.
     */
    private fun resolveOriginalTopic(dlqTopic: String, originalTopicHeader: String?): String {
        if (!originalTopicHeader.isNullOrBlank()) {
            return originalTopicHeader
        }
        val prefix = "${kafkaProperties.topics.dlqPrefix}."
        return if (dlqTopic.startsWith(prefix)) {
            dlqTopic.removePrefix(prefix)
        } else {
            dlqTopic
        }
    }

    /**
     * 원본 메시지 단위를 고유하게 식별하는 키를 생성한다.
     */
    private fun buildSourceKey(
        originalTopic: String,
        originalPartition: Int?,
        originalOffset: Long?,
        record: ConsumerRecord<String, String>
    ): String {
        return if (originalPartition != null && originalOffset != null) {
            "${originalTopic}:${originalPartition}:${originalOffset}"
        } else {
            "${record.topic()}:${record.partition()}:${record.offset()}"
        }
    }

    /**
     * DLQ 헤더 정보를 구조화해 접근한다.
     */
    private class DlqHeaderAccessor(record: ConsumerRecord<String, String>) {
        val allHeaders: Map<String, String> = extractHeaders(record)
        val originalTopic: String? = headerAsString(record, KafkaHeaders.DLT_ORIGINAL_TOPIC)
        val originalPartition: Int? = headerAsInt(record, KafkaHeaders.DLT_ORIGINAL_PARTITION)
        val originalOffset: Long? = headerAsLong(record, KafkaHeaders.DLT_ORIGINAL_OFFSET)
        val originalTimestamp: Instant? = headerAsLong(record, KafkaHeaders.DLT_ORIGINAL_TIMESTAMP)
            ?.let { Instant.ofEpochMilli(it) }
            ?: record.timestamp().takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) }
        val errorType: String? = headerAsString(record, KafkaHeaders.DLT_EXCEPTION_FQCN)
        val errorMessage: String? = headerAsString(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE)
        val errorStacktrace: String? = headerAsString(record, KafkaHeaders.DLT_EXCEPTION_STACKTRACE)

        /**
         * 레코드 헤더를 문자열 맵으로 변환한다.
         */
        private fun extractHeaders(record: ConsumerRecord<String, String>): Map<String, String> {
            val headers = LinkedHashMap<String, String>()
            record.headers().forEach { header ->
                val value = header.value()
                headers[header.key()] = value?.let { String(it, Charsets.UTF_8) } ?: ""
            }
            return headers
        }

        /**
         * 헤더를 문자열로 변환한다.
         */
        private fun headerAsString(record: ConsumerRecord<String, String>, name: String): String? {
            return record.headers().lastHeader(name)?.value()?.let { String(it, Charsets.UTF_8) }
        }

        /**
         * 헤더를 Int로 변환한다.
         */
        private fun headerAsInt(record: ConsumerRecord<String, String>, name: String): Int? {
            val value = record.headers().lastHeader(name)?.value() ?: return null
            return try {
                ByteBuffer.wrap(value).int
            } catch (exception: Exception) {
                null
            }
        }

        /**
         * 헤더를 Long으로 변환한다.
         */
        private fun headerAsLong(record: ConsumerRecord<String, String>, name: String): Long? {
            val value = record.headers().lastHeader(name)?.value() ?: return null
            return try {
                ByteBuffer.wrap(value).long
            } catch (exception: Exception) {
                null
            }
        }
    }

    companion object {
        private const val MAX_PAGE_SIZE = 200
    }
}

/**
 * DLQ 검색 요청을 전달한다.
 */
data class DlqSearchCriteria(
    val status: DlqMessageStatus?,
    val originalTopic: String?,
    val page: Int,
    val size: Int
)

/**
 * DLQ 메시지 페이지 응답 모델.
 */
data class DlqMessagePage(
    val items: List<DlqMessage>,
    val total: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int
) {
    companion object {
        /**
         * Spring Data Page를 도메인 페이지로 변환한다.
         */
        fun from(page: Page<DlqMessage>): DlqMessagePage {
            return DlqMessagePage(
                items = page.content,
                total = page.totalElements,
                page = page.number,
                size = page.size,
                totalPages = page.totalPages
            )
        }
    }
}

/**
 * DLQ 재처리 요청 정보를 전달한다.
 */
data class DlqReprocessCommand(
    val messageId: String,
    val requestedBy: String?,
    val reason: String?
)

/**
 * DLQ 재처리 결과를 반환한다.
 */
data class DlqReprocessResult(
    val messageId: String,
    val status: DlqMessageStatus,
    val reprocessCount: Int,
    val reprocessedAt: Instant?,
    val errorMessage: String?
)
