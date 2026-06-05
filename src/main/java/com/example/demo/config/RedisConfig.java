package com.example.demo.config;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * TICKET LF-202 — Redis configuration with graceful degradation.
 *
 * Problem 1 — infinite connection timeout:
 *   Fixed in application.yml: spring.data.redis.timeout=2000ms and
 *   spring.data.redis.connect-timeout=2000ms. Lettuce connects lazily
 *   so the app starts fine; per-request operations time out quickly
 *   when Redis is down instead of hanging forever.
 *
 * Problem 2 — uncaught cache exceptions crash requests:
 *   Fixed by implementing CachingConfigurer.errorHandler() to return
 *   CustomCacheErrorHandler, which logs-and-swallows all cache exceptions.
 *   This means @Cacheable / @CacheEvict methods transparently fall back
 *   to the real implementation when the cache is unavailable.
 *
 * Active-worker tracking uses StringRedisTemplate with manual try-catch
 * (see AttendanceService) because it's a Hash operation, not @Cacheable.
 */
@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    /**
     * Register the custom error handler so Spring Cache infrastructure uses it
     * for every @Cacheable / @CachePut / @CacheEvict invocation.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CustomCacheErrorHandler();
    }
}
