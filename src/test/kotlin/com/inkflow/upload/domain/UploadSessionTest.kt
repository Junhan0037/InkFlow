package com.inkflow.upload.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * UploadSession 도메인 상태 전이와 검증 로직을 검증한다.
 */
class UploadSessionTest {
    private val baseTime: Instant = Instant.parse("2026-01-01T00:00:00Z")

    /**
     * 생성 팩토리가 기본 상태와 초기 값을 설정하는지 확인한다.
     */
    @Test
    fun create_initializesSession() {
        val session = UploadSession.create(
            uploadId = "upload-1",
            episodeId = 10L,
            creatorId = "creator-1",
            fileName = "sample.png",
            contentType = "image/png",
            totalSize = 100L,
            checksum = "checksum",
            totalParts = 2,
            chunkSize = 50L,
            storageBucket = "bucket",
            storageKey = "key",
            multipartUploadId = "multipart-1",
            expiresAt = baseTime.plusSeconds(3600),
            now = baseTime,
            id = UUID.fromString("00000000-0000-0000-0000-000000000001")
        )

        assertEquals(UploadSessionStatus.CREATED, session.status)
        assertEquals(0, session.uploadedSize)
        assertEquals(baseTime, session.updatedAt)
    }

    /**
     * CREATED → UPLOADING 전이가 정상적으로 수행되는지 검증한다.
     */
    @Test
    fun markUploading_transitionsFromCreated() {
        val session = buildSession()

        val updated = session.markUploading(baseTime.plusSeconds(60))

        assertEquals(UploadSessionStatus.UPLOADING, updated.status)
        assertEquals(baseTime.plusSeconds(60), updated.updatedAt)
    }

    /**
     * 업로드 진행 바이트 갱신 시 감소/초과 입력이 거부되는지 확인한다.
     */
    @Test
    fun recordUploadedSize_rejectsDecreaseAndOverflow() {
        val session = buildSession(
            status = UploadSessionStatus.UPLOADING,
            uploadedSize = 50L,
            totalSize = 100L
        )

        val updated = session.recordUploadedSize(80L, baseTime.plusSeconds(60))
        assertEquals(80L, updated.uploadedSize)

        assertThrows(IllegalArgumentException::class.java) {
            session.recordUploadedSize(40L, baseTime.plusSeconds(120))
        }

        assertThrows(IllegalArgumentException::class.java) {
            session.recordUploadedSize(120L, baseTime.plusSeconds(180))
        }
    }

    /**
     * 완료 전이는 UPLOADING 상태와 전체 업로드 완료 조건을 만족해야 한다.
     */
    @Test
    fun markCompleted_requiresUploadingAndFullUpload() {
        val createdSession = buildSession(status = UploadSessionStatus.CREATED)
        assertThrows(IllegalArgumentException::class.java) {
            createdSession.markCompleted(baseTime.plusSeconds(60))
        }

        val partialSession = buildSession(
            status = UploadSessionStatus.UPLOADING,
            uploadedSize = 80L,
            totalSize = 100L
        )
        assertThrows(IllegalArgumentException::class.java) {
            partialSession.markCompleted(baseTime.plusSeconds(60))
        }

        val completedSession = buildSession(
            status = UploadSessionStatus.UPLOADING,
            uploadedSize = 100L,
            totalSize = 100L
        ).markCompleted(baseTime.plusSeconds(120))
        assertEquals(UploadSessionStatus.COMPLETED, completedSession.status)
    }

    /**
     * COMPLETED 상태에서는 만료/중단 전이가 거부되는지 확인한다.
     */
    @Test
    fun markExpiredAndAborted_rejectsCompleted() {
        val completedSession = buildSession(
            status = UploadSessionStatus.COMPLETED,
            uploadedSize = 100L,
            totalSize = 100L
        )

        assertThrows(IllegalArgumentException::class.java) {
            completedSession.markExpired(baseTime.plusSeconds(60))
        }

        assertThrows(IllegalArgumentException::class.java) {
            completedSession.markAborted(baseTime.plusSeconds(60))
        }
    }

    /**
     * 만료 여부 판단이 기준 시각에 따라 올바르게 동작하는지 확인한다.
     */
    @Test
    fun isExpired_returnsExpected() {
        val session = buildSession(expiresAt = baseTime.plusSeconds(60))

        assertTrue(!session.isExpired(baseTime))
        assertTrue(session.isExpired(baseTime.plusSeconds(120)))
    }

    /**
     * 테스트용 UploadSession을 생성한다.
     */
    private fun buildSession(
        status: UploadSessionStatus = UploadSessionStatus.CREATED,
        uploadedSize: Long = 0L,
        totalSize: Long = 100L,
        expiresAt: Instant = baseTime.plusSeconds(3600)
    ): UploadSession {
        val session = UploadSession.create(
            uploadId = "upload-1",
            episodeId = 10L,
            creatorId = "creator-1",
            fileName = "sample.png",
            contentType = "image/png",
            totalSize = totalSize,
            checksum = "checksum",
            totalParts = 2,
            chunkSize = 50L,
            storageBucket = "bucket",
            storageKey = "key",
            multipartUploadId = "multipart-1",
            expiresAt = expiresAt,
            now = baseTime,
            id = UUID.fromString("00000000-0000-0000-0000-000000000001")
        )
        // 테스트 목적에 맞게 상태와 업로드 크기를 조정한다.
        return session.copy(status = status, uploadedSize = uploadedSize)
    }
}
