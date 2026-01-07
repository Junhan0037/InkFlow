package com.inkflow.common.observability

/**
 * 메트릭 공통 태그 키를 정의한다.
 */
object MetricTagKeys {
    /**
     * 서비스 이름 태그 키를 정의한다.
     */
    const val SERVICE = "service"

    /**
     * 실행 환경 태그 키를 정의한다.
     */
    const val ENVIRONMENT = "env"

    /**
     * 인스턴스 식별자 태그 키를 정의한다.
     */
    const val INSTANCE_ID = "instanceId"
}
