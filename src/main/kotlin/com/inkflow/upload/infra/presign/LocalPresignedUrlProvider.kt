package com.inkflow.upload.infra.presign

import com.inkflow.upload.application.AcceleratedPresignedUrl
import com.inkflow.upload.application.MultipartPresignedUpload
import com.inkflow.upload.application.MultipartPresignedUrlProvider
import com.inkflow.upload.application.PresignedDownloadUrl
import com.inkflow.upload.application.PresignedDownloadUrlProvider
import com.inkflow.upload.application.PresignedPartUrl
import com.inkflow.upload.application.UploadAccelerationMode
import com.inkflow.upload.application.UploadPresignProperties
import org.springframework.stereotype.Component
import org.springframework.web.util.UriUtils
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

/**
 * 로컬 환경에서 presigned URL을 시뮬레이션하는 구현체.
 */
@Component
class LocalPresignedUrlProvider(
    private val properties: UploadPresignProperties
) : MultipartPresignedUrlProvider, PresignedDownloadUrlProvider {
    /**
     * 실제 S3 연동 전까지 사용할 presigned URL을 구성한다.
     */
    override fun createPresignedMultipart(
        bucket: String,
        key: String,
        totalParts: Int,
        expiresAt: Instant
    ): MultipartPresignedUpload {
        val multipartUploadId = generateMultipartUploadId()
        val baseUrl = properties.baseUrl.trimEnd('/')
        val encodedKey = UriUtils.encodePath(key, StandardCharsets.UTF_8)
        val objectPath = encodedKey.trimStart('/')

        val urls = (1..totalParts).map { partNumber ->
            val url = buildPresignedUrl(
                baseUrl = baseUrl,
                bucket = bucket,
                objectPath = objectPath,
                partNumber = partNumber,
                uploadId = multipartUploadId,
                expiresAt = expiresAt
            )
            // 업로드 가속 옵션을 활성화한 경우 멀티 리전/ CDN URL을 추가로 제공한다.
            val accelerationUrls = buildAccelerationUrls(
                bucket = bucket,
                objectPath = objectPath,
                partNumber = partNumber,
                uploadId = multipartUploadId,
                expiresAt = expiresAt
            )
            PresignedPartUrl(
                partNumber = partNumber,
                url = url,
                accelerationUrls = accelerationUrls
            )
        }

        return MultipartPresignedUpload(
            multipartUploadId = multipartUploadId,
            presignedUrls = urls
        )
    }

    /**
     * 로컬 환경에서 다운로드용 presigned URL을 구성한다.
     */
    override fun createPresignedDownload(
        bucket: String,
        key: String,
        contentType: String,
        expiresAt: Instant
    ): PresignedDownloadUrl {
        val baseUrl = properties.baseUrl.trimEnd('/')
        val encodedKey = UriUtils.encodePath(key, StandardCharsets.UTF_8)
        val encodedContentType = UriUtils.encodeQueryParam(contentType, StandardCharsets.UTF_8)
        val objectPath = encodedKey.trimStart('/')
        val url = "$baseUrl/$bucket/$objectPath?expiresAt=${expiresAt.toEpochMilli()}" +
            "&responseContentType=$encodedContentType"

        return PresignedDownloadUrl(url = url)
    }

    /**
     * 멀티파트 업로드 식별자를 생성한다.
     */
    private fun generateMultipartUploadId(): String {
        return "mpu_${UUID.randomUUID()}"
    }

    /**
     * 공통 presigned URL 규칙을 생성한다.
     */
    private fun buildPresignedUrl(
        baseUrl: String,
        bucket: String,
        objectPath: String,
        partNumber: Int,
        uploadId: String,
        expiresAt: Instant
    ): String {
        return "$baseUrl/$bucket/$objectPath?partNumber=$partNumber" +
            "&uploadId=$uploadId&expiresAt=${expiresAt.toEpochMilli()}"
    }

    /**
     * 멀티 리전 및 CDN 가속 URL을 구성한다.
     */
    private fun buildAccelerationUrls(
        bucket: String,
        objectPath: String,
        partNumber: Int,
        uploadId: String,
        expiresAt: Instant
    ): List<AcceleratedPresignedUrl> {
        if (!properties.acceleration.enabled) {
            return emptyList()
        }

        val urls = mutableListOf<AcceleratedPresignedUrl>()
        for (region in properties.acceleration.regions) {
            if (region.region.isBlank() || region.baseUrl.isBlank()) {
                continue
            }
            val regionUrl = buildPresignedUrl(
                baseUrl = region.baseUrl.trimEnd('/'),
                bucket = bucket,
                objectPath = objectPath,
                partNumber = partNumber,
                uploadId = uploadId,
                expiresAt = expiresAt
            )
            urls.add(
                AcceleratedPresignedUrl(
                    mode = UploadAccelerationMode.REGION,
                    url = regionUrl,
                    region = region.region
                )
            )
        }

        val cdn = properties.acceleration.cdn
        if (cdn.enabled && cdn.baseUrl.isNotBlank()) {
            val cdnUrl = buildPresignedUrl(
                baseUrl = cdn.baseUrl.trimEnd('/'),
                bucket = bucket,
                objectPath = objectPath,
                partNumber = partNumber,
                uploadId = uploadId,
                expiresAt = expiresAt
            )
            urls.add(
                AcceleratedPresignedUrl(
                    mode = UploadAccelerationMode.CDN,
                    url = cdnUrl
                )
            )
        }

        return urls
    }
}
