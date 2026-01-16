package com.inkflow.media.infra.jpa

import org.springframework.data.jpa.repository.JpaRepository

/**
 * Derivative 메타데이터를 조회하기 위한 JPA Repository.
 */
interface DerivativeMetadataJpaRepository : JpaRepository<DerivativeMetadataEntity, Long>
