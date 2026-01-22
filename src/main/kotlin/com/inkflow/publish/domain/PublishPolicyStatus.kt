package com.inkflow.publish.domain

/**
 * 퍼블리시 지역/언어 정책의 상태를 정의.
 */
enum class PublishPolicyStatus {
    ACTIVE, // 정책이 활성화된 상태다.
    LOCKED, // 운영 정책으로 잠긴 상태다.
    DISABLED // 노출 대상에서 제외된 상태다.
}
