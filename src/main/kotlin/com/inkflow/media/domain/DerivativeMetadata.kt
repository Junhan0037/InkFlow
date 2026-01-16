package com.inkflow.media.domain

import java.time.Instant

/**
 * 파생 리소스 메타데이터.
 */
data class DerivativeMetadata(
    val id: Long? = null,
    val assetId: Long,
    val type: DerivativeType,
    val width: Int?,
    val height: Int?,
    val format: String,
    val storageKey: String,
    val status: DerivativeStatus,
    val createdAt: Instant
) {
    init {
        require(assetId > 0) { "assetId는 0보다 커야 합니다." }
        width?.let { require(it > 0) { "width는 0보다 커야 합니다." } }
        height?.let { require(it > 0) { "height는 0보다 커야 합니다." } }
        require(format.isNotBlank()) { "format은 비어 있을 수 없습니다." }
        require(storageKey.isNotBlank()) { "storageKey는 비어 있을 수 없습니다." }
    }

    companion object {
        /**
         * 준비 완료 상태의 파생 리소스 메타데이터를 생성한다.
         */
        fun createReady(
            assetId: Long,
            type: DerivativeType,
            width: Int?,
            height: Int?,
            format: String,
            storageKey: String,
            now: Instant = Instant.now()
        ): DerivativeMetadata {
            return DerivativeMetadata(
                assetId = assetId,
                type = type,
                width = width,
                height = height,
                format = format,
                storageKey = storageKey,
                status = DerivativeStatus.READY,
                createdAt = now
            )
        }
    }
}
