package com.distbuild.coordinator;

import com.distbuild.common.cache.BuildCache;
import com.distbuild.common.cache.CompileResult;
import com.distbuild.common.graph.ModuleGraph;
import com.distbuild.common.model.ModuleInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simplified coordinator that focuses on core compilation logic without gRPC
 */
public class SimpleCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(SimpleCoordinator.class);
    
    private final BuildCache cache;
    private final ExecutorService executorService;
    private final AtomicInteger taskCounter = new AtomicInteger(0);

    public SimpleCoordinator(BuildCache cache) {
        this.cache = Objects.requireNonNull(cache);
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "SimpleCoordinator-" + taskCounter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        
        logger.info("SimpleCoordinator initialized with cache");
    }

    /**
     * Coordinates a distributed build for the given module graph
     */
    public CompletableFuture<Map<String, CompileResult>> build(ModuleGraph moduleGraph) {
        logger.info("Starting distributed build for {} modules", moduleGraph.size());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeBuild(moduleGraph);
            } catch (Exception e) {
                logger.error("Build failed", e);
                throw new RuntimeException("Build failed", e);
            }
        }, executorService);
    }

    private Map<String, CompileResult> executeBuild(ModuleGraph moduleGraph) throws Exception {
        Map<String, CompileResult> results = new ConcurrentHashMap<>();
        
        try {
            // Get modules in compilation order, filtering out annotation processor modules
            List<List<String>> parallelBatches = moduleGraph.getParallelBatches();
            
            logger.info("Executing {} parallel batches for {} modules", 
                       parallelBatches.size(), moduleGraph.size());

            for (int batchIndex = 0; batchIndex < parallelBatches.size(); batchIndex++) {
                List<String> batch = parallelBatches.get(batchIndex);
                logger.info("Executing batch {}/{} with {} modules: {}", 
                           batchIndex + 1, parallelBatches.size(), batch.size(), batch);
                
                // Execute batch in parallel
                Map<String, CompletableFuture<CompileResult>> batchFutures = executeBatch(batch, moduleGraph);
                
                // Wait for all tasks in this batch to complete
                for (Map.Entry<String, CompletableFuture<CompileResult>> entry : batchFutures.entrySet()) {
                    String moduleName = entry.getKey();
                    CompletableFuture<CompileResult> future = entry.getValue();
                    
                    try {
                        CompileResult result = future.get(5, TimeUnit.MINUTES);
                        results.put(moduleName, result);
                        
                        if (result.isSuccess()) {
                            logger.info("Module {} compiled successfully in {}ms", 
                                       moduleName, result.getCompileTimeMs());
                        } else {
                            logger.error("Module {} compilation failed: {}", 
                                        moduleName, result.getErrorMessage());
                        }
                    } catch (TimeoutException e) {
                        logger.error("Module {} compilation timed out", moduleName);
                        results.put(moduleName, CompileResult.failure("Compilation timed out", 300000));
                    } catch (Exception e) {
                        logger.error("Module {} compilation failed with exception", moduleName, e);
                        results.put(moduleName, CompileResult.failure("Compilation failed: " + e.getMessage(), 0));
                    }
                }
            }
            
            logger.info("Build completed. Success: {}, Failed: {}", 
                       results.values().stream().mapToInt(r -> r.isSuccess() ? 1 : 0).sum(),
                       results.values().stream().mapToInt(r -> r.isSuccess() ? 0 : 1).sum());
            
            return results;
            
        } catch (Exception e) {
            logger.error("Build execution failed", e);
            throw e;
        }
    }

    private Map<String, CompletableFuture<CompileResult>> executeBatch(List<String> batch, ModuleGraph moduleGraph) {
        Map<String, CompletableFuture<CompileResult>> futures = new ConcurrentHashMap<>();
        
        for (String moduleName : batch) {
            Optional<ModuleInfo> moduleOpt = moduleGraph.getModule(moduleName);
            if (moduleOpt.isEmpty()) {
                logger.error("Module {} not found in graph", moduleName);
                futures.put(moduleName, CompletableFuture.completedFuture(
                    CompileResult.failure("Module not found in graph", 0)));
                continue;
            }
            
            ModuleInfo module = moduleOpt.get();
            CompletableFuture<CompileResult> future = compileModuleAsync(module, moduleGraph);
            futures.put(moduleName, future);
        }
        
        return futures;
    }

    private CompletableFuture<CompileResult> compileModuleAsync(ModuleInfo module, ModuleGraph moduleGraph) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return compileModule(module, moduleGraph);
            } catch (Exception e) {
                logger.error("Async compilation failed for module {}", module.getName(), e);
                return CompileResult.failure("Async compilation failed: " + e.getMessage(), 0);
            }
        }, executorService);
    }

    private CompileResult compileModule(ModuleInfo module, ModuleGraph moduleGraph) throws Exception {
        String moduleName = module.getName();
        logger.debug("Compiling module: {}", moduleName);
        
        // Check cache first
        String cacheKey = generateCacheKey(module, moduleGraph);
        Optional<CompileResult> cached = cache.get(cacheKey);
        if (cached.isPresent()) {
            logger.debug("Cache hit for module: {}", moduleName);
            return cached.get();
        }
        
        // For now, simulate compilation
        logger.debug("Compiling module {} locally (simulated)", moduleName);
        return compileLocally(module);
    }

    private CompileResult compileLocally(ModuleInfo module) throws Exception {
        // Simulate compilation
        logger.debug("Simulating compilation of module {}", module.getName());
        
        try {
            // Simulate compilation time
            Thread.sleep(500 + (int)(Math.random() * 1000));
            
            // Create a mock successful result
            Set<String> classFiles = new HashSet<>();
            for (String sourceFile : module.getSourceFiles()) {
                String classFile = sourceFile.replace(".java", ".class")
                                            .replace("src/main/java/", "build/classes/java/main/");
                classFiles.add(classFile);
            }
            
            Map<String, byte[]> classFileContents = new HashMap<>();
            for (String classFile : classFiles) {
                classFileContents.put(classFile, ("compiled class data for " + classFile).getBytes());
            }
            
            long compileTime = 500 + (int)(Math.random() * 1000);
            
            CompileResult result = CompileResult.success(module.getSourceFiles(), classFiles, classFileContents, compileTime);
            
            // Cache the result
            String cacheKey = generateCacheKey(module, null);
            cache.put(cacheKey, result);
            
            return result;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompileResult.failure("Local compilation interrupted", 0);
        }
    }

    private String generateCacheKey(ModuleInfo module, ModuleGraph moduleGraph) {
        // Simple cache key generation - in production, use InputHasher
        return module.getName() + "-" + module.getSourceFiles().hashCode() + "-" + 
               module.getClasspathJars().hashCode() + "-" + module.getJavaVersion();
    }

    public void shutdown() {
        logger.info("Shutting down SimpleCoordinator");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
