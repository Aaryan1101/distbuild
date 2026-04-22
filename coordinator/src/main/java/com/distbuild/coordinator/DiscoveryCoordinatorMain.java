package com.distbuild.coordinator;

import com.distbuild.common.discovery.NetworkDiscovery;
import com.distbuild.coordinator.SimpleCoordinator;
import com.distbuild.coordinator.WorkerManager;
import com.distbuild.common.cache.LocalDiskCache;
import com.distbuild.coordinator.CoordinatorGrpcService;
import com.distbuild.coordinator.WorkerConfig;
import com.distbuild.common.error.ErrorReportingService;
import com.distbuild.coordinator.android.AndroidJarCache;
import com.distbuild.coordinator.android.AndroidBuildService;
import com.distbuild.coordinator.android.AndroidLocalPipeline;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Enhanced coordinator with automatic device discovery
 */
public class DiscoveryCoordinatorMain {
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryCoordinatorMain.class);
    
    private Server grpcServer;
    private HttpServer managementServer;
    private NetworkDiscovery discovery;
    private BuildCoordinator coordinator;
    private WorkerManager workerManager;
    private LocalDiskCache cache;
    private int grpcPort;
    private ErrorReportingService errorReporter = ErrorReportingService.getInstance();
    private AndroidJarCache androidJarCache;
    private AndroidBuildService androidBuildService;
    private AndroidLocalPipeline androidLocalPipeline;
    
    public static void main(String[] args) {
        DiscoveryCoordinatorMain main = new DiscoveryCoordinatorMain();
        main.start(args);
    }
    
    public void start(String[] args) {
        try {
            // Parse arguments
            int port = 8080;
            int managementPort = 0; // 0 means auto (grpc + 1)
            String cacheDir = "./coordinator-cache";
            boolean enableDiscovery = true;
            WorkerManager.LoadBalancingStrategy loadBalancingStrategy = WorkerManager.LoadBalancingStrategy.LEAST_LOADED;
            String workerConfigFile = null;
            
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--port":
                        port = Integer.parseInt(args[++i]);
                        break;
                    case "--cache-dir":
                        cacheDir = args[++i];
                        break;
                    case "--management-port":
                        managementPort = Integer.parseInt(args[++i]);
                        break;
                    case "--load-balancing":
                        String strategy = args[++i];
                        try {
                            loadBalancingStrategy = WorkerManager.LoadBalancingStrategy.valueOf(strategy.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            System.err.println("Invalid load balancing strategy: " + strategy);
                            System.err.println("Available strategies: " + java.util.Arrays.toString(WorkerManager.LoadBalancingStrategy.values()));
                            System.exit(1);
                        }
                        break;
                    case "--worker-config":
                        workerConfigFile = args[++i];
                        break;
                    case "--no-discovery":
                        enableDiscovery = false;
                        break;
                    case "--help":
                        printHelp();
                        return;
                }
            }
            if (managementPort <= 0) {
                managementPort = port + 1;
            }
            this.grpcPort = port;
            
            // Load worker configuration first
            WorkerConfig.WorkersConfig workersConfig = new WorkerConfig.WorkersConfig();
            if (workerConfigFile != null) {
                logger.info("Worker configuration file specified: {}", workerConfigFile);
                workersConfig.maxTasks = 4;
            } else {
                workersConfig.maxTasks = 4; // Default
            }
            workerManager = new WorkerManager(workersConfig);
            workerManager.setLoadBalancingStrategy(loadBalancingStrategy);
            
            // Initialize coordinator
            cache = new LocalDiskCache(Paths.get(cacheDir));
            coordinator = new BuildCoordinator(cache, workerManager);
            
            // Initialize Android support
            initializeAndroidSupport();
            
            // Start gRPC server
            startGrpcServer(port, workerManager);
            startManagementServer(managementPort);
            
            // Start discovery broadcasting
            if (enableDiscovery) {
                startDiscovery(port);
            }
            
            logger.info("Enhanced coordinator started on port {} with discovery: {}", port, enableDiscovery);
            logger.info("Management API available at http://localhost:{}/health", managementPort);
            logger.info("Workers can now discover this coordinator automatically!");
            
            // Keep running
            waitForShutdown();
            
        } catch (Exception e) {
            logger.error("Failed to start coordinator", e);
            System.exit(1);
        }
    }
    
    private void startGrpcServer(int port, WorkerManager workerManager) throws IOException {
        grpcServer = ServerBuilder.forPort(port)
            .addService(new CoordinatorGrpcService(workerManager))
            .build()
            .start();
        
        logger.info("gRPC server started on port {}", port);
    }
    
    private void initializeAndroidSupport() {
        try {
            // Try to detect Android SDK from environment
            String androidHome = System.getenv("ANDROID_HOME");
            if (androidHome == null) {
                // Try common Android SDK locations
                String userHome = System.getProperty("user.home");
                if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                    androidHome = userHome + "\\AppData\\Local\\Android\\Sdk";
                } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                    androidHome = userHome + "/Library/Android/sdk";
                } else {
                    androidHome = userHome + "/Android/Sdk";
                }
            }
            
            Path androidSdkPath = Paths.get(androidHome);
            if (Files.exists(androidSdkPath)) {
                logger.info("Android SDK detected at: {}", androidSdkPath);
                
                // Initialize Android components
                androidJarCache = new AndroidJarCache(coordinator, cache, androidSdkPath);
                androidBuildService = new AndroidBuildService(coordinator, androidJarCache);
                androidLocalPipeline = new AndroidLocalPipeline(androidSdkPath);
                
                // Preload common android.jar versions
                androidJarCache.preloadCommonAndroidJars();
                
                logger.info("Android support initialized successfully");
            } else {
                logger.warn("Android SDK not found at {}. Android builds will not be available.", androidSdkPath);
            }
            
        } catch (Exception e) {
            logger.warn("Failed to initialize Android support: {}", e.getMessage());
            logger.debug("Android initialization error", e);
        }
    }
    
    private void startDiscovery(int port) {
        discovery = new NetworkDiscovery(port);
        discovery.startBroadcasting();
        logger.info("Network discovery started - broadcasting coordinator availability");
    }

    private void startManagementServer(int managementPort) throws IOException {
        managementServer = HttpServer.create(new InetSocketAddress(managementPort), 0);
        managementServer.createContext("/health", this::handleHealth);
        managementServer.createContext("/shutdown", this::handleShutdown);
        managementServer.createContext("/errors", this::handleErrors);
        managementServer.createContext("/diagnostics", this::handleDiagnostics);
        managementServer.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        managementServer.start();
        logger.info("Management HTTP server started on port {}", managementPort);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        Collection<WorkerManager.WorkerInfo> workers = workerManager != null ? workerManager.getAllWorkers() : List.of();
        WorkerManager.WorkerStats stats = workerManager != null
            ? workerManager.getStats()
            : new WorkerManager.WorkerStats(0, 0, 0, 0, 0);
        String json = buildHealthJson(workers, stats);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void handleShutdown(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        byte[] body = "{\"message\":\"shutdown initiated\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();

        new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            shutdownNow();
            System.exit(0);
        }, "CoordinatorShutdown").start();
    }

    private void handleErrors(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        
        String limitParam = exchange.getRequestURI().getQuery();
        int limit = 50; // Default limit
        if (limitParam != null && limitParam.contains("limit=")) {
            try {
                limit = Integer.parseInt(limitParam.split("limit=")[1].split("&")[0]);
                limit = Math.min(limit, 200); // Cap at 200
            } catch (Exception e) {
                // Use default
            }
        }
        
        var recentErrors = errorReporter.getRecentErrors(limit);
        String json = buildErrorsJson(recentErrors);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
    
    private void handleDiagnostics(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        
        var statistics = errorReporter.getStatistics();
        var trends = errorReporter.getTrends();
        var patterns = errorReporter.analyzePatterns();
        var health = errorReporter.getSystemHealth();
        
        String json = buildDiagnosticsJson(statistics, trends, patterns, health);
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
    
    private String buildErrorsJson(List<?> errors) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{");
            sb.append("\"errorId\":\"").append(escape(errors.get(i).toString().split("errorId=")[1].split(",")[0])).append("\",");
            sb.append("\"timestamp\":\"").append(java.time.Instant.now().toString()).append("\",");
            sb.append("\"message\":\"").append(escape(errors.get(i).toString().split("message=")[1].split(",")[0])).append("\",");
            sb.append("\"severity\":\"").append(escape(errors.get(i).toString().split("severity=")[1].split(",")[0])).append("\",");
            sb.append("\"category\":\"").append(escape(errors.get(i).toString().split("category=")[1].split(",")[0])).append("\",");
            sb.append("\"component\":\"").append(escape(errors.get(i).toString().split("component=")[1].split(",")[0])).append("\"");
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }
    
    private String buildDiagnosticsJson(Object statistics, Object trends, Object patterns, Object health) {
        return String.format(
            "{\"statistics\":%s,\"trends\":%s,\"patterns\":%s,\"health\":%s,\"timestamp\":\"%s\"}",
            statistics.toString(),
            trends.toString(),
            patterns.toString(),
            health.toString(),
            java.time.Instant.now().toString()
        );
    }
    
    private String buildHealthJson(Collection<WorkerManager.WorkerInfo> workers, WorkerManager.WorkerStats stats) {
        List<String> entries = new ArrayList<>();
        for (WorkerManager.WorkerInfo worker : workers) {
            entries.add(String.format(
                "{\"id\":\"%s\",\"host\":\"%s\",\"port\":%d,\"status\":\"%s\",\"activeTasks\":%d,\"maxTasks\":%d}",
                escape(worker.getId()),
                escape(worker.getHost()),
                worker.getPort(),
                worker.getStatus().name(),
                worker.getActiveTasks(),
                worker.getMaxConcurrentTasks()
            ));
        }

        String cacheStats = cache != null ? cache.getStats().toString() : "unavailable";
        return String.format(
            "{\"status\":\"healthy\",\"grpcPort\":%d,\"workers\":%s,\"workerStats\":{\"total\":%d,\"available\":%d,\"busy\":%d,\"offline\":%d,\"activeTasks\":%d},\"cacheStats\":\"%s\"}",
            grpcPort,
            "[" + String.join(",", entries) + "]",
            stats.getTotalWorkers(),
            stats.getAvailableWorkers(),
            stats.getBusyWorkers(),
            stats.getOfflineWorkers(),
            stats.getTotalActiveTasks(),
            escape(cacheStats)
        );
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    
    private void waitForShutdown() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down coordinator...");
            shutdownNow();
        }));
        
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    

    private void shutdownNow() {
        try {
            if (managementServer != null) {
                managementServer.stop(0);
            }
            if (grpcServer != null) {
                grpcServer.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            }
            if (discovery != null) {
                discovery.stopBroadcasting();
            }
            if (coordinator != null) {
                coordinator.shutdown();
            }
            if (workerManager != null) {
                workerManager.shutdown();
            }
            if (cache != null) {
                cache.close();
            }
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
    }
    
    private void printHelp() {
        System.out.println("Enhanced Build Coordinator with Device Discovery");
        System.out.println("Usage: java -jar coordinator.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --port <port>                 Coordinator gRPC port (default: 8080)");
        System.out.println("  --cache-dir <dir>              Cache directory (default: ./coordinator-cache)");
        System.out.println("  --management-port <port>       Management HTTP port (default: port+1)");
        System.out.println("  --load-balancing <strategy>    Load balancing strategy (default: LEAST_LOADED)");
        System.out.println("                                 Available: ROUND_ROBIN, LEAST_LOADED, WEIGHTED_RESPONSE_TIME, CAPABILITY_BASED");
        System.out.println("  --worker-config <file>        Worker specialization configuration file");
        System.out.println("  --no-discovery                 Disable automatic worker discovery");
        System.out.println("  --help                         Show this help message");
        System.out.println();
        System.out.println("Load Balancing Strategies:");
        System.out.println("  ROUND_ROBIN                  Distribute tasks evenly in circular order");
        System.out.println("  LEAST_LOADED                 Always select worker with lowest load ratio");
        System.out.println("  WEIGHTED_RESPONSE_TIME        Balance load and historical response times");
        System.out.println("  CAPABILITY_BASED              Match tasks to workers based on capabilities");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Start with least-loaded strategy:");
        System.out.println("  java -jar coordinator.jar --load-balancing LEAST_LOADED");
        System.out.println();
        System.out.println("  # Start with capability-based matching:");
        System.out.println("  java -jar coordinator.jar --load-balancing CAPABILITY_BASED");
    }
}
