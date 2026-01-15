package com.inkflow.media.infra.storage

import com.inkflow.common.error.ErrorCode
import com.inkflow.common.error.SystemException
import com.inkflow.media.application.MediaStorageClient
import com.inkflow.media.application.MediaStorageLocation
import com.inkflow.media.application.MediaStorageObject
import io.minio.GetObjectArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream

/**
 * MinIO SDK를 사용하는 Media 스토리지 구현체.
 */
class MinioMediaStorageClient(
    private val minioClient: MinioClient
) : MediaStorageClient {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * MinIO에서 객체를 다운로드한다.
     */
    override fun download(location: MediaStorageLocation): MediaStorageObject {
        return try {
            minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(location.bucket)
                    .`object`(location.key)
                    .build()
            ).use { input ->
                // 스트림을 메모리로 읽어 Media 처리 파이프라인에 전달한다.
                val bytes = input.readAllBytes()
                MediaStorageObject(contentType = null, bytes = bytes)
            }
        } catch (exception: Exception) {
            logger.warn(
                "Media 스토리지 다운로드 실패. bucket={}, key={}",
                location.bucket,
                location.key,
                exception
            )
            throw SystemException(
                errorCode = ErrorCode.DEPENDENCY_FAILURE,
                details = mapOf("bucket" to location.bucket, "key" to location.key),
                message = "Media 스토리지에서 객체 다운로드에 실패했습니다.",
                cause = exception
            )
        }
    }

    /**
     * MinIO에 객체를 업로드한다.
     */
    override fun upload(location: MediaStorageLocation, contentType: String, bytes: ByteArray) {
        try {
            ByteArrayInputStream(bytes).use { inputStream ->
                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(location.bucket)
                        .`object`(location.key)
                        .stream(inputStream, bytes.size.toLong(), -1)
                        .contentType(contentType)
                        .build()
                )
            }
        } catch (exception: Exception) {
            logger.warn(
                "Media 스토리지 업로드 실패. bucket={}, key={}",
                location.bucket,
                location.key,
                exception
            )
            throw SystemException(
                errorCode = ErrorCode.DEPENDENCY_FAILURE,
                details = mapOf("bucket" to location.bucket, "key" to location.key),
                message = "Media 스토리지에 객체 업로드에 실패했습니다.",
                cause = exception
            )
        }
    }
}
