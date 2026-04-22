package com.distbuild.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Local disk-based implementation of BuildCache
 */
public class LocalDiskCache implements BuildCache {
    private static final Logger logger = LoggerFactory.getLogger(LocalDiskCache.class);
    
    private final Path cacheDirectory;
    private final long maxSizeBytes;
    private final CacheStats stats;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, CacheEntry> index = new ConcurrentHashMap<>();

    public LocalDiskCache(Path cacheDirectory) {
        this(cacheDirectory, 1024 * 1024 * 1024); // 1GB default
    }

    public LocalDiskCache(Path cacheDirectory, long maxSizeBytes) {
        this.cacheDirectory = Objects.requireNonNull(cacheDirectory);
        this.maxSizeBytes = maxSizeBytes;
        this.stats = new CacheStats();
        
        try {
            Files.createDirectories(cacheDirectory);
            loadIndex();
            logger.info("LocalDiskCache initialized at {} with max size {} bytes", 
                       cacheDirectory, maxSizeBytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize cache directory", e);
        }
    }

    @Override
    public void put(String key, CompileResult result) throws CacheException {
        if (!result.isSuccess()) {
            logger.debug("Skipping cache put for failed compilation");
            return;
        }

        lock.writeLock().lock();
        try {
            Path entryPath = getEntryPath(key);
            byte[] data = result.toJson();
            
            // Check if we need to evict entries
            ensureSpace(data.length);
            
            // Write the data
            Files.write(entryPath, data, StandardOpenOption.CREATE, 
                       StandardOpenOption.TRUNCATE_EXISTING);
            
            // Update index
            index.put(key, new CacheEntry(key, entryPath, data.length, Instant.now()));
            
            stats.recordPut();
            logger.debug("Cached result for key: {} ({} bytes)", key, data.length);
            
        } catch (IOException e) {
            stats.recordError();
            throw new CacheException("Failed to cache result for key: " + key, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Optional<CompileResult> get(String key) throws CacheException {
        lock.readLock().lock();
        try {
            CacheEntry entry = index.get(key);
            if (entry == null) {
                stats.recordMiss();
                return Optional.empty();
            }
            
            // Check if file still exists
            if (!Files.exists(entry.getPath())) {
                index.remove(key);
                stats.recordMiss();
                return Optional.empty();
            }
            
            // Read the data
            byte[] data = Files.readAllBytes(entry.getPath());
            CompileResult result = CompileResult.fromJson(data);
            
            stats.recordHit();
            logger.debug("Cache hit for key: {} ({} bytes)", key, data.length);
            return Optional.of(result);
            
        } catch (IOException e) {
            stats.recordError();
            throw new CacheException("Failed to read cached result for key: " + key, e);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean contains(String key) throws CacheException {
        lock.readLock().lock();
        try {
            CacheEntry entry = index.get(key);
            if (entry == null) {
                return false;
            }
            
            boolean exists = Files.exists(entry.getPath());
            if (!exists) {
                index.remove(key);
            }
            return exists;
            
        } catch (Exception e) {
            stats.recordError();
            throw new CacheException("Failed to check cache for key: " + key, e);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void remove(String key) throws CacheException {
        lock.writeLock().lock();
        try {
            CacheEntry entry = index.remove(key);
            if (entry != null) {
                Files.deleteIfExists(entry.getPath());
                logger.debug("Removed cache entry for key: {}", key);
            }
        } catch (IOException e) {
            stats.recordError();
            throw new CacheException("Failed to remove cache entry for key: " + key, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void clear() throws CacheException {
        lock.writeLock().lock();
        try {
            // Delete all cache files
            for (CacheEntry entry : index.values()) {
                Files.deleteIfExists(entry.getPath());
            }
            
            // Clear index
            index.clear();
            logger.info("Cleared all cache entries");
            
        } catch (IOException e) {
            stats.recordError();
            throw new CacheException("Failed to clear cache", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public CacheStats getStats() {
        return stats;
    }

    @Override
    public void close() throws CacheException {
        lock.writeLock().lock();
        try {
            saveIndex();
            logger.info("LocalDiskCache closed");
        } catch (IOException e) {
            throw new CacheException("Failed to save cache index", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private Path getEntryPath(String key) {
        // Use first 2 characters as subdirectory to avoid too many files in one directory
        String prefix = key.length() >= 2 ? key.substring(0, 2) : key;
        Path subDir = cacheDirectory.resolve(prefix);
        try {
            Files.createDirectories(subDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create cache subdirectory", e);
        }
        return subDir.resolve(key);
    }

    private void ensureSpace(long requiredBytes) throws IOException {
        long currentSize = getCurrentSize();
        
        if (currentSize + requiredBytes <= maxSizeBytes) {
            return; // Enough space
        }
        
        // Evict least recently used entries
        List<CacheEntry> entries = new ArrayList<>(index.values());
        entries.sort(Comparator.comparing(e -> e.lastAccessed));
        
        for (CacheEntry entry : entries) {
            Files.deleteIfExists(entry.getPath());
            index.remove(entry.key);
            stats.recordEviction();
            currentSize -= entry.size;
            
            logger.debug("Evicted cache entry: {} ({} bytes)", entry.key, entry.size);
            
            if (currentSize + requiredBytes <= maxSizeBytes) {
                break;
            }
        }
    }

    private long getCurrentSize() {
        return index.values().stream().mapToLong(e -> e.size).sum();
    }

    private void loadIndex() throws IOException {
        Path indexPath = cacheDirectory.resolve("cache.index");
        if (!Files.exists(indexPath)) {
            return;
        }
        
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(indexPath.toFile()))) {
            @SuppressWarnings("unchecked")
            Map<String, CacheEntry> loadedIndex = (Map<String, CacheEntry>) ois.readObject();
            index.putAll(loadedIndex);
            
            // Remove entries whose files don't exist anymore
            index.entrySet().removeIf(entry -> !Files.exists(entry.getValue().getPath()));
            
            logger.info("Loaded cache index with {} entries", index.size());
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to load cache index", e);
        }
    }

    private void saveIndex() throws IOException {
        Path indexPath = cacheDirectory.resolve("cache.index");
        
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(indexPath.toFile()))) {
            oos.writeObject(new HashMap<>(index));
        }
    }

    private static class CacheEntry implements Serializable {
        private static final long serialVersionUID = 1L;
        
        final String key;
        final String pathString;
        final long size;
        final Instant lastAccessed;

        CacheEntry(String key, Path path, long size, Instant lastAccessed) {
            this.key = key;
            this.pathString = path.toString();
            this.size = size;
            this.lastAccessed = lastAccessed;
        }
        
        public Path getPath() {
            return Path.of(pathString);
        }
    }
}
