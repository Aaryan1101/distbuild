package com.distbuild.common.cache;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Mock Redis implementation for testing
 */
public class MockRedisCache implements BuildCache {
    private final ConcurrentHashMap<String, byte[]> data = new ConcurrentHashMap<>();
    private final CacheStats stats = new CacheStats();
    private final long ttlMillis;

    public MockRedisCache() {
        this(TimeUnit.HOURS.toMillis(24)); // 24 hour default TTL
    }

    public MockRedisCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    @Override
    public void put(String key, CompileResult result) throws CacheException {
        if (!result.isSuccess()) {
            return;
        }

        try {
            byte[] serialized = result.toJson();
            data.put(key, serialized);
            stats.recordPut();
        } catch (Exception e) {
            stats.recordError();
            throw new CacheException("Failed to put key: " + key, e);
        }
    }

    @Override
    public Optional<CompileResult> get(String key) throws CacheException {
        try {
            byte[] serialized = data.get(key);
            if (serialized == null) {
                stats.recordMiss();
                return Optional.empty();
            }

            CompileResult result = CompileResult.fromJson(serialized);
            stats.recordHit();
            return Optional.of(result);

        } catch (Exception e) {
            stats.recordError();
            throw new CacheException("Failed to get key: " + key, e);
        }
    }

    @Override
    public boolean contains(String key) throws CacheException {
        return data.containsKey(key);
    }

    @Override
    public void remove(String key) throws CacheException {
        data.remove(key);
    }

    @Override
    public void clear() throws CacheException {
        data.clear();
    }

    @Override
    public CacheStats getStats() {
        return stats;
    }

    @Override
    public void close() throws CacheException {
        data.clear();
    }
}
