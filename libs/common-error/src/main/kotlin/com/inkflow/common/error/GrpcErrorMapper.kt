package com.inkflow.common.error

import io.grpc.Metadata
import io.grpc.StatusRuntimeException

/**
 * ErrorCode를 gRPC 오류로 변환하는 유틸리티.
 */
object GrpcErrorMapper {
    /**
     * 에러 코드를 전달하는 메타데이터 키.
     */
    private val ERROR_CODE_KEY: Metadata.Key<String> =
        Metadata.Key.of("x-error-code", Metadata.ASCII_STRING_MARSHALLER)

    /**
     * 재시도 가능 여부를 전달하는 메타데이터 키.
     */
    private val RETRYABLE_KEY: Metadata.Key<String> =
        Metadata.Key.of("x-error-retryable", Metadata.ASCII_STRING_MARSHALLER)

    /**
     * 상세 메시지를 전달하는 메타데이터 키.
     */
    private val DETAIL_KEY: Metadata.Key<String> =
        Metadata.Key.of("x-error-detail", Metadata.ASCII_STRING_MARSHALLER)

    /**
     * ErrorCode를 StatusRuntimeException으로 변환한다.
     */
    fun toRuntimeException(
        errorCode: ErrorCode,
        message: String? = null,
        details: Map<String, String> = emptyMap()
    ): StatusRuntimeException {
        val metadata = Metadata()
        metadata.put(ERROR_CODE_KEY, errorCode.code)
        metadata.put(RETRYABLE_KEY, errorCode.retryable.toString())
        formatDetails(details)?.let { metadata.put(DETAIL_KEY, it) }
        return errorCode.toGrpcStatus(message).asRuntimeException(metadata)
    }

    /**
     * 상세 맵을 문자열로 변환한다.
     */
    private fun formatDetails(details: Map<String, String>): String? {
        if (details.isEmpty()) {
            return null
        }
        return details.entries.joinToString(",") { (key, value) -> "$key=$value" }
    }
}
