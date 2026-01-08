package com.inkflow.common.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * RequestContextFactory를 스프링 빈으로 등록한다.
 */
@Configuration
class RequestContextConfig {
    /**
     * 요청 컨텍스트 생성을 위한 기본 팩토리 빈.
     */
    @Bean
    fun requestContextFactory(): RequestContextFactory {
        return RequestContextFactory()
    }
}
