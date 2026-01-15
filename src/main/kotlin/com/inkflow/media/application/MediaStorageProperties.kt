package com.inkflow.media.application

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Media 워커가 사용할 오브젝트 스토리지 설정 값을 정의한다.
 */
@ConfigurationProperties("inkflow.media.storage")
data class MediaStorageProperties(
    val endpoint: String = "http://localhost:9000",
    val accessKey: String = "inkflow",
    val secretKey: String = "inkflow_pw",
    val region: String? = null
) {
    init {
        require(endpoint.isNotBlank()) { "endpoint는 비어 있을 수 없습니다." }
        require(accessKey.isNotBlank()) { "accessKey는 비어 있을 수 없습니다." }
        require(secretKey.isNotBlank()) { "secretKey는 비어 있을 수 없습니다." }
    }
}
