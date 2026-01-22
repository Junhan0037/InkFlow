package com.inkflow.publish.infra.config

import com.inkflow.publish.domain.PublishPolicy
import com.inkflow.publish.domain.PublishPolicyRepository
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 퍼블리시 정책 관련 빈을 구성.
 */
@Configuration
@EnableConfigurationProperties(PublishPolicyProperties::class)
class PublishPolicyConfig {
    /**
     * 설정 기반 퍼블리시 정책 저장소를 등록한다.
     */
    @Bean
    fun publishPolicyRepository(properties: PublishPolicyProperties): PublishPolicyRepository {
        return InMemoryPublishPolicyRepository(properties)
    }
}

/**
 * 메모리 기반 퍼블리시 정책 저장소 구현체.
 */
private class InMemoryPublishPolicyRepository(
    private val properties: PublishPolicyProperties
) : PublishPolicyRepository {
    private val policyMap: Map<String, PublishPolicy> = properties.rules
        .map { it.toDomain() }
        // 동일 키가 존재하면 마지막 규칙으로 덮어쓴다.
        .associateBy { buildKey(it.region, it.language) }

    /**
     * 지역/언어에 해당하는 정책을 조회한다.
     */
    override fun findPolicy(region: String, language: String): PublishPolicy? {
        val normalizedRegion = region.trim().uppercase()
        val normalizedLanguage = language.trim().lowercase()
        val key = buildKey(normalizedRegion, normalizedLanguage)
        return policyMap[key] ?: PublishPolicy.defaultPolicy(
            region = normalizedRegion,
            language = normalizedLanguage,
            status = properties.defaultStatus
        )
    }

    /**
     * 정책 조회 키를 생성한다.
     */
    private fun buildKey(region: String, language: String): String {
        return "$region:$language"
    }
}
