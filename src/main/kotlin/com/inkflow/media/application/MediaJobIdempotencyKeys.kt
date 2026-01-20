package com.inkflow.media.application

/**
 * Media 작업 멱등성 키 생성 규칙을 정의.
 */
object MediaJobIdempotencyKeys {
    /**
     * Asset 콘텐츠 해시 기반으로 Media 작업 키를 생성한다.
     * contentHash는 Asset 체크섬을 사용하고, 자산 경계 보장을 위해 assetId를 포함한다.
     */
    fun forDerivative(
        assetId: Long,
        contentHash: String,
        derivativeType: String,
        spec: MediaJobSpec
    ): String {
        val normalizedHash = contentHash.trim()
        val normalizedType = derivativeType.trim().uppercase()
        val normalizedFormat = spec.format.trim().lowercase()
        return "asset:$assetId:$normalizedHash:$normalizedType:${spec.width}x${spec.height}:$normalizedFormat"
    }
}
