package com.inkflow.upload.infra.redis

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Clock

/**
 * 업로드 세션 Redis 저장소에 필요한 Bean 구성을 제공한다.
 */
@Configuration
@EnableConfigurationProperties(UploadSessionRedisProperties::class)
class UploadSessionRedisConfig {
    /**
     * 업로드 세션 캐시 전용 RedisTemplate을 구성한다.
     */
    @Bean
    fun uploadSessionRedisTemplate(
        redisConnectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper
    ): RedisTemplate<String, UploadSessionCache> {
        val keySerializer = StringRedisSerializer()
        val valueSerializer = Jackson2JsonRedisSerializer(objectMapper, UploadSessionCache::class.java)

        return RedisTemplate<String, UploadSessionCache>().apply {
            // 키/값 직렬화를 명시하여 JSON 기반 캐시 저장을 보장한다.
            setKeySerializer(keySerializer)
            setValueSerializer(valueSerializer)
            setHashKeySerializer(keySerializer)
            setHashValueSerializer(valueSerializer)
            connectionFactory = redisConnectionFactory
            afterPropertiesSet()
        }
    }

    /**
     * 업로드 세션 TTL 계산에 사용하는 시스템 시계를 제공한다.
     */
    @Bean
    fun uploadSessionClock(): Clock {
        return Clock.systemUTC()
    }
}
