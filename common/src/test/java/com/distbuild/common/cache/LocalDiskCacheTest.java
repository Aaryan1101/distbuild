package com.distbuild.common.cache;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.Assert.*;

public class LocalDiskCacheTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testPutAndGet() throws CacheException {
        Path cacheDir = tempFolder.getRoot().toPath().resolve("cache");
        LocalDiskCache cache = new LocalDiskCache(cacheDir);

        CompileResult result = CompileResult.success(
            Set.of("src/Main.java"),
            Set.of("build/classes/Main.class"),
            Map.of("build/classes/Main.class", "compiled class".getBytes()),
            1000
        );

        cache.put("test-key", result);
        Optional<CompileResult> retrieved = cache.get("test-key");

        assertTrue(retrieved.isPresent());
        CompileResult retrievedResult = retrieved.get();
        assertTrue(retrievedResult.isSuccess());
        assertEquals(Set.of("src/Main.java"), retrievedResult.getCompiledFiles());
        assertEquals(1000, retrievedResult.getCompileTimeMs());

        cache.close();
    }

    @Test
    public void testCacheMiss() throws CacheException {
        Path cacheDir = tempFolder.getRoot().toPath().resolve("cache");
        LocalDiskCache cache = new LocalDiskCache(cacheDir);

        Optional<CompileResult> result = cache.get("non-existent-key");
        assertFalse(result.isPresent());

        cache.close();
    }

    @Test
    public void testFailedCompilationNotCached() throws CacheException {
        Path cacheDir = tempFolder.getRoot().toPath().resolve("cache");
        LocalDiskCache cache = new LocalDiskCache(cacheDir);

        CompileResult result = CompileResult.failure("Compilation failed", 500);
        cache.put("failed-key", result);

        Optional<CompileResult> retrieved = cache.get("failed-key");
        assertFalse(retrieved.isPresent());

        cache.close();
    }

    @Test
    public void testContains() throws CacheException {
        Path cacheDir = tempFolder.getRoot().toPath().resolve("cache");
        LocalDiskCache cache = new LocalDiskCache(cacheDir);

        CompileResult result = CompileResult.success(
            Set.of("src/Main.java"),
            Set.of("build/classes/Main.class"),
            Map.of("build/classes/Main.class", "class data".getBytes()),
            1000
        );

        assertFalse(cache.contains("test-key"));
        cache.put("test-key", result);
        assertTrue(cache.contains("test-key"));

        cache.close();
    }

    @Test
    public void testRemove() throws CacheException {
        Path cacheDir = tempFolder.getRoot().toPath().resolve("cache");
        LocalDiskCache cache = new LocalDiskCache(cacheDir);

        CompileResult result = CompileResult.success(
            Set.of("src/Main.java"),
            Set.of("build/classes/Main.class"),
            Map.of("build/classes/Main.class", "class data".getBytes()),
            1000
        );

        cache.put("test-key", result);
        assertTrue(cache.contains("test-key"));

        cache.remove("test-key");
        assertFalse(cache.contains("test-key"));

        cache.close();
    }

    @Test
    public void testClear() throws CacheException {
        Path cacheDir = tempFolder.getRoot().toPath().resolve("cache");
        LocalDiskCache cache = new LocalDiskCache(cacheDir);

        CompileResult result = CompileResult.success(
            Set.of("src/Main.java"),
            Set.of("build/classes/Main.class"),
            Map.of("build/classes/Main.class", "class data".getBytes()),
            1000
        );

        cache.put("key1", result);
        cache.put("key2", result);

        assertEquals(2, cache.getStats().getPuts());
        cache.clear();
        assertEquals(0, cache.getStats().getHits()); // No hits after clear

        assertFalse(cache.contains("key1"));
        assertFalse(cache.contains("key2"));

        cache.close();
    }

    @Test
    public void testEviction() throws CacheException {
        Path cacheDir = tempFolder.getRoot().toPath().resolve("cache");
        // Small cache size to trigger eviction
        LocalDiskCache cache = new LocalDiskCache(cacheDir, 1000);

        CompileResult result = CompileResult.success(
            Set.of("src/Main.java"),
            Set.of("build/classes/Main.class"),
            Map.of("build/classes/Main.class", "large class data".getBytes()),
            1000
        );

        // Add multiple entries to trigger eviction
        for (int i = 0; i < 10; i++) {
            cache.put("key" + i, result);
        }

        // Some entries should have been evicted
        assertTrue(cache.getStats().getEvictions() > 0);

        cache.close();
    }

    @Test
    public void testPersistence() throws CacheException, IOException {
        Path cacheDir = tempFolder.getRoot().toPath().resolve("cache");
        
        // Create cache and add entry
        LocalDiskCache cache1 = new LocalDiskCache(cacheDir);
        CompileResult result = CompileResult.success(
            Set.of("src/Main.java"),
            Set.of("build/classes/Main.class"),
            Map.of("build/classes/Main.class", "persistent data".getBytes()),
            1000
        );
        cache1.put("persistent-key", result);
        cache1.close();

        // Create new cache instance and verify persistence
        LocalDiskCache cache2 = new LocalDiskCache(cacheDir);
        Optional<CompileResult> retrieved = cache2.get("persistent-key");
        
        assertTrue(retrieved.isPresent());
        assertTrue(retrieved.get().isSuccess());
        
        cache2.close();
    }

    @Test
    public void testStats() throws CacheException {
        Path cacheDir = tempFolder.getRoot().toPath().resolve("cache");
        LocalDiskCache cache = new LocalDiskCache(cacheDir);

        CompileResult result = CompileResult.success(
            Set.of("src/Main.java"),
            Set.of("build/classes/Main.class"),
            Map.of("build/classes/Main.class", "class data".getBytes()),
            1000
        );

        // Initially no stats
        assertEquals(0, cache.getStats().getHits());
        assertEquals(0, cache.getStats().getMisses());
        assertEquals(0, cache.getStats().getPuts());

        // Put operation
        cache.put("test-key", result);
        assertEquals(1, cache.getStats().getPuts());

        // Hit
        cache.get("test-key");
        assertEquals(1, cache.getStats().getHits());

        // Miss
        cache.get("missing-key");
        assertEquals(1, cache.getStats().getMisses());

        // Check hit rate
        assertEquals(0.5, cache.getStats().getHitRate(), 0.01);

        cache.close();
    }

    @Test
    public void testConcurrentAccess() throws CacheException, InterruptedException {
        Path cacheDir = tempFolder.getRoot().toPath().resolve("cache");
        LocalDiskCache cache = new LocalDiskCache(cacheDir);

        CompileResult result = CompileResult.success(
            Set.of("src/Main.java"),
            Set.of("build/classes/Main.class"),
            Map.of("build/classes/Main.class", "concurrent data".getBytes()),
            1000
        );

        int numThreads = 10;
        int operationsPerThread = 100;
        Thread[] threads = new Thread[numThreads];

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "thread-" + threadId + "-key-" + j;
                        cache.put(key, result);
                        cache.get(key);
                    }
                } catch (CacheException e) {
                    fail("Concurrent access failed: " + e.getMessage());
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify all operations completed
        assertEquals(numThreads * operationsPerThread, cache.getStats().getPuts());
        assertEquals(numThreads * operationsPerThread, cache.getStats().getHits());

        cache.close();
    }

    @Test
    public void testLargeData() throws CacheException {
        Path cacheDir = tempFolder.getRoot().toPath().resolve("cache");
        LocalDiskCache cache = new LocalDiskCache(cacheDir);

        // Create large class data
        byte[] largeData = new byte[1024 * 1024]; // 1MB
        Arrays.fill(largeData, (byte) 42);

        CompileResult result = CompileResult.success(
            Set.of("src/LargeClass.java"),
            Set.of("build/classes/LargeClass.class"),
            Map.of("build/classes/LargeClass.class", largeData),
            5000
        );

        cache.put("large-key", result);
        Optional<CompileResult> retrieved = cache.get("large-key");

        assertTrue(retrieved.isPresent());
        assertArrayEquals(largeData, retrieved.get().getClassFileContents().get("build/classes/LargeClass.class"));

        cache.close();
    }
}
