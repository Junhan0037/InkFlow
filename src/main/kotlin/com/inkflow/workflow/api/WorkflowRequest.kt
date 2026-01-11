package com.inkflow.workflow.api

import java.time.Instant

/**
 * 에피소드 제출 요청 DTO.
 */
data class SubmitEpisodeRequest(
    val deadline: Instant
)

/**
 * 에피소드 승인 요청 DTO.
 */
data class ApproveEpisodeRequest(
    val reviewerId: String? = null,
    val comment: String? = null
)

/**
 * 에피소드 반려 요청 DTO.
 */
data class RejectEpisodeRequest(
    val reviewerId: String? = null,
    val reason: String
)
