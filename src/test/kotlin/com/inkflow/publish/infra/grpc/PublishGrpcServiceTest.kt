package com.inkflow.publish.infra.grpc

import com.inkflow.publish.application.CreateSnapshotCommand
import com.inkflow.publish.application.CreateSnapshotResult
import com.inkflow.publish.application.PublishSnapshotApplicationService
import com.inkflow.publish.application.RollbackSnapshotCommand
import com.inkflow.publish.application.RollbackSnapshotResult
import com.inkflow.publish.v1.CreateSnapshotRequest
import com.inkflow.publish.v1.CreateSnapshotResponse
import com.inkflow.publish.v1.RollbackRequest
import com.inkflow.publish.v1.RollbackResponse
import io.grpc.stub.StreamObserver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Publish gRPC 서비스의 요청/응답 매핑을 검증.
 */
class PublishGrpcServiceTest {
    /**
     * CreateSnapshot 요청이 애플리케이션 서비스 호출과 응답으로 매핑되는지 확인한다.
     */
    @Test
    fun createSnapshot_mapsRequestAndReturnsResponse() {
        // 준비: 애플리케이션 서비스 목과 응답 옵저버를 구성한다.
        val applicationService = Mockito.mock(PublishSnapshotApplicationService::class.java)
        val grpcService = PublishGrpcService(applicationService)
        val request = CreateSnapshotRequest.newBuilder()
            .setEpisodeId(1L)
            .setRegion(" KR ")
            .setLanguage(" ko ")
            .setRequestId(" req-1 ")
            .build()
        val expectedCommand = CreateSnapshotCommand(
            episodeId = 1L,
            region = "KR",
            language = "ko",
            requestId = "req-1"
        )
        Mockito.doReturn(CreateSnapshotResult(snapshotId = "snap-1", publishVersion = 2L))
            .`when`(applicationService)
            .createSnapshot(expectedCommand)
        val observer = TestStreamObserver<CreateSnapshotResponse>()

        // 실행: gRPC 요청을 처리한다.
        grpcService.createSnapshot(request, observer)

        // 검증: 응답과 완료 이벤트가 정상 호출된다.
        assertEquals(1, observer.values.size)
        assertEquals("snap-1", observer.values.first().snapshotId)
        assertEquals(2L, observer.values.first().publishVersion)
        assertTrue(observer.completed)
        assertNull(observer.error)
        Mockito.verify(applicationService).createSnapshot(expectedCommand)
    }

    /**
     * Rollback 요청이 애플리케이션 서비스 호출과 응답으로 매핑되는지 확인한다.
     */
    @Test
    fun rollback_mapsRequestAndReturnsResponse() {
        // 준비: 애플리케이션 서비스 목과 응답 옵저버를 구성한다.
        val applicationService = Mockito.mock(PublishSnapshotApplicationService::class.java)
        val grpcService = PublishGrpcService(applicationService)
        val request = RollbackRequest.newBuilder()
            .setEpisodeId(1L)
            .setPublishVersion(1L)
            .setRequestId(" req-rollback ")
            .build()
        val expectedCommand = RollbackSnapshotCommand(
            episodeId = 1L,
            publishVersion = 1L,
            requestId = "req-rollback"
        )
        Mockito.doReturn(RollbackSnapshotResult(success = true, activeVersion = 1L))
            .`when`(applicationService)
            .rollback(expectedCommand)
        val observer = TestStreamObserver<RollbackResponse>()

        // 실행: gRPC 요청을 처리한다.
        grpcService.rollback(request, observer)

        // 검증: 응답과 완료 이벤트가 정상 호출된다.
        assertEquals(1, observer.values.size)
        assertTrue(observer.values.first().success)
        assertTrue(observer.completed)
        assertNull(observer.error)
        Mockito.verify(applicationService).rollback(expectedCommand)
    }

    /**
     * StreamObserver 호출 상태를 검증하기 위한 테스트용 구현체.
     */
    private class TestStreamObserver<T> : StreamObserver<T> {
        val values: MutableList<T> = mutableListOf()
        var error: Throwable? = null
        var completed: Boolean = false

        /**
         * 응답 값을 수집한다.
         */
        override fun onNext(value: T) {
            values.add(value)
        }

        /**
         * 에러 응답을 저장한다.
         */
        override fun onError(t: Throwable) {
            error = t
        }

        /**
         * 완료 이벤트 발생 여부를 기록한다.
         */
        override fun onCompleted() {
            completed = true
        }
    }
}
