package com.inkflow.observability

import com.inkflow.common.observability.CommonTagsApplier
import com.inkflow.common.observability.StaticCommonTagsProvider
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 서비스 관측성의 공통 설정을 구성.
 */
@Configuration
@EnableConfigurationProperties(ObservabilityProperties::class)
class ObservabilityConfig {
    /**
     * Prometheus/OTel 메트릭에 공통 태그를 적용한다.
     */
    @Bean
    fun meterRegistryCustomizer(
        properties: ObservabilityProperties
    ): MeterRegistryCustomizer<MeterRegistry> {
        return MeterRegistryCustomizer { registry ->
            val tagsProvider = StaticCommonTagsProvider(
                service = properties.service,
                environment = properties.environment,
                instanceId = properties.instanceId,
                extraTags = properties.extraTags
            )

            // 대시보드/알림에서 서비스 단위를 안정적으로 필터링하기 위한 공통 태그 적용이다.
            CommonTagsApplier.apply(registry, tagsProvider)
        }
    }
}
