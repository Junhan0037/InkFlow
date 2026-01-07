package com.inkflow.common.observability

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags

/**
 * 메트릭 공통 태그 제공 규약을 정의한다.
 */
interface CommonTagsProvider {
    /**
     * 공통 태그 목록을 반환한다.
     */
    fun commonTags(): Iterable<Tag>
}

/**
 * 고정 태그 기반의 공통 태그 제공자다.
 */
data class StaticCommonTagsProvider(
    val service: String,
    val environment: String,
    val instanceId: String? = null,
    val extraTags: Map<String, String> = emptyMap()
) : CommonTagsProvider {
    /**
     * 구성된 값을 기반으로 공통 태그를 생성한다.
     */
    override fun commonTags(): Iterable<Tag> {
        val tags = Tags.of(
            Tag.of(MetricTagKeys.SERVICE, service),
            Tag.of(MetricTagKeys.ENVIRONMENT, environment)
        )

        val withInstance = if (!instanceId.isNullOrBlank()) {
            tags.and(Tag.of(MetricTagKeys.INSTANCE_ID, instanceId))
        } else {
            tags
        }

        // 사용자 지정 태그는 빈 값을 제외하고 추가한다.
        val filteredExtras = extraTags
            .filterKeys { it.isNotBlank() }
            .filterValues { it.isNotBlank() }
            .map { Tag.of(it.key, it.value) }

        return withInstance.and(filteredExtras)
    }
}

/**
 * MeterRegistry에 공통 태그를 적용한다.
 */
object CommonTagsApplier {
    /**
     * MeterRegistry에 공통 태그를 설정한다.
     */
    fun apply(registry: MeterRegistry, provider: CommonTagsProvider) {
        registry.config().commonTags(provider.commonTags())
    }
}
