package com.inkflow.contract

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * gRPC 프로토콜 버퍼 정의 파일의 계약을 검증한다.
 */
class GrpcProtoContractTest {
    private val protoPath: Path = Paths.get("docs", "proto", "inkflow", "publish", "v1", "publish.proto")

    /**
     * 퍼블리시 서비스의 필수 정의가 유지되는지 확인한다.
     */
    @Test
    fun publishProto_definesCoreServiceAndMessages() {
        assertTrue(Files.exists(protoPath), "gRPC proto 파일이 존재하지 않습니다: $protoPath")
        val content = Files.readString(protoPath, Charsets.UTF_8)

        assertTrue(content.contains("syntax = \"proto3\";"), "proto3 선언이 누락되었습니다.")
        assertTrue(content.contains("package inkflow.publish.v1;"), "패키지 선언이 누락되었습니다.")
        assertTrue(content.contains("option java_package = \"com.inkflow.publish.v1\";"), "java_package 옵션이 누락되었습니다.")
        assertTrue(content.contains("service PublishService"), "PublishService 정의가 누락되었습니다.")
        assertTrue(content.contains("rpc CreateSnapshot"), "CreateSnapshot RPC 정의가 누락되었습니다.")
        assertTrue(content.contains("rpc Rollback"), "Rollback RPC 정의가 누락되었습니다.")
        assertTrue(content.contains("message CreateSnapshotRequest"), "CreateSnapshotRequest 메시지가 누락되었습니다.")
        assertTrue(content.contains("message CreateSnapshotResponse"), "CreateSnapshotResponse 메시지가 누락되었습니다.")
        assertTrue(content.contains("message RollbackRequest"), "RollbackRequest 메시지가 누락되었습니다.")
        assertTrue(content.contains("message RollbackResponse"), "RollbackResponse 메시지가 누락되었습니다.")
    }
}
