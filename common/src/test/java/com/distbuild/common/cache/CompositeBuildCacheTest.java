package com.distbuild.common.cache;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.*;

public class CompositeBuildCacheTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testL1CacheHit() throws CacheException {
        Path cacheDir1 = tempFolder.getRoot().toPath().resolve("cache1");
        Path cacheDir2 = tempFolder.getRoot().toPath().resolve("cache2");
        BuildCache localCache = new LocalDiskCache(cacheDir1);
        BuildCache remoteCache = new LocalDiskCache(cacheDir2);
        CompositeBuildCache compositeCache = new CompositeBuildCache(localCache, remoteCache);

        CompileResult result = CompileResult.success(
            Set.of("src/Main.java"),
            Set.of("build/classes/Main.class"),
            java.util.Map.of("build/classes/Main.class", "class data".getBytes()),
            1000
        );

        // Store in composite cache
        compositeCache.put("test-key", result);

        // Retrieve - should hit L1 cache
        Optional<CompileResult> retrieved = compositeCache.get("test-key");
        assertTrue(retrieved.isPresent());
        assertTrue(retrieved.get().isSuccess());

        // Check stats - should have 1 hit
        assertEquals(1, compositeCache.getStats().getHits());
        assertEquals(0, compositeCache.getStats().getMisses());

        compositeCache.close();
    }

    @Test
    public void testL2CacheHit() throws CacheException, InterruptedException {
        Path cacheDir1 = tempFolder.getRoot().toPath().resolve("cache1");
        Path cacheDir2 = tempFolder.getRoot().toPath().resolve("cache2");
        BuildCache localCache = new LocalDiskCache(cacheDir1);
        BuildCache remoteCache = new LocalDiskCache(cacheDir2); // Use local disk as mock remote
        CompositeBuildCache compositeCache = new CompositeBuildCache(localCache, remoteCache);

        CompileResult result = CompileResult.success(
            Set.of("src/Main.java"),
            Set.of("build/classes/Main.class"),
            java.util.Map.of("build/classes/Main.class", "class data".getBytes()),
            1000
        );

        // Store directly in remote cache (simulating another node)
        remoteCache.put("test-key", result);

        // Retrieve from composite - should hit L2 and populate L1
        Optional<CompileResult> retrieved = compositeCache.get("test-key");
        assertTrue(retrieved.isPresent());
        assertTrue(retrieved.get().isSuccess());

        // Check stats - should have 1 hit
        assertEquals(1, compositeCache.getStats().getHits());
        assertEquals(0, compositeCache.getStats().getMisses());

        // Wait a bit for async population to complete
        Thread.sleep(100);
        
        // Verify L1 is now populated
        assertTrue(localCache.contains("test-key"));

        compositeCache.close();
    }

    @Test
    public void testCacheMiss() throws CacheException {
        Path cacheDir1 = tempFolder.getRoot().toPath().resolve("cache1");
        Path cacheDir2 = tempFolder.getRoot().toPath().resolve("cache2");
        BuildCache localCache = new LocalDiskCache(cacheDir1);
        BuildCache remoteCache = new LocalDiskCache(cacheDir2);
        CompositeBuildCache compositeCache = new CompositeBuildCache(localCache, remoteCache);

        // Try to get non-existent key
        Optional<CompileResult> retrieved = compositeCache.get("non-existent-key");
        assertFalse(retrieved.isPresent());

        // Check stats - should have 1 miss
        assertEquals(0, compositeCache.getStats().getHits());
        assertEquals(1, compositeCache.getStats().getMisses());

        compositeCache.close();
    }

    @Test
    public void testPutToBothCaches() throws CacheException {
        Path cacheDir1 = tempFolder.getRoot().toPath().resolve("cache1");
        Path cacheDir2 = tempFolder.getRoot().toPath().resolve("cache2");
        BuildCache localCache = new LocalDiskCache(cacheDir1);
        BuildCache remoteCache = new LocalDiskCache(cacheDir2);
        CompositeBuildCache compositeCache = new CompositeBuildCache(localCache, remoteCache);

        CompileResult result = CompileResult.success(
            Set.of("src/Main.java"),
            Set.of("build/classes/Main.class"),
            java.util.Map.of("build/classes/Main.class", "class data".getBytes()),
            1000
        );

        compositeCache.put("test-key", result);

        // Verify both caches have the entry
        assertTrue(localCache.contains("test-key"));
        assertTrue(remoteCache.contains("test-key"));

        compositeCache.close();
    }

    @Test
    public void testRemoveFromBothCaches() throws CacheException {
        Path cacheDir1 = tempFolder.getRoot().toPath().resolve("cache1");
        Path cacheDir2 = tempFolder.getRoot().toPath().resolve("cache2");
        BuildCache localCache = new LocalDiskCache(cacheDir1);
        BuildCache remoteCache = new LocalDiskCache(cacheDir2);
        CompositeBuildCache compositeCache = new CompositeBuildCache(localCache, remoteCache);

        CompileResult result = CompileResult.success(
            Set.of("src/Main.java"),
            Set.of("build/classes/Main.class"),
            java.util.Map.of("build/classes/Main.class", "class data".getBytes()),
            1000
        );

        compositeCache.put("test-key", result);
        assertTrue(localCache.contains("test-key"));
        assertTrue(remoteCache.contains("test-key"));

        compositeCache.remove("test-key");
        assertFalse(localCache.contains("test-key"));
        assertFalse(remoteCache.contains("test-key"));

        compositeCache.close();
    }

    @Test
    public void testClearBothCaches() throws CacheException {
        Path cacheDir1 = tempFolder.getRoot().toPath().resolve("cache1");
        Path cacheDir2 = tempFolder.getRoot().toPath().resolve("cache2");
        BuildCache localCache = new LocalDiskCache(cacheDir1);
        BuildCache remoteCache = new LocalDiskCache(cacheDir2);
        CompositeBuildCache compositeCache = new CompositeBuildCache(localCache, remoteCache);

        CompileResult result = CompileResult.success(
            Set.of("src/Main.java"),
            Set.of("build/classes/Main.class"),
            java.util.Map.of("build/classes/Main.class", "class data".getBytes()),
            1000
        );

        compositeCache.put("key1", result);
        compositeCache.put("key2", result);

        assertTrue(localCache.contains("key1"));
        assertTrue(remoteCache.contains("key1"));

        compositeCache.clear();
        assertFalse(localCache.contains("key1"));
        assertFalse(remoteCache.contains("key1"));

        compositeCache.close();
    }

    @Test
    public void testDetailedStats() throws CacheException {
        Path cacheDir1 = tempFolder.getRoot().toPath().resolve("cache1");
        Path cacheDir2 = tempFolder.getRoot().toPath().resolve("cache2");
        BuildCache localCache = new LocalDiskCache(cacheDir1);
        BuildCache remoteCache = new LocalDiskCache(cacheDir2);
        CompositeBuildCache compositeCache = new CompositeBuildCache(localCache, remoteCache);

        CompileResult result = CompileResult.success(
            Set.of("src/Main.java"),
            Set.of("build/classes/Main.class"),
            java.util.Map.of("build/classes/Main.class", "class data".getBytes()),
            1000
        );

        compositeCache.put("test-key", result);
        compositeCache.get("test-key");

        CompositeBuildCache.CompositeCacheStats detailedStats = compositeCache.getDetailedStats();
        
        assertNotNull(detailedStats.getCompositeStats());
        assertNotNull(detailedStats.getLocalStats());
        assertNotNull(detailedStats.getRemoteStats());

        // Composite should have 1 hit
        assertEquals(1, detailedStats.getCompositeStats().getHits());
        
        // Local should have 1 put and 1 hit
        assertEquals(1, detailedStats.getLocalStats().getPuts());
        assertEquals(1, detailedStats.getLocalStats().getHits());
        
        // Remote should have 1 put and 0 hits (we only put to it directly)
        assertEquals(1, detailedStats.getRemoteStats().getPuts());
        assertEquals(0, detailedStats.getRemoteStats().getHits());

        compositeCache.close();
    }
}
