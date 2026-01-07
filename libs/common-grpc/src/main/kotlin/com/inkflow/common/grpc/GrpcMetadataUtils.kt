package com.inkflow.common.grpc

import io.grpc.Metadata

/**
 * gRPC 메타데이터 파싱 규칙을 모아둔 유틸리티.
 */
object GrpcMetadataUtils {
    /**
     * 문자열 메타데이터를 조회하고 공백을 제거한다.
     */
    fun get(metadata: Metadata, key: Metadata.Key<String>): String? {
        return metadata.get(key)?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * 값이 비어 있지 않을 때만 메타데이터에 저장한다.
     */
    fun putIfAbsent(metadata: Metadata, key: Metadata.Key<String>, value: String?) {
        if (value.isNullOrBlank()) {
            return
        }
        if (metadata.get(key) == null) {
            metadata.put(key, value)
        }
    }
}
