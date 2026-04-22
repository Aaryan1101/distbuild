package com.distbuild.common.cache;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Statistics for cache operations
 */
public class CacheStats {
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong puts = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);
    private final AtomicLong errors = new AtomicLong(0);
    private final Instant startTime;

    public CacheStats() {
        this.startTime = Instant.now();
    }

    public void recordHit() {
        hits.incrementAndGet();
    }

    public void recordMiss() {
        misses.incrementAndGet();
    }

    public void recordPut() {
        puts.incrementAndGet();
    }

    public void recordEviction() {
        evictions.incrementAndGet();
    }

    public void recordError() {
        errors.incrementAndGet();
    }

    public long getHits() {
        return hits.get();
    }

    public long getMisses() {
        return misses.get();
    }

    public long getPuts() {
        return puts.get();
    }

    public long getEvictions() {
        return evictions.get();
    }

    public long getErrors() {
        return errors.get();
    }

    public long getTotalRequests() {
        return hits.get() + misses.get();
    }

    public double getHitRate() {
        long total = getTotalRequests();
        return total == 0 ? 0.0 : (double) hits.get() / total;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void reset() {
        hits.set(0);
        misses.set(0);
        puts.set(0);
        evictions.set(0);
        errors.set(0);
    }

    @Override
    public String toString() {
        return "CacheStats{" +
                "hits=" + hits.get() +
                ", misses=" + misses.get() +
                ", puts=" + puts.get() +
                ", evictions=" + evictions.get() +
                ", errors=" + errors.get() +
                ", hitRate=" + String.format("%.2f%%", getHitRate() * 100) +
                ", uptime=" + java.time.Duration.between(startTime, Instant.now()).getSeconds() + "s" +
                '}';
    }
}
