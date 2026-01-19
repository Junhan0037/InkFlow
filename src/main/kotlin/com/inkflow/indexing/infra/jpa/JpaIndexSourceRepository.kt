package com.inkflow.indexing.infra.jpa

import com.inkflow.indexing.domain.AssetIndexSource
import com.inkflow.indexing.domain.EpisodeIndexSource
import com.inkflow.indexing.domain.IndexSourceRepository
import com.inkflow.indexing.domain.WorkIndexSource
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

/**
 * JPA 기반 색인 원천 데이터 조회 저장소 구현체.
 */
@Repository
class JpaIndexSourceRepository(
    private val workIndexJpaRepository: WorkIndexJpaRepository,
    private val episodeIndexJpaRepository: EpisodeIndexJpaRepository,
    private val assetIndexJpaRepository: AssetIndexJpaRepository
) : IndexSourceRepository {
    /**
     * Work 원천 데이터를 조회한다.
     */
    override fun findWork(workId: Long): WorkIndexSource? {
        return workIndexJpaRepository.findByIdOrNull(workId)?.toDomain()
    }

    /**
     * Episode 원천 데이터를 조회한다.
     */
    override fun findEpisode(episodeId: Long): EpisodeIndexSource? {
        return episodeIndexJpaRepository.findByIdOrNull(episodeId)?.toDomain()
    }

    /**
     * Asset 원천 데이터를 조회한다.
     */
    override fun findAsset(assetId: Long): AssetIndexSource? {
        return assetIndexJpaRepository.findByIdOrNull(assetId)?.toDomain()
    }
}
