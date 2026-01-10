package com.inkflow.upload.infra.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * Episode-Work 조인으로 creatorId를 조회하기 위한 JPA 리포지토리.
 */
interface EpisodeAccessJpaRepository : JpaRepository<EpisodeEntity, Long> {
    /**
     * 에피소드 소유자를 조회해 업로드 권한 검증에 사용한다.
     */
    @Query(
        """
        select w.creatorId
        from EpisodeEntity e
        join e.work w
        where e.id = :episodeId
        """
    )
    fun findCreatorIdByEpisodeId(@Param("episodeId") episodeId: Long): String?
}
