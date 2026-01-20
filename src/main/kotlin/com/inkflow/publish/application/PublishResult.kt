package com.inkflow.publish.application

/**
 * 퍼블리시 스냅샷 생성 결과.
 */
data class CreateSnapshotResult(
    val snapshotId: String,
    val publishVersion: Long
)

/**
 * 퍼블리시 롤백 결과.
 */
data class RollbackSnapshotResult(
    val success: Boolean,
    val activeVersion: Long
)
