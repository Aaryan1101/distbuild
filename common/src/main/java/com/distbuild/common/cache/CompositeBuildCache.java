package com.distbuild.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Composite cache that uses local disk as L1 cache and Redis as L2 cache
 */
public class CompositeBuildCache implements BuildCache {
    private static final Logger logger = LoggerFactory.getLogger(CompositeBuildCache.class);
    
    private final BuildCache localCache;
    private final BuildCache remoteCache;
    private final CacheStats stats;
    private final ExecutorService asyncExecutor;

    public CompositeBuildCache(BuildCache localCache, BuildCache remoteCache) {
        this.localCache = Objects.requireNonNull(localCache);
        this.remoteCache = Objects.requireNonNull(remoteCache);
        this.stats = new CacheStats();
        this.asyncExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "CompositeCache-Async");
            t.setDaemon(true);
            return t;
        });
        
        logger.info("CompositeBuildCache initialized with L1={} and L2={}", 
                   this.localCache.getClass().getSimpleName(), 
                   this.remoteCache.getClass().getSimpleName());
    }

    @Override
    public void put(String key, CompileResult result) throws CacheException {
        try {
            // Always store in both caches
            localCache.put(key, result);
            remoteCache.put(key, result);
            
            stats.recordPut();
            logger.debug("Stored result in both caches for key: {}", key);
            
        } catch (CacheException e) {
            stats.recordError();
            logger.warn("Failed to store in composite cache for key: {}", key, e);
            throw e;
        }
    }

    @Override
    public Optional<CompileResult> get(String key) throws CacheException {
        try {
            // Try local cache first (L1)
            Optional<CompileResult> result = localCache.get(key);
            if (result.isPresent()) {
                stats.recordHit();
                logger.debug("L1 cache hit for key: {}", key);
                return result;
            }
            
            // Try remote cache (L2)
            Optional<CompileResult> remoteResult = remoteCache.get(key);
            if (remoteResult.isPresent()) {
                stats.recordHit();
                
                // Async populate local cache
                asyncExecutor.submit(() -> {
                    try {
                        this.localCache.put(key, remoteResult.get());
                        logger.debug("Populated L1 cache from L2 for key: {}", key);
                    } catch (Exception e) {
                        logger.warn("Failed to populate L1 cache for key: {}", key, e);
                    }
                });
                
                logger.debug("L2 cache hit for key: {}", key);
                return remoteResult;
            }
            
            stats.recordMiss();
            logger.debug("Cache miss for key: {}", key);
            return Optional.empty();
            
        } catch (CacheException e) {
            stats.recordError();
            throw e;
        }
    }

    @Override
    public boolean contains(String key) throws CacheException {
        try {
            // Check local cache first
            if (localCache.contains(key)) {
                return true;
            }
            
            // Check remote cache
            return remoteCache.contains(key);
            
        } catch (CacheException e) {
            stats.recordError();
            throw e;
        }
    }

    @Override
    public void remove(String key) throws CacheException {
        try {
            localCache.remove(key);
            remoteCache.remove(key);
            logger.debug("Removed from both caches for key: {}", key);
            
        } catch (CacheException e) {
            stats.recordError();
            throw e;
        }
    }

    @Override
    public void clear() throws CacheException {
        try {
            localCache.clear();
            remoteCache.clear();
            logger.info("Cleared both caches");
            
        } catch (CacheException e) {
            stats.recordError();
            throw e;
        }
    }

    @Override
    public CacheStats getStats() {
        return stats;
    }

    @Override
    public void close() throws CacheException {
        try {
            localCache.close();
            remoteCache.close();
            asyncExecutor.shutdown();
            logger.info("CompositeBuildCache closed");
            
        } catch (Exception e) {
            throw new CacheException("Failed to close composite cache", e);
        }
    }

    /**
     * Gets detailed statistics from both cache layers
     */
    public CompositeCacheStats getDetailedStats() throws CacheException {
        CacheStats localStats = localCache.getStats();
        CacheStats remoteStats = remoteCache.getStats();
        
        return new CompositeCacheStats(stats, localStats, remoteStats);
    }

    /**
     * Warms up the local cache with frequently accessed items from remote cache
     */
    public CompletableFuture<Void> warmupCache() {
        return CompletableFuture.runAsync(() -> {
            logger.info("Starting cache warmup");
            // This is a placeholder for a more sophisticated warmup strategy
            // In a real implementation, you might track access patterns
            // and pre-load hot keys from remote to local cache
            logger.info("Cache warmup completed");
        }, asyncExecutor);
    }

    /**
     * Detailed statistics for composite cache
     */
    public static class CompositeCacheStats {
        private final CacheStats compositeStats;
        private final CacheStats localStats;
        private final CacheStats remoteStats;

        public CompositeCacheStats(CacheStats compositeStats, CacheStats localStats, CacheStats remoteStats) {
            this.compositeStats = compositeStats;
            this.localStats = localStats;
            this.remoteStats = remoteStats;
        }

        public CacheStats getCompositeStats() { return compositeStats; }
        public CacheStats getLocalStats() { return localStats; }
        public CacheStats getRemoteStats() { return remoteStats; }

        @Override
        public String toString() {
            return "CompositeCacheStats{" +
                    "composite=" + compositeStats +
                    ", local=" + localStats +
                    ", remote=" + remoteStats +
                    '}';
        }
    }
}
