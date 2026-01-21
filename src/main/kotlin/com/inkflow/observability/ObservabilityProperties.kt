package com.inkflow.observability

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 관측 파이프라인에서 사용할 공통 속성을 정의.
 */
@ConfigurationProperties(prefix = "inkflow.observability")
data class ObservabilityProperties(
    val service: String,
    val environment: String,
    val instanceId: String? = null,
    val extraTags: Map<String, String> = emptyMap()
)
