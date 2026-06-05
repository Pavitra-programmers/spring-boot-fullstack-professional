package com.example.demo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * TICKET LF-202 — Graceful Redis cache failure handling.
 *
 * Root cause of the original crash: default Spring Cache propagates all
 * RedisConnectionFailureException / QueryTimeoutException as unchecked
 * exceptions, so any Redis outage caused the request to fail with 500.
 *
 * Fix: implement CacheErrorHandler to log-and-swallow all cache exceptions.
 * Effect:
 *  - Cache GET miss → method executes normally, fetches from database.
 *  - Cache PUT fail → data is returned without being cached; next request
 *    hits the database again until Redis recovers.
 *  - Cache EVICT/CLEAR fail → cache may hold stale data temporarily; logged
 *    so ops can detect a prolonged Redis outage.
 *
 * This is registered in RedisConfig.errorHandler().
 */
@Slf4j
public class CustomCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
        log.warn("[Cache] GET failed — cache='{}', key='{}'. Falling back to DB. Cause: {}",
                cache.getName(), key, ex.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
        log.warn("[Cache] PUT failed — cache='{}', key='{}'. Data not cached. Cause: {}",
                cache.getName(), key, ex.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
        log.warn("[Cache] EVICT failed — cache='{}', key='{}'. Cache may be stale. Cause: {}",
                cache.getName(), key, ex.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException ex, Cache cache) {
        log.warn("[Cache] CLEAR failed — cache='{}'. Cache may be stale. Cause: {}",
                cache.getName(), ex.getMessage());
    }
}
