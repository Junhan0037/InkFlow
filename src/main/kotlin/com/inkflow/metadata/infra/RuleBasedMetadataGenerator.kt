package com.inkflow.metadata.infra

import com.inkflow.metadata.domain.EpisodeMetadataSource
import com.inkflow.metadata.domain.GeneratedMetadata
import com.inkflow.metadata.domain.MetadataGenerator
import com.inkflow.metadata.domain.MetadataTagNormalizer
import org.springframework.stereotype.Component

/**
 * 실제 LLM 연동 전까지 사용하는 룰 기반 메타 생성기.
 */
@Component
class RuleBasedMetadataGenerator : MetadataGenerator {
    companion object {
        private const val GENERATOR_NAME = "rule-based-v1" // 메타 생성기 식별자.
    }

    /**
     * 에피소드 제목과 Work 정보를 조합해 요약/태그를 생성한다.
     */
    override fun generate(source: EpisodeMetadataSource): GeneratedMetadata {
        val summary = buildSummary(source)
        val tags = buildTags(source)
        return GeneratedMetadata(
            summary = summary,
            tags = tags,
            generator = GENERATOR_NAME
        )
    }

    /**
     * 요약 문장을 생성한다.
     */
    private fun buildSummary(source: EpisodeMetadataSource): String {
        // 기본 언어를 반영해 간단한 문장으로 요약한다.
        return "${source.workTitle}의 ${source.episodeTitle} 에피소드 요약입니다."
    }

    /**
     * 태그 후보를 생성하고 정규화한다.
     */
    private fun buildTags(source: EpisodeMetadataSource): List<String> {
        val candidates = mutableListOf<String>()
        candidates.addAll(extractTokens(source.workTitle))
        candidates.addAll(extractTokens(source.episodeTitle))
        candidates.add(source.defaultLanguage)
        candidates.add(source.creatorId)
        return MetadataTagNormalizer.normalize(candidates)
    }

    /**
     * 제목 문자열에서 토큰을 추출한다.
     */
    private fun extractTokens(text: String): List<String> {
        return text.split(Regex("[\\s\\p{Punct}]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
}
