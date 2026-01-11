package com.inkflow.workflow.application

import java.time.Instant

/**
 * 에피소드 제출 전이를 위한 커맨드.
 */
data class SubmitEpisodeCommand(
    val episodeId: Long,
    val submitterId: String,
    val deadline: Instant
)

/**
 * 에피소드 검수 시작 전이를 위한 커맨드.
 */
data class StartReviewCommand(
    val episodeId: Long,
    val reviewerId: String
)

/**
 * 에피소드 승인 전이를 위한 커맨드.
 */
data class ApproveEpisodeCommand(
    val episodeId: Long,
    val reviewerId: String,
    val comment: String?
)

/**
 * 에피소드 반려 전이를 위한 커맨드.
 */
data class RejectEpisodeCommand(
    val episodeId: Long,
    val reviewerId: String,
    val reason: String
)
