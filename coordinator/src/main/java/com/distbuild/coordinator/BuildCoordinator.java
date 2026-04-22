package com.distbuild.coordinator;

import com.distbuild.common.cache.BuildCache;
import com.distbuild.common.cache.CompileResult;
import com.distbuild.common.graph.ModuleGraph;
import com.distbuild.common.model.ModuleInfo;
import com.distbuild.common.grpc.*;
import com.distbuild.coordinator.WorkerManager.WorkerInfo;
import com.distbuild.coordinator.WorkerManager.WorkerStatus;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Coordinates distributed compilation across multiple workers
 */
public class BuildCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(BuildCoordinator.class);
    
    private final BuildCache cache;
    private final WorkerManager workerManager;
    private final ExecutorService executorService;
    private final Map<String, CompletableFuture<CompileResult>> activeTasks = new ConcurrentHashMap<>();
    private final AtomicInteger taskCounter = new AtomicInteger(0);
    private final AtomicInteger threadCounter = new AtomicInteger(0);

    public BuildCoordinator(BuildCache cache, WorkerManager workerManager) {
        this.cache = Objects.requireNonNull(cache);
        this.workerManager = Objects.requireNonNull(workerManager);
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "BuildCoordinator-" + threadCounter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        
        logger.info("BuildCoordinator initialized with cache and worker manager");
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
            List<String> compilationOrder = moduleGraph.getModulesInCompilationOrder();
            List<List<String>> parallelBatches = moduleGraph.getParallelBatches();
            
            logger.info("Executing {} parallel batches for {} modules", 
                       parallelBatches.size(), compilationOrder.size());

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
        
        // Find available worker
        WorkerInfo worker = workerManager.getAvailableWorker();
        if (worker == null) {
            logger.warn("No workers available for module: {}, falling back to local compilation", moduleName);
            return compileLocally(module);
        }
        
        // Compile on worker
        logger.debug("Compiling module {} on worker {}", moduleName, worker.getId());
        return compileOnWorker(module, worker, cacheKey);
    }

    private CompileResult compileOnWorker(ModuleInfo module, WorkerInfo worker, String cacheKey) throws Exception {
        String taskId = "task-" + taskCounter.incrementAndGet();
        
        // Build compile task request
        BuildProto.CompileTaskRequest request = BuildProto.CompileTaskRequest.newBuilder()
                .setTaskId(taskId)
                .setModuleName(module.getName())
                .addAllSourceFiles(module.getSourceFiles())
                .addAllClasspathJars(module.getClasspathJars())
                .addAllJarHashes(extractJarHashes(module))
                .setOutputDirectory(module.getOutputDirectory().toString())
                .putAllCompilerOptions(module.getCompilerOptions())
                .setJavaVersion(module.getJavaVersion())
                .build();
        
        // Execute on worker
        CompletableFuture<BuildProto.CompileTaskResponse> future = new CompletableFuture<>();
        workerManager.executeTask(worker.getId(), request, future);
        
        try {
            BuildProto.CompileTaskResponse response = future.get(10, TimeUnit.MINUTES);
            
            CompileResult result = convertResponse(response);
            
            // Cache successful results
            if (result.isSuccess()) {
                cache.put(cacheKey, result);
            }
            
            return result;
            
        } catch (TimeoutException e) {
            logger.error("Worker task {} timed out", taskId);
            workerManager.markWorkerUnavailable(worker.getId());
            return CompileResult.failure("Worker task timed out", 600000);
        }
    }

    private CompileResult compileLocally(ModuleInfo module) {
        // Placeholder for local compilation
        // In a real implementation, this would use the Java compiler API
        logger.debug("Compiling module {} locally", module.getName());
        
        try {
            // Simulate compilation time
            Thread.sleep(1000);
            
            // Create a mock successful result
            Set<String> classFiles = new HashSet<>();
            for (String sourceFile : module.getSourceFiles()) {
                String classFile = sourceFile.replace(".java", ".class")
                                            .replace("src/main/java/", "build/classes/java/main/");
                classFiles.add(classFile);
            }
            
            Map<String, byte[]> classFileContents = new HashMap<>();
            for (String classFile : classFiles) {
                classFileContents.put(classFile, "mock class content".getBytes());
            }
            
            return CompileResult.success(module.getSourceFiles(), classFiles, classFileContents, 1000);
            
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

    private List<String> extractJarHashes(ModuleInfo module) {
        // Placeholder for jar hash extraction
        // In production, this would use the InputHasher to compute actual hashes
        return module.getClasspathJars().stream()
                .map(jar -> jar.hashCode() + "") // Mock hash
                .collect(Collectors.toList());
    }

    /**
     * Get list of available workers
     */
    public List<String> getAvailableWorkers() {
        return workerManager.getAllWorkers().stream()
                .filter(worker -> worker.getStatus() == WorkerStatus.AVAILABLE)
                .map(WorkerInfo::getId)
                .collect(Collectors.toList());
    }
    
    /**
     * Check if worker has specific specialization
     */
    public boolean isWorkerSpecialized(String workerId, String specialization) {
        WorkerInfo worker = workerManager.getAllWorkers().stream()
                .filter(w -> w.getId().equals(workerId))
                .findFirst()
                .orElse(null);
        
        if (worker == null) {
            return false;
        }
        
        return worker.getCapabilities().containsValue(specialization);
    }
    
    /**
     * Get gRPC channel for worker
     */
    public io.grpc.ManagedChannel getWorkerChannel(String workerId) {
        WorkerInfo worker = workerManager.getAllWorkers().stream()
                .filter(w -> w.getId().equals(workerId))
                .findFirst()
                .orElse(null);
        
        if (worker == null) {
            return null;
        }
        
        try {
            return io.grpc.ManagedChannelBuilder.forAddress(worker.getHost(), worker.getPort())
                    .usePlaintext()
                    .build();
        } catch (Exception e) {
            logger.error("Failed to create channel for worker {}", workerId, e);
            return null;
        }
    }

    private CompileResult convertResponse(BuildProto.CompileTaskResponse response) {
        if (!response.getSuccess()) {
            return CompileResult.failure(response.getErrorMessage(), response.getCompileTimeMs());
        }
        
        Set<String> compiledFiles = new HashSet<>(response.getCompiledFilesList());
        Set<String> classFiles = new HashSet<>(response.getClassFilesList());
        Map<String, byte[]> classFileContents = new HashMap<>();
        
        // For now, create empty class file contents - in a real implementation,
        // the worker would send the actual compiled class data
        for (String classFile : classFiles) {
            classFileContents.put(classFile, "compiled class data".getBytes());
        }
        
        byte[] incrementalState = response.getNewIncrementalState() != null ? 
                               response.getNewIncrementalState().toByteArray() : null;
        
        return CompileResult.builder()
                .success(true)
                .compiledFiles(compiledFiles)
                .classFiles(classFiles)
                .classFileContents(classFileContents)
                .compileTimeMs(response.getCompileTimeMs())
                .incrementalState(incrementalState)
                .javaVersion("17") // Would come from response
                .build();
    }

    public void shutdown() {
        logger.info("Shutting down BuildCoordinator");
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
