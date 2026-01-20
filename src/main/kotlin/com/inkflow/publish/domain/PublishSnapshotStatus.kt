package com.inkflow.publish.domain

/**
 * 퍼블리시 스냅샷 상태를 정의.
 */
enum class PublishSnapshotStatus {
    ACTIVE, // 현재 서비스에 적용된 스냅샷이다.
    SUPERSEDED, // 최신 스냅샷으로 대체된 상태다.
    ROLLED_BACK // 롤백으로 인해 비활성화된 상태다.
}
