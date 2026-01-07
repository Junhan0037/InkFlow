package com.inkflow.common.grpc

import io.grpc.Metadata

/**
 * gRPC 메타데이터 키를 표준화한다.
 */
object GrpcMetadataKeys {
    /**
     * 요청 식별자를 전달하는 메타데이터 키.
     */
    const val REQUEST_ID_HEADER = "x-request-id"

    /**
     * 추적 식별자를 전달하는 메타데이터 키.
     */
    const val TRACE_ID_HEADER = "x-trace-id"

    /**
     * 호출 주체(사용자/서비스) 식별자를 전달하는 메타데이터 키.
     */
    const val ACTOR_ID_HEADER = "x-actor-id"

    /**
     * 요청 식별자 메타데이터 키를 정의한다.
     */
    val REQUEST_ID: Metadata.Key<String> =
        Metadata.Key.of(REQUEST_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER)

    /**
     * 추적 식별자 메타데이터 키를 정의한다.
     */
    val TRACE_ID: Metadata.Key<String> =
        Metadata.Key.of(TRACE_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER)

    /**
     * 호출 주체 식별자 메타데이터 키를 정의한다.
     */
    val ACTOR_ID: Metadata.Key<String> =
        Metadata.Key.of(ACTOR_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER)
}
