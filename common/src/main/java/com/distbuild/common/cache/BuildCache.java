package com.distbuild.common.cache;

import java.util.Optional;

/**
 * Interface for build result caching
 */
public interface BuildCache {
    
    /**
     * Stores a compilation result in the cache
     */
    void put(String key, CompileResult result) throws CacheException;
    
    /**
     * Retrieves a compilation result from the cache
     */
    Optional<CompileResult> get(String key) throws CacheException;
    
    /**
     * Checks if a result exists in the cache
     */
    boolean contains(String key) throws CacheException;
    
    /**
     * Removes a result from the cache
     */
    void remove(String key) throws CacheException;
    
    /**
     * Clears all entries from the cache
     */
    void clear() throws CacheException;
    
    /**
     * Gets cache statistics
     */
    CacheStats getStats();
    
    /**
     * Closes the cache and releases resources
     */
    void close() throws CacheException;
}
