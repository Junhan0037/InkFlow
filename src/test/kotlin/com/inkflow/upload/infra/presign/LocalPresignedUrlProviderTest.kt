package com.inkflow.upload.infra.presign

import com.inkflow.upload.application.UploadAccelerationMode
import com.inkflow.upload.application.UploadAccelerationProperties
import com.inkflow.upload.application.UploadCdnPresignProperties
import com.inkflow.upload.application.UploadPresignProperties
import com.inkflow.upload.application.UploadRegionPresignProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * 로컬 presigned URL 발급기의 업로드 가속 옵션 동작을 검증.
 */
class LocalPresignedUrlProviderTest {
    /**
     * 멀티 리전과 CDN 가속 옵션이 활성화되면 가속 URL이 포함된다.
     */
    @Test
    fun createPresignedMultipart_includesAccelerationUrls() {
        val properties = UploadPresignProperties(
            baseUrl = "http://origin.local",
            acceleration = UploadAccelerationProperties(
                enabled = true,
                regions = listOf(
                    UploadRegionPresignProperties(
                        region = "ap-northeast-2",
                        baseUrl = "https://ap-northeast-2.local"
                    ),
                    UploadRegionPresignProperties(
                        region = "us-west-2",
                        baseUrl = "https://us-west-2.local"
                    )
                ),
                cdn = UploadCdnPresignProperties(
                    enabled = true,
                    baseUrl = "https://cdn.local"
                )
            )
        )
        val provider = LocalPresignedUrlProvider(properties)

        val result = provider.createPresignedMultipart(
            bucket = "inkflow-uploads",
            key = "uploads/upl-1/image.png",
            totalParts = 1,
            expiresAt = Instant.parse("2026-01-01T00:00:00Z")
        )

        val accelerationUrls = result.presignedUrls.first().accelerationUrls
        assertEquals(3, accelerationUrls.size)
        assertTrue(
            accelerationUrls.any {
                it.mode == UploadAccelerationMode.REGION &&
                    it.region == "ap-northeast-2" &&
                    it.url.startsWith("https://ap-northeast-2.local/")
            }
        )
        assertTrue(
            accelerationUrls.any {
                it.mode == UploadAccelerationMode.REGION &&
                    it.region == "us-west-2" &&
                    it.url.startsWith("https://us-west-2.local/")
            }
        )
        assertTrue(
            accelerationUrls.any {
                it.mode == UploadAccelerationMode.CDN &&
                    it.url.startsWith("https://cdn.local/")
            }
        )
        // 모든 가속 URL에 파트 번호가 포함되는지 확인한다.
        assertTrue(accelerationUrls.all { it.url.contains("partNumber=1") })
    }

    /**
     * 가속 옵션이 비활성화면 가속 URL 목록이 비어 있어야 한다.
     */
    @Test
    fun createPresignedMultipart_returnsEmptyWhenAccelerationDisabled() {
        val properties = UploadPresignProperties(
            baseUrl = "http://origin.local",
            acceleration = UploadAccelerationProperties(
                enabled = false,
                regions = listOf(
                    UploadRegionPresignProperties(
                        region = "ap-northeast-2",
                        baseUrl = "https://ap-northeast-2.local"
                    )
                ),
                cdn = UploadCdnPresignProperties(
                    enabled = true,
                    baseUrl = "https://cdn.local"
                )
            )
        )
        val provider = LocalPresignedUrlProvider(properties)

        val result = provider.createPresignedMultipart(
            bucket = "inkflow-uploads",
            key = "uploads/upl-1/image.png",
            totalParts = 1,
            expiresAt = Instant.parse("2026-01-01T00:00:00Z")
        )

        assertTrue(result.presignedUrls.first().accelerationUrls.isEmpty())
    }
}
