package com.inkflow.upload.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * AssetMetadata 도메인 상태 전이와 검증 로직을 검증한다.
 */
class AssetMetadataTest {
    private val baseTime: Instant = Instant.parse("2026-01-01T00:00:00Z")

    /**
     * 생성 팩토리가 기본 상태를 PENDING으로 설정하는지 확인한다.
     */
    @Test
    fun create_initializesPending() {
        val asset = AssetMetadata.create(
            episodeId = 1L,
            creatorId = "creator-1",
            uploadId = "upload-1",
            fileName = "sample.png",
            contentType = "image/png",
            size = 100L,
            checksum = "checksum",
            storageBucket = "bucket",
            storageKey = "key",
            now = baseTime
        )

        assertEquals(AssetStatus.PENDING, asset.status)
        assertEquals(baseTime, asset.updatedAt)
    }

    /**
     * PENDING → STORED 전이가 정상 동작하는지 확인한다.
     */
    @Test
    fun markStored_transitionsFromPending() {
        val asset = buildAsset()

        val updated = asset.markStored(baseTime.plusSeconds(60))

        assertEquals(AssetStatus.STORED, updated.status)
        assertEquals(baseTime.plusSeconds(60), updated.updatedAt)
    }

    /**
     * STORED 상태에서 다시 저장 전이가 거부되는지 확인한다.
     */
    @Test
    fun markStored_rejectsWhenNotPending() {
        val asset = buildAsset(status = AssetStatus.STORED)

        assertThrows(IllegalArgumentException::class.java) {
            asset.markStored(baseTime.plusSeconds(60))
        }
    }

    /**
     * DELETED 상태에서는 실패 전이가 거부되는지 확인한다.
     */
    @Test
    fun markFailed_rejectsDeleted() {
        val asset = buildAsset(status = AssetStatus.DELETED)

        assertThrows(IllegalArgumentException::class.java) {
            asset.markFailed(baseTime.plusSeconds(60))
        }
    }

    /**
     * 이미 삭제된 상태는 다시 삭제할 수 없다.
     */
    @Test
    fun markDeleted_rejectsAlreadyDeleted() {
        val asset = buildAsset(status = AssetStatus.DELETED)

        assertThrows(IllegalArgumentException::class.java) {
            asset.markDeleted(baseTime.plusSeconds(60))
        }
    }

    /**
     * 테스트용 AssetMetadata를 생성한다.
     */
    private fun buildAsset(
        status: AssetStatus = AssetStatus.PENDING
    ): AssetMetadata {
        val asset = AssetMetadata.create(
            episodeId = 1L,
            creatorId = "creator-1",
            uploadId = "upload-1",
            fileName = "sample.png",
            contentType = "image/png",
            size = 100L,
            checksum = "checksum",
            storageBucket = "bucket",
            storageKey = "key",
            now = baseTime
        )
        // 테스트 목적에 맞게 상태를 조정한다.
        return asset.copy(status = status)
    }
}
