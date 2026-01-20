package com.inkflow.publish.infra.mongo

import com.inkflow.publish.domain.PublishSnapshot
import com.inkflow.publish.domain.PublishSnapshotRepository
import org.springframework.stereotype.Repository

/**
 * MongoDB 기반 퍼블리시 스냅샷 저장소 구현체.
 */
@Repository
class MongoPublishSnapshotRepository(
    private val publishSnapshotMongoRepository: PublishSnapshotMongoRepository
) : PublishSnapshotRepository {
    /**
     * 퍼블리시 스냅샷을 저장한다.
     */
    override fun save(snapshot: PublishSnapshot): PublishSnapshot {
        val document = PublishSnapshotDocument.fromDomain(snapshot)
        return publishSnapshotMongoRepository.save(document).toDomain()
    }

    /**
     * snapshotId로 퍼블리시 스냅샷을 조회한다.
     */
    override fun findBySnapshotId(snapshotId: String): PublishSnapshot? {
        return publishSnapshotMongoRepository.findBySnapshotId(snapshotId)?.toDomain()
    }
}
