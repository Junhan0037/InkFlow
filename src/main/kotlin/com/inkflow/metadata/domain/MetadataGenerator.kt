package com.inkflow.metadata.domain

/**
 * LLM 기반 메타 자동 생성 인터페이스.
 */
interface MetadataGenerator {
    /**
     * 에피소드 원천 정보를 바탕으로 요약/태그를 생성한다.
     */
    fun generate(source: EpisodeMetadataSource): GeneratedMetadata
}
