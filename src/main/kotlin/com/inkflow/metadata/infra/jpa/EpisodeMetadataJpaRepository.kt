package com.inkflow.metadata.infra.jpa

import org.springframework.data.jpa.repository.JpaRepository

/**
 * 승인된 에피소드 메타데이터 JPA Repository.
 */
interface EpisodeMetadataJpaRepository : JpaRepository<EpisodeMetadataEntity, Long>
