package com.distbuild.common.cache;

/**
 * Exception thrown for cache-related errors
 */
public class CacheException extends Exception {
    
    public CacheException(String message) {
        super(message);
    }

    public CacheException(String message, Throwable cause) {
        super(message, cause);
    }

    public CacheException(Throwable cause) {
        super(cause);
    }
}
