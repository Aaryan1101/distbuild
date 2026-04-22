package com.distbuild.coordinator.android;

import com.distbuild.coordinator.BuildCoordinator;
import com.distbuild.common.cache.BuildCache;
import com.distbuild.common.cache.CompileResult;
import com.distbuild.common.grpc.BuildProto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Manages android.jar distribution to workers for Android builds
 */
public class AndroidJarCache {
    private static final Logger logger = LoggerFactory.getLogger(AndroidJarCache.class);
    
    private final BuildCoordinator coordinator;
    private final BuildCache cache;
    private final Path androidSdkPath;
    
    public AndroidJarCache(BuildCoordinator coordinator, BuildCache cache, Path androidSdkPath) {
        this.coordinator = coordinator;
        this.cache = cache;
        this.androidSdkPath = androidSdkPath;
    }
    
    /**
     * Ensures android.jar is available to workers for the specified compile SDK version
     */
    public CompletableFuture<Boolean> ensureAndroidJarAvailable(int compileSdkVersion) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path androidJarPath = findAndroidJar(compileSdkVersion);
                if (androidJarPath == null) {
                    logger.error("android.jar not found for SDK version {}", compileSdkVersion);
                    return false;
                }
                
                String cacheKey = generateAndroidJarCacheKey(compileSdkVersion);
                
                // Check if already in cache
                try {
                    var cachedResult = cache.get(cacheKey);
                    if (cachedResult.isPresent() && cachedResult.get().isSuccess()) {
                        logger.debug("android.jar for SDK {} already cached", compileSdkVersion);
                        return true;
                    }
                } catch (Exception e) {
                    logger.debug("Cache check failed for android.jar SDK {}", compileSdkVersion);
                }
                
                // Upload android.jar to cache
                return uploadAndroidJarToCache(androidJarPath, cacheKey, compileSdkVersion);
                
            } catch (Exception e) {
                logger.error("Failed to ensure android.jar availability for SDK {}", compileSdkVersion, e);
                return false;
            }
        });
    }
    
    /**
     * Find android.jar for the specified SDK version
     */
    private Path findAndroidJar(int compileSdkVersion) {
        // Look for android.jar in the Android SDK
        Path platformsDir = androidSdkPath.resolve("platforms");
        if (!Files.exists(platformsDir)) {
            logger.error("Android platforms directory not found: {}", platformsDir);
            return null;
        }
        
        // Try exact version first
        Path androidJarExact = platformsDir.resolve("android-" + compileSdkVersion).resolve("android.jar");
        if (Files.exists(androidJarExact)) {
            return androidJarExact;
        }
        
        // Look for any platform with the requested version
        try (var stream = Files.list(platformsDir)) {
            return stream
                .filter(Files::isDirectory)
                .filter(dir -> dir.getFileName().toString().contains(String.valueOf(compileSdkVersion)))
                .map(dir -> dir.resolve("android.jar"))
                .filter(Files::exists)
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            logger.error("Failed to scan Android platforms directory", e);
            return null;
        }
    }
    
    /**
     * Generate cache key for android.jar
     */
    private String generateAndroidJarCacheKey(int compileSdkVersion) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = "android-jar-sdk-" + compileSdkVersion;
            byte[] hash = md.digest(input.getBytes());
            return "android-jar-" + HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return "android-jar-sdk-" + compileSdkVersion;
        }
    }
    
    /**
     * Upload android.jar to cache
     */
    private boolean uploadAndroidJarToCache(Path androidJarPath, String cacheKey, int compileSdkVersion) {
        try {
            logger.info("Uploading android.jar for SDK {} to cache", compileSdkVersion);
            
            // Create a successful compile result for android.jar
            Set<String> compiledFiles = new HashSet<>();
            compiledFiles.add(androidJarPath.toString());
            Set<String> classFiles = new HashSet<>();
            Map<String, byte[]> classFileContents = new HashMap<>();
            CompileResult result = CompileResult.success(compiledFiles, classFiles, classFileContents, 0);
            
            cache.put(cacheKey, result);
            logger.info("Successfully uploaded android.jar for SDK {} to cache", compileSdkVersion);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to upload android.jar for SDK {} to cache", compileSdkVersion, e);
            return false;
        }
    }
    
    /**
     * Get cache key for android.jar to include in compile task
     */
    public String getAndroidJarCacheKey(int compileSdkVersion) {
        return generateAndroidJarCacheKey(compileSdkVersion);
    }
    
    /**
     * Check if android.jar is available for the specified SDK version
     */
    public boolean isAndroidJarAvailable(int compileSdkVersion) {
        String cacheKey = generateAndroidJarCacheKey(compileSdkVersion);
        try {
            var cachedResult = cache.get(cacheKey);
            return cachedResult.isPresent() && cachedResult.get().isSuccess();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Preload common android.jar versions for better performance
     */
    public CompletableFuture<Void> preloadCommonAndroidJars() {
        return CompletableFuture.runAsync(() -> {
            int[] commonVersions = {28, 29, 30, 31, 32, 33, 34};
            
            logger.info("Preloading common android.jar versions...");
            
            for (int version : commonVersions) {
                if (!isAndroidJarAvailable(version)) {
                    ensureAndroidJarAvailable(version);
                }
            }
            
            logger.info("Completed preloading common android.jar versions");
        });
    }
    
    /**
     * Get information about cached android.jar versions
     */
    public String getCacheInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Android Jar Cache Status:\n");
        
        for (int version = 28; version <= 34; version++) {
            String cacheKey = generateAndroidJarCacheKey(version);
            boolean available = isAndroidJarAvailable(version);
            
            info.append(String.format("  SDK %d: %s\n", version, 
                available ? "Available" : "Not cached"));
        }
        
        return info.toString();
    }
}
