package com.inkflow.upload.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.inkflow.common.error.BusinessException
import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.upload.domain.IdempotencyKeyRepository
import com.inkflow.upload.domain.IdempotencyRecord
import com.inkflow.upload.domain.IdempotencyStatus
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * Idempotency 키 기반 중복 요청 처리를 담당하는 서비스.
 */
@Service
class IdempotencyService(
    private val idempotencyKeyRepository: IdempotencyKeyRepository,
    private val objectMapper: ObjectMapper,
    private val properties: IdempotencyProperties,
    private val clock: Clock
) {
    /**
     * Idempotency 키가 존재하면 저장된 결과를 반환하고, 없으면 요청을 실행한다.
     */
    fun <T : Any> execute(
        key: String,
        resultClass: Class<T>,
        actionName: String,
        supplier: () -> T
    ): T {
        val existing = idempotencyKeyRepository.find(key)
        if (existing != null) {
            return when (existing.status) {
                IdempotencyStatus.COMPLETED -> decodePayload(existing, resultClass)
                IdempotencyStatus.IN_PROGRESS -> throw BusinessException(
                    errorCode = ErrorCode.CONFLICT,
                    details = mapOf("idempotencyKey" to key, "action" to actionName),
                    message = "동일 Idempotency-Key 요청이 처리 중입니다."
                )
            }
        }

        val now = Instant.now(clock)
        val inProgress = IdempotencyRecord(
            key = key,
            status = IdempotencyStatus.IN_PROGRESS,
            payload = null,
            createdAt = now,
            updatedAt = now
        )

        val saved = idempotencyKeyRepository.saveIfAbsent(inProgress, properties.ttl)
        if (!saved) {
            // 동시성 경합 시 최신 레코드를 다시 조회한다.
            val latest = idempotencyKeyRepository.find(key)
                ?: throw SystemException(
                    errorCode = ErrorCode.INTERNAL_ERROR,
                    details = mapOf("idempotencyKey" to key),
                    message = "Idempotency 레코드를 확인할 수 없습니다."
                )
            return when (latest.status) {
                IdempotencyStatus.COMPLETED -> decodePayload(latest, resultClass)
                IdempotencyStatus.IN_PROGRESS -> throw BusinessException(
                    errorCode = ErrorCode.CONFLICT,
                    details = mapOf("idempotencyKey" to key, "action" to actionName),
                    message = "동일 Idempotency-Key 요청이 처리 중입니다."
                )
            }
        }

        return try {
            val result = supplier()
            val payload = encodePayload(result)
            val completed = inProgress.copy(
                status = IdempotencyStatus.COMPLETED,
                payload = payload,
                updatedAt = Instant.now(clock)
            )
            idempotencyKeyRepository.save(completed, properties.ttl)
            result
        } catch (exception: Exception) {
            // 실패 시 재시도를 허용하도록 Idempotency 레코드를 제거한다.
            idempotencyKeyRepository.delete(key)
            throw exception
        }
    }

    /**
     * 결과 객체를 JSON payload로 변환한다.
     */
    private fun encodePayload(result: Any): String {
        return try {
            objectMapper.writeValueAsString(result)
        } catch (exception: Exception) {
            throw SystemException(
                errorCode = ErrorCode.INTERNAL_ERROR,
                message = "Idempotency 결과 직렬화에 실패했습니다.",
                cause = exception
            )
        }
    }

    /**
     * 저장된 JSON payload를 결과 객체로 복원한다.
     */
    private fun <T : Any> decodePayload(record: IdempotencyRecord, resultClass: Class<T>): T {
        val payload = record.payload ?: throw SystemException(
            errorCode = ErrorCode.INTERNAL_ERROR,
            details = mapOf("idempotencyKey" to record.key),
            message = "Idempotency 결과 payload가 비어 있습니다."
        )

        return try {
            objectMapper.readValue(payload, resultClass)
        } catch (exception: Exception) {
            throw SystemException(
                errorCode = ErrorCode.INTERNAL_ERROR,
                details = mapOf("idempotencyKey" to record.key),
                message = "Idempotency 결과 역직렬화에 실패했습니다.",
                cause = exception
            )
        }
    }
}
