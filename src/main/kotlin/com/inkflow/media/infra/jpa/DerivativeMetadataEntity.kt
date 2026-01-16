package com.inkflow.media.infra.jpa

import com.inkflow.media.domain.DerivativeMetadata
import com.inkflow.media.domain.DerivativeStatus
import com.inkflow.media.domain.DerivativeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Derivative 메타데이터를 JPA로 영속화하기 위한 엔티티.
 */
@Entity
@Table(name = "derivative")
class DerivativeMetadataEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null,

    @Column(name = "asset_id", nullable = false)
    var assetId: Long = 0L,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    var type: DerivativeType = DerivativeType.THUMBNAIL,

    @Column(name = "width")
    var width: Int? = null,

    @Column(name = "height")
    var height: Int? = null,

    @Column(name = "format", nullable = false)
    var format: String = "",

    @Column(name = "storage_key", nullable = false)
    var storageKey: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: DerivativeStatus = DerivativeStatus.READY,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH
) {
    /**
     * 엔티티를 도메인 모델로 변환한다.
     */
    fun toDomain(): DerivativeMetadata {
        return DerivativeMetadata(
            id = id,
            assetId = assetId,
            type = type,
            width = width,
            height = height,
            format = format,
            storageKey = storageKey,
            status = status,
            createdAt = createdAt
        )
    }

    companion object {
        /**
         * 도메인 모델을 엔티티로 변환한다.
         */
        fun fromDomain(derivative: DerivativeMetadata): DerivativeMetadataEntity {
            return DerivativeMetadataEntity(
                id = derivative.id,
                assetId = derivative.assetId,
                type = derivative.type,
                width = derivative.width,
                height = derivative.height,
                format = derivative.format,
                storageKey = derivative.storageKey,
                status = derivative.status,
                createdAt = derivative.createdAt
            )
        }
    }
}
