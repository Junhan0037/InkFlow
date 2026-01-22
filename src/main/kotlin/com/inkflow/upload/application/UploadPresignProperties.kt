package com.inkflow.upload.application

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Presigned URL 발급에 필요한 기본 설정을 관리한다.
 */
@ConfigurationProperties("inkflow.upload.presign")
data class UploadPresignProperties(
    val baseUrl: String = "http://localhost:9000",
    val acceleration: UploadAccelerationProperties = UploadAccelerationProperties()
)

/**
 * 업로드 가속(멀티 리전, CDN 시뮬레이션) 옵션을 관리한다.
 */
data class UploadAccelerationProperties(
    val enabled: Boolean = false,
    val regions: List<UploadRegionPresignProperties> = emptyList(),
    val cdn: UploadCdnPresignProperties = UploadCdnPresignProperties()
)

/**
 * 리전별 presigned URL 발급을 위한 기본 정보를 정의한다.
 */
data class UploadRegionPresignProperties(
    val region: String = "",
    val baseUrl: String = ""
)

/**
 * CDN 업로드 가속 시뮬레이션 정보를 정의한다.
 */
data class UploadCdnPresignProperties(
    val enabled: Boolean = false,
    val baseUrl: String = ""
)
