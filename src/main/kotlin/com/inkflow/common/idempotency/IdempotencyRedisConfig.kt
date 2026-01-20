package com.inkflow.common.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

/**
 * Idempotency 키 Redis 저장소에 필요한 Bean 구성을 제공.
 */
@Configuration
@EnableConfigurationProperties(IdempotencyRedisProperties::class)
class IdempotencyRedisConfig {
    /**
     * Idempotency 전용 RedisTemplate을 구성한다.
     */
    @Bean
    fun idempotencyRedisTemplate(
        redisConnectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper
    ): RedisTemplate<String, IdempotencyRecord> {
        val keySerializer = StringRedisSerializer()
        val valueSerializer = Jackson2JsonRedisSerializer(objectMapper, IdempotencyRecord::class.java)

        return RedisTemplate<String, IdempotencyRecord>().apply {
            // 키/값 직렬화를 명시하여 JSON 기반 저장을 보장한다.
            setKeySerializer(keySerializer)
            setValueSerializer(valueSerializer)
            setHashKeySerializer(keySerializer)
            setHashValueSerializer(valueSerializer)
            connectionFactory = redisConnectionFactory
            afterPropertiesSet()
        }
    }
}
