package com.inkflow.publish.domain

/**
 * 퍼블리시 버전의 상태를 정의.
 */
enum class PublishVersionStatus {
    ACTIVE, // 현재 서비스에 노출되는 활성 버전이다.
    SUPERSEDED, // 최신 스냅샷으로 대체된 버전이다.
    ROLLED_BACK // 롤백으로 인해 비활성화된 버전이다.
}
