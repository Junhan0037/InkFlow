package com.inkflow.publish.infra.grpc

import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.GrpcErrorMapper
import com.inkflow.common.error.InkFlowException
import com.inkflow.publish.application.CreateSnapshotCommand
import com.inkflow.publish.application.PublishSnapshotApplicationService
import com.inkflow.publish.application.RollbackSnapshotCommand
import com.inkflow.publish.v1.CreateSnapshotRequest
import com.inkflow.publish.v1.CreateSnapshotResponse
import com.inkflow.publish.v1.PublishServiceGrpc
import com.inkflow.publish.v1.RollbackRequest
import com.inkflow.publish.v1.RollbackResponse
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Component

/**
 * 퍼블리시 gRPC 요청을 처리하는 서비스 구현체.
 */
@Component
class PublishGrpcService(
    private val publishSnapshotApplicationService: PublishSnapshotApplicationService
) : PublishServiceGrpc.PublishServiceImplBase() {
    /**
     * 퍼블리시 스냅샷 생성 요청을 처리한다.
     */
    override fun createSnapshot(
        request: CreateSnapshotRequest,
        responseObserver: StreamObserver<CreateSnapshotResponse>
    ) {
        try {
            val command = CreateSnapshotCommand(
                episodeId = request.episodeId,
                region = normalize(request.region),
                language = normalize(request.language),
                requestId = normalize(request.requestId)
            )
            val result = publishSnapshotApplicationService.createSnapshot(command)
            val response = CreateSnapshotResponse.newBuilder()
                .setSnapshotId(result.snapshotId)
                .setPublishVersion(result.publishVersion)
                .build()
            responseObserver.onNext(response)
            responseObserver.onCompleted()
        } catch (exception: InkFlowException) {
            responseObserver.onError(
                GrpcErrorMapper.toRuntimeException(
                    exception.errorCode,
                    exception.message,
                    exception.details
                )
            )
        } catch (exception: IllegalArgumentException) {
            responseObserver.onError(
                GrpcErrorMapper.toRuntimeException(
                    ErrorCode.INVALID_REQUEST,
                    exception.message
                )
            )
        } catch (exception: Exception) {
            responseObserver.onError(
                GrpcErrorMapper.toRuntimeException(
                    ErrorCode.INTERNAL_ERROR,
                    "퍼블리시 스냅샷 생성 중 오류가 발생했습니다."
                )
            )
        }
    }

    /**
     * 퍼블리시 스냅샷 롤백 요청을 처리한다.
     */
    override fun rollback(
        request: RollbackRequest,
        responseObserver: StreamObserver<RollbackResponse>
    ) {
        try {
            val command = RollbackSnapshotCommand(
                episodeId = request.episodeId,
                publishVersion = request.publishVersion,
                requestId = normalize(request.requestId)
            )
            val result = publishSnapshotApplicationService.rollback(command)
            val response = RollbackResponse.newBuilder()
                .setSuccess(result.success)
                .build()
            responseObserver.onNext(response)
            responseObserver.onCompleted()
        } catch (exception: InkFlowException) {
            responseObserver.onError(
                GrpcErrorMapper.toRuntimeException(
                    exception.errorCode,
                    exception.message,
                    exception.details
                )
            )
        } catch (exception: IllegalArgumentException) {
            responseObserver.onError(
                GrpcErrorMapper.toRuntimeException(
                    ErrorCode.INVALID_REQUEST,
                    exception.message
                )
            )
        } catch (exception: Exception) {
            responseObserver.onError(
                GrpcErrorMapper.toRuntimeException(
                    ErrorCode.INTERNAL_ERROR,
                    "퍼블리시 롤백 처리 중 오류가 발생했습니다."
                )
            )
        }
    }

    /**
     * gRPC 요청 문자열을 트림 처리한다.
     */
    private fun normalize(value: String): String {
        return value.trim()
    }
}
