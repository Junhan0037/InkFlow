package com.inkflow.upload.infra.presign

import com.inkflow.upload.application.MultipartPresignedUpload
import com.inkflow.upload.application.MultipartPresignedUrlProvider
import com.inkflow.upload.application.PresignedDownloadUrl
import com.inkflow.upload.application.PresignedDownloadUrlProvider
import com.inkflow.upload.application.PresignedPartUrl
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
            val url = "$baseUrl/$bucket/$objectPath?partNumber=$partNumber" +
                "&uploadId=$multipartUploadId&expiresAt=${expiresAt.toEpochMilli()}"
            PresignedPartUrl(partNumber = partNumber, url = url)
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
}
