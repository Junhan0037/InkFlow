package com.inkflow.metadata.infra.jdbc

import com.inkflow.metadata.domain.EpisodeMetadataSource
import com.inkflow.metadata.domain.EpisodeMetadataSourceRepository
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

/**
 * JDBC 기반 메타 생성 원천 정보 조회 구현체.
 */
@Repository
class EpisodeMetadataSourceJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) : EpisodeMetadataSourceRepository {
    companion object {
        private const val SOURCE_QUERY = """
            SELECT
                e.id AS episode_id,
                e.work_id AS work_id,
                e.title AS episode_title,
                w.title AS work_title,
                w.default_language AS default_language,
                w.creator_id AS creator_id
            FROM episode e
            JOIN work w ON w.id = e.work_id
            WHERE e.id = :episodeId
        """
    }

    private val rowMapper = RowMapper<EpisodeMetadataSource> { rs, _ ->
        EpisodeMetadataSource(
            episodeId = rs.getLong("episode_id"),
            workId = rs.getLong("work_id"),
            episodeTitle = rs.getString("episode_title"),
            workTitle = rs.getString("work_title"),
            defaultLanguage = rs.getString("default_language"),
            creatorId = rs.getString("creator_id")
        )
    }

    /**
     * 에피소드 ID로 메타 생성 원천 정보를 조회한다.
     */
    override fun findSourceByEpisodeId(episodeId: Long): EpisodeMetadataSource? {
        val params = MapSqlParameterSource("episodeId", episodeId)
        return jdbcTemplate.query(SOURCE_QUERY, params, rowMapper).firstOrNull()
    }
}
