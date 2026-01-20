package com.inkflow.publish.infra.mongo

import org.springframework.data.mongodb.repository.MongoRepository

/**
 * 퍼블리시 스냅샷 Mongo 리포지토리.
 */
interface PublishSnapshotMongoRepository : MongoRepository<PublishSnapshotDocument, String> {
    /**
     * snapshotId로 조회한다.
     */
    fun findBySnapshotId(snapshotId: String): PublishSnapshotDocument?
}
