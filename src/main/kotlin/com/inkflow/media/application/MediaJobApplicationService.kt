package com.inkflow.media.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Media 작업 요청을 처리하는 애플리케이션 서비스.
 */
@Service
class MediaJobApplicationService {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Media 작업을 수신해 처리 파이프라인으로 전달한다.
     */
    fun handleJob(command: MediaJobCommand, metadata: MediaJobMessageMetadata) {
        // 작업 파이프라인 연동 전까지는 수신 로그로 가시성을 확보한다.
        logger.info(
            "Media 작업 수신. jobId={}, assetId={}, derivativeType={}, eventId={}, traceId={}",
            command.jobId,
            command.assetId,
            command.derivativeType,
            metadata.eventId,
            metadata.traceId
        )
    }
}

/**
 * Media 작업 메시지 메타데이터를 전달하는 DTO.
 */
data class MediaJobMessageMetadata(
    val eventId: UUID,
    val traceId: String?,
    val idempotencyKey: String?
) {
    init {
        require(traceId?.isNotBlank() ?: true) { "traceId는 빈 문자열일 수 없습니다." }
        require(idempotencyKey?.isNotBlank() ?: true) { "idempotencyKey는 빈 문자열일 수 없습니다." }
    }
}
