package com.inkflow.indexing.infra.jpa

import org.springframework.data.jpa.repository.JpaRepository

/**
 * Work 색인 원천 데이터를 조회하기 위한 JPA Repository.
 */
interface WorkIndexJpaRepository : JpaRepository<WorkIndexEntity, Long>
