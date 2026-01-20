package com.inkflow.publish.domain

/**
 * 퍼블리시 스냅샷 저장소 계약.
 */
interface PublishSnapshotRepository {
    /**
     * 퍼블리시 스냅샷을 저장한다.
     */
    fun save(snapshot: PublishSnapshot): PublishSnapshot

    /**
     * 스냅샷 ID로 퍼블리시 스냅샷을 조회한다.
     */
    fun findBySnapshotId(snapshotId: String): PublishSnapshot?
}
