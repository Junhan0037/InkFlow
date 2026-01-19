package com.inkflow.indexing.application

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Indexing 모듈 설정 프로퍼티를 활성화한다.
 */
@Configuration
@EnableConfigurationProperties(IndexingProperties::class)
class IndexingConfig
