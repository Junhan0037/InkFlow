package com.inkflow.publish.domain

/**
 * 퍼블리시 정책 조회를 위한 저장소 계약.
 */
interface PublishPolicyRepository {
    /**
     * 지역/언어에 맞는 퍼블리시 정책을 조회한다.
     */
    fun findPolicy(region: String, language: String): PublishPolicy?
}
