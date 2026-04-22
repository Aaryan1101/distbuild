package com.distbuild.coordinator;

import com.distbuild.common.grpc.BuildProto;
import com.distbuild.common.grpc.BuildServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages worker registration, health monitoring, and task distribution
 */
public class WorkerManager {
    private static final Logger logger = LoggerFactory.getLogger(WorkerManager.class);
    
    private final Map<String, WorkerInfo> workers = new ConcurrentHashMap<>();
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();
    private final Map<String, BuildServiceGrpc.BuildServiceBlockingStub> stubs = new ConcurrentHashMap<>();
    private final ScheduledExecutorService healthCheckExecutor;
    private final Random loadBalancer = new Random();
    private WorkerConfig.WorkersConfig workersConfig;
    
    // Load balancing strategies
    public enum LoadBalancingStrategy {
        ROUND_ROBIN, LEAST_LOADED, WEIGHTED_RESPONSE_TIME, CAPABILITY_BASED
    }
    
    private volatile LoadBalancingStrategy strategy = LoadBalancingStrategy.LEAST_LOADED;
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    public WorkerManager() {
        this(new WorkerConfig.WorkersConfig());
    }
    
    public WorkerManager(WorkerConfig.WorkersConfig workersConfig) {
        this.workersConfig = workersConfig;
        this.healthCheckExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "WorkerHealthCheck");
            t.setDaemon(true);
            return t;
        });
        
        // Start health checking
        startHealthChecking();
        
        logger.info("WorkerManager initialized with {} specializations", 
                   workersConfig.specializations.size());
    }

    /**
     * Registers a new worker
     */
    public boolean registerWorker(BuildProto.RegisterWorkerRequest request) {
        String workerId = request.getWorkerId();
        String host = request.getHost();
        int port = request.getPort();
        
        logger.info("Registering worker {} at {}:{}", workerId, host, port);
        
        try {
            // Create gRPC channel
            ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .keepAliveTime(30, TimeUnit.SECONDS)
                    .build();
            
            // Create stub
            BuildServiceGrpc.BuildServiceBlockingStub stub = BuildServiceGrpc.newBlockingStub(channel);
            
            // Test connection
            BuildProto.GetWorkerStatusRequest statusRequest = BuildProto.GetWorkerStatusRequest.newBuilder()
                    .setWorkerId(workerId)
                    .build();
            
            BuildProto.GetWorkerStatusResponse statusResponse = stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                    .getWorkerStatus(statusRequest);
            
            // Create specializations from configuration
            List<WorkerSpecialization> specializations = new ArrayList<>();
            for (WorkerConfig.WorkerSpecialization configSpec : workersConfig.specializations) {
                specializations.add(new WorkerSpecialization(
                    configSpec.name,
                    configSpec.pattern,
                    configSpec.capabilities,
                    configSpec.preferences,
                    configSpec.priority
                ));
            }
            
            // Combine default capabilities with worker capabilities
            Map<String, String> allCapabilities = new HashMap<>(workersConfig.defaultCapabilities);
            allCapabilities.putAll(request.getCapabilitiesMap());
            
            // Store worker info
            WorkerInfo worker = new WorkerInfo(workerId, host, port, request.getMaxConcurrentTasks(),
                                          request.getJavaVersion(), allCapabilities, 
                                          specializations, new HashMap<>());
            worker.setStatus(WorkerStatus.AVAILABLE);
            worker.setLastHeartbeat(System.currentTimeMillis());
            
            workers.put(workerId, worker);
            channels.put(workerId, channel);
            stubs.put(workerId, stub);
            
            logger.info("Successfully registered worker {}", workerId);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to register worker {} at {}:{}", workerId, host, port, e);
            // Cleanup on failure
            channels.remove(workerId);
            stubs.remove(workerId);
            return false;
        }
    }

    /**
     * Gets an available worker for task execution
     */
    public WorkerInfo getAvailableWorker() {
        return getAvailableWorker(null);
    }
    
    /**
     * Gets an available worker for task execution with optional task requirements
     */
    public WorkerInfo getAvailableWorker(BuildProto.CompileTaskRequest taskRequest) {
        List<WorkerInfo> availableWorkers = workers.values().stream()
                .filter(w -> w.getStatus() == WorkerStatus.AVAILABLE)
                .filter(w -> w.getActiveTasks() < w.getMaxConcurrentTasks())
                .toList();
        
        if (availableWorkers.isEmpty()) {
            return null;
        }
        
        // Apply intelligent load balancing strategy
        return selectWorker(availableWorkers, taskRequest);
    }
    
    /**
     * Selects the best worker based on the current strategy
     */
    private WorkerInfo selectWorker(List<WorkerInfo> availableWorkers, BuildProto.CompileTaskRequest taskRequest) {
        switch (strategy) {
            case ROUND_ROBIN:
                return selectRoundRobin(availableWorkers);
            case LEAST_LOADED:
                return selectLeastLoaded(availableWorkers);
            case WEIGHTED_RESPONSE_TIME:
                return selectWeightedByResponseTime(availableWorkers);
            case CAPABILITY_BASED:
                return selectCapabilityBased(availableWorkers, taskRequest);
            default:
                return selectLeastLoaded(availableWorkers);
        }
    }
    
    /**
     * Round-robin selection
     */
    private WorkerInfo selectRoundRobin(List<WorkerInfo> workers) {
        int index = roundRobinCounter.getAndIncrement() % workers.size();
        return workers.get(index);
    }
    
    /**
     * Least-loaded selection (minimum active tasks)
     */
    private WorkerInfo selectLeastLoaded(List<WorkerInfo> workers) {
        return workers.stream()
                .min(Comparator.comparingDouble(w -> {
                    double loadRatio = (double) w.getActiveTasks() / w.getMaxConcurrentTasks();
                    return loadRatio;
                }))
                .orElse(workers.get(0));
    }
    
    /**
     * Weighted by response time and load
     */
    private WorkerInfo selectWeightedByResponseTime(List<WorkerInfo> workers) {
        return workers.stream()
                .min(Comparator.comparingDouble(w -> {
                    double loadRatio = (double) w.getActiveTasks() / w.getMaxConcurrentTasks();
                    long totalTasks = w.getTotalTasksCompleted();
                    double avgResponseTime = totalTasks > 0 ? 
                        (double) w.getTotalCompileTime() / totalTasks : 1000.0; // Default 1s
                    
                    // Weight: 70% load, 30% response time
                    return (loadRatio * 0.7) + (avgResponseTime / 10000.0 * 0.3);
                }))
                .orElse(workers.get(0));
    }
    
    /**
     * Capability-based selection for specialized tasks
     */
    private WorkerInfo selectCapabilityBased(List<WorkerInfo> workers, BuildProto.CompileTaskRequest taskRequest) {
        if (taskRequest == null) {
            return selectLeastLoaded(workers);
        }
        
        // Score workers based on task requirements
        return workers.stream()
                .max(Comparator.comparingDouble(w -> scoreWorkerForTask(w, taskRequest)))
                .orElse(workers.get(0));
    }
    
    /**
     * Scores a worker for a specific task
     */
    private double scoreWorkerForTask(WorkerInfo worker, BuildProto.CompileTaskRequest taskRequest) {
        double score = 0.0;
        
        // Base score from load (inverse of load ratio)
        double loadRatio = (double) worker.getActiveTasks() / worker.getMaxConcurrentTasks();
        score += (1.0 - loadRatio) * 0.3; // 30% weight on availability
        
        // Specialization matching (highest priority)
        String moduleName = taskRequest.getModuleName();
        if (moduleName != null) {
            for (WorkerSpecialization spec : worker.getSpecializations()) {
                if (spec.matches(moduleName)) {
                    score += 0.3; // 30% weight on specialization match
                    score += spec.getPriority() * 0.1; // Bonus for specialization priority
                    
                    // Add specialization-specific capabilities
                    Map<String, String> specCapabilities = spec.getCapabilities();
                    if (specCapabilities != null) {
                        for (Map.Entry<String, String> cap : specCapabilities.entrySet()) {
                            if (taskRequest.getCompilerOptionsMap().containsKey(cap.getKey()) &&
                                taskRequest.getCompilerOptionsMap().get(cap.getKey()).equals(cap.getValue())) {
                                score += 0.1; // Bonus for matching specialized capability
                            }
                        }
                    }
                    break; // Use first matching specialization
                }
            }
        }
        
        // Java version compatibility
        String requiredJavaVersion = taskRequest.getJavaVersion();
        if (requiredJavaVersion != null && !requiredJavaVersion.isEmpty()) {
            if (worker.getJavaVersion().startsWith(requiredJavaVersion)) {
                score += 0.15; // 15% weight on version compatibility
            }
        }
        
        // General capability matching
        Map<String, String> workerCapabilities = worker.getCapabilities();
        if (workerCapabilities != null) {
            // Check for specific capabilities
            if (taskRequest.getCompilerOptionsMap().containsKey("native") && 
                "true".equals(workerCapabilities.get("native"))) {
                score += 0.15; // Native compilation support
            }
            
            if (taskRequest.getCompilerOptionsMap().containsKey("debug") && 
                "true".equals(workerCapabilities.get("debug"))) {
                score += 0.05; // Debug support
            }
            
            // Check for OS-specific optimizations
            String os = workerCapabilities.get("os");
            if (os != null && taskRequest.getCompilerOptionsMap().containsKey("optimize-for")) {
                String targetOS = taskRequest.getCompilerOptionsMap().get("optimize-for");
                if (os.toLowerCase().contains(targetOS.toLowerCase())) {
                    score += 0.05; // OS optimization match
                }
            }
        }
        
        // Historical performance
        long totalTasks = worker.getTotalTasksCompleted();
        if (totalTasks > 0) {
            double avgResponseTime = (double) worker.getTotalCompileTime() / totalTasks;
            double performanceScore = Math.max(0, 1.0 - (avgResponseTime / 10000.0)); // Normalize to 0-1
            score += performanceScore * 0.05; // 5% weight on performance
        }
        
        return score;
    }
    
    /**
     * Sets the load balancing strategy
     */
    public void setLoadBalancingStrategy(LoadBalancingStrategy strategy) {
        this.strategy = strategy;
        logger.info("Load balancing strategy changed to: {}", strategy);
    }
    
    /**
     * Gets the current load balancing strategy
     */
    public LoadBalancingStrategy getLoadBalancingStrategy() {
        return strategy;
    }

    /**
     * Executes a task on a specific worker
     */
    public void executeTask(String workerId, BuildProto.CompileTaskRequest request, 
                           CompletableFuture<BuildProto.CompileTaskResponse> future) {
        
        WorkerInfo worker = workers.get(workerId);
        if (worker == null) {
            future.completeExceptionally(new IllegalArgumentException("Worker not found: " + workerId));
            return;
        }
        
        BuildServiceGrpc.BuildServiceBlockingStub stub = stubs.get(workerId);
        if (stub == null) {
            future.completeExceptionally(new IllegalStateException("No stub available for worker: " + workerId));
            return;
        }
        
        // Increment active task count
        worker.incrementActiveTasks();
        worker.setStatus(WorkerStatus.BUSY);
        
        // Execute task asynchronously
        CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            try {
                logger.debug("Executing task {} on worker {}", request.getTaskId(), workerId);
                
                BuildProto.CompileTaskResponse response = stub.withDeadlineAfter(10, TimeUnit.MINUTES)
                        .compileTask(request);
                
                long compileTime = System.currentTimeMillis() - startTime;
                logger.debug("Task {} completed on worker {} with success: {} in {}ms", 
                           request.getTaskId(), workerId, response.getSuccess(), compileTime);
                
                // Record performance metrics
                if (response.getSuccess()) {
                    worker.recordTaskCompletion(compileTime);
                }
                
                future.complete(response);
                
            } catch (Exception e) {
                long compileTime = System.currentTimeMillis() - startTime;
                logger.error("Task {} failed on worker {} after {}ms", request.getTaskId(), workerId, compileTime, e);
                future.completeExceptionally(e);
            } finally {
                // Decrement active task count
                worker.decrementActiveTasks();
                if (worker.getActiveTasks() == 0) {
                    worker.setStatus(WorkerStatus.AVAILABLE);
                }
            }
        });
    }

    /**
     * Marks a worker as unavailable (e.g., after timeout)
     */
    public void markWorkerUnavailable(String workerId) {
        WorkerInfo worker = workers.get(workerId);
        if (worker != null) {
            logger.warn("Marking worker {} as unavailable due to timeout or error", workerId);
            worker.setStatus(WorkerStatus.OFFLINE);
        }
    }

    /**
     * Handles worker heartbeat
     */
    public void handleHeartbeat(BuildProto.HeartbeatRequest request) {
        String workerId = request.getWorkerId();
        WorkerInfo worker = workers.get(workerId);
        
        if (worker != null) {
            worker.setActiveTasks(request.getActiveTasks());
            worker.setLastHeartbeat(System.currentTimeMillis());
            
            // Update status based on heartbeat
            if (request.getStatus() == BuildProto.WorkerStatus.WORKER_STATUS_AVAILABLE && 
                worker.getActiveTasks() == 0) {
                worker.setStatus(WorkerStatus.AVAILABLE);
            } else if (request.getStatus() == BuildProto.WorkerStatus.WORKER_STATUS_BUSY || 
                      worker.getActiveTasks() > 0) {
                worker.setStatus(WorkerStatus.BUSY);
            }
            
            logger.debug("Heartbeat from worker {}: status={}, activeTasks={}", 
                        workerId, worker.getStatus(), worker.getActiveTasks());
        } else {
            logger.warn("Heartbeat from unknown worker: {}", workerId);
        }
    }

    /**
     * Gets all registered workers
     */
    public Collection<WorkerInfo> getAllWorkers() {
        return new ArrayList<>(workers.values());
    }

    /**
     * Gets worker statistics
     */
    public WorkerStats getStats() {
        int totalWorkers = workers.size();
        int availableWorkers = (int) workers.values().stream()
                .filter(w -> w.getStatus() == WorkerStatus.AVAILABLE)
                .count();
        int busyWorkers = (int) workers.values().stream()
                .filter(w -> w.getStatus() == WorkerStatus.BUSY)
                .count();
        int offlineWorkers = (int) workers.values().stream()
                .filter(w -> w.getStatus() == WorkerStatus.OFFLINE)
                .count();
        int totalActiveTasks = workers.values().stream()
                .mapToInt(WorkerInfo::getActiveTasks)
                .sum();
        
        return new WorkerStats(totalWorkers, availableWorkers, busyWorkers, offlineWorkers, totalActiveTasks);
    }

    /**
     * Shuts down the worker manager
     */
    public void shutdown() {
        logger.info("Shutting down WorkerManager");
        
        healthCheckExecutor.shutdown();
        
        // Close all channels
        for (ManagedChannel channel : channels.values()) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        channels.clear();
        stubs.clear();
        workers.clear();
    }

    private void startHealthChecking() {
        healthCheckExecutor.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            long timeout = 60000; // 1 minute timeout
            
            for (WorkerInfo worker : workers.values()) {
                if (worker.getStatus() != WorkerStatus.OFFLINE && 
                    (now - worker.getLastHeartbeat()) > timeout) {
                    
                    logger.warn("Worker {} timed out, marking as offline", worker.getId());
                    worker.setStatus(WorkerStatus.OFFLINE);
                }
            }
        }, 30, 30, TimeUnit.SECONDS); // Check every 30 seconds
    }

    /**
     * Worker information
     */
    public static class WorkerInfo {
        private final String id;
        private final String host;
        private final int port;
        private final int maxConcurrentTasks;
        private final String javaVersion;
        private final Map<String, String> capabilities;
        private final List<WorkerSpecialization> specializations;
        private final Map<String, Object> preferences;
        
        private volatile WorkerStatus status = WorkerStatus.OFFLINE;
        private volatile int activeTasks = 0;
        private volatile long lastHeartbeat = 0;
        private final AtomicLong totalTasksCompleted = new AtomicLong(0);
        private final AtomicLong totalCompileTime = new AtomicLong(0);

        public WorkerInfo(String id, String host, int port, int maxConcurrentTasks, 
                         String javaVersion, Map<String, String> capabilities) {
            this(id, host, port, maxConcurrentTasks, javaVersion, capabilities, 
                 new ArrayList<>(), new HashMap<>());
        }
        
        public WorkerInfo(String id, String host, int port, int maxConcurrentTasks, 
                         String javaVersion, Map<String, String> capabilities,
                         List<WorkerSpecialization> specializations, Map<String, Object> preferences) {
            this.id = id;
            this.host = host;
            this.port = port;
            this.maxConcurrentTasks = maxConcurrentTasks;
            this.javaVersion = javaVersion;
            this.capabilities = new HashMap<>(capabilities);
            this.specializations = new ArrayList<>(specializations);
            this.preferences = new HashMap<>(preferences);
        }

        // Getters
        public String getId() { return id; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public int getMaxConcurrentTasks() { return maxConcurrentTasks; }
        public String getJavaVersion() { return javaVersion; }
        public Map<String, String> getCapabilities() { return new HashMap<>(capabilities); }
        public List<WorkerSpecialization> getSpecializations() { return new ArrayList<>(specializations); }
        public Map<String, Object> getPreferences() { return new HashMap<>(preferences); }
        public WorkerStatus getStatus() { return status; }
        public int getActiveTasks() { return activeTasks; }
        public long getLastHeartbeat() { return lastHeartbeat; }
        public long getTotalTasksCompleted() { return totalTasksCompleted.get(); }
        public long getTotalCompileTime() { return totalCompileTime.get(); }

        // Setters
        public void setStatus(WorkerStatus status) { this.status = status; }
        public void setLastHeartbeat(long lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
        public void setActiveTasks(int activeTasks) { this.activeTasks = activeTasks; }

        public void incrementActiveTasks() { this.activeTasks++; }
        public void decrementActiveTasks() { 
            this.activeTasks = Math.max(0, this.activeTasks - 1); 
        }

        public void recordTaskCompletion(long compileTimeMs) {
            totalTasksCompleted.incrementAndGet();
            totalCompileTime.addAndGet(compileTimeMs);
        }

        @Override
        public String toString() {
            return String.format("Worker{id='%s', address='%s:%d', status=%s, activeTasks=%d/%d}", 
                               id, host, port, status, activeTasks, maxConcurrentTasks);
        }
    }

    /**
     * Worker statistics
     */
    public static class WorkerStats {
        private final int totalWorkers;
        private final int availableWorkers;
        private final int busyWorkers;
        private final int offlineWorkers;
        private final int totalActiveTasks;

        public WorkerStats(int totalWorkers, int availableWorkers, int busyWorkers, 
                          int offlineWorkers, int totalActiveTasks) {
            this.totalWorkers = totalWorkers;
            this.availableWorkers = availableWorkers;
            this.busyWorkers = busyWorkers;
            this.offlineWorkers = offlineWorkers;
            this.totalActiveTasks = totalActiveTasks;
        }

        public int getTotalWorkers() { return totalWorkers; }
        public int getAvailableWorkers() { return availableWorkers; }
        public int getBusyWorkers() { return busyWorkers; }
        public int getOfflineWorkers() { return offlineWorkers; }
        public int getTotalActiveTasks() { return totalActiveTasks; }

        @Override
        public String toString() {
            return String.format("WorkerStats{total=%d, available=%d, busy=%d, offline=%d, activeTasks=%d}",
                               totalWorkers, availableWorkers, busyWorkers, offlineWorkers, totalActiveTasks);
        }
    }

    /**
     * Worker status enum
     */
    public enum WorkerStatus {
        AVAILABLE, BUSY, OFFLINE, ERROR
    }
}
