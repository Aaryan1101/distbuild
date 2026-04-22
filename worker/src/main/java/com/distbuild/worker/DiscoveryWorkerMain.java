package com.distbuild.worker;

import com.distbuild.common.discovery.NetworkDiscovery;
import com.distbuild.worker.WorkerService;
import com.distbuild.common.grpc.BuildServiceGrpc;
import com.distbuild.common.error.DiagnosticError;
import com.distbuild.common.error.ErrorReportingService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Enhanced worker with automatic coordinator discovery
 */
public class DiscoveryWorkerMain {
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryWorkerMain.class);
    
    private WorkerService workerService;
    private ManagedChannel channel;
    private BuildServiceGrpc.BuildServiceBlockingStub coordinatorStub;
    private HttpServer managementServer;
    private AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private String currentWorkerId;
    private ErrorReportingService errorReporter = ErrorReportingService.getInstance();
    
    public static void main(String[] args) {
        DiscoveryWorkerMain main = new DiscoveryWorkerMain();
        main.start(args);
    }
    
    public void start(String[] args) {
        try {
            // Parse arguments
            String workerId = "worker-" + System.currentTimeMillis();
            String coordinatorHost = null;
            int coordinatorPort = 8080;
            int maxTasks = 4;
            int managementPort = 0; // 0 means auto
            boolean enableDiscovery = true;
            
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--worker-id":
                        workerId = args[++i];
                        break;
                    case "--coordinator-host":
                        coordinatorHost = args[++i];
                        enableDiscovery = false;
                        break;
                    case "--coordinator-port":
                        coordinatorPort = Integer.parseInt(args[++i]);
                        break;
                    case "--max-tasks":
                        maxTasks = Integer.parseInt(args[++i]);
                        break;
                    case "--management-port":
                        managementPort = Integer.parseInt(args[++i]);
                        break;
                    case "--no-discovery":
                        enableDiscovery = false;
                        break;
                    case "--help":
                        printHelp();
                        return;
                }
            }
            
            // Auto-calculate management port if not specified
            if (managementPort <= 0) {
                managementPort = 9000 + (int)(Math.random() * 1000);
            }
            
            // Store worker ID for management server
            this.currentWorkerId = workerId;
            
            // Discover coordinator or use manual configuration
            if (enableDiscovery && coordinatorHost == null) {
                logger.info("Discovering coordinator automatically...");
                NetworkDiscovery.CoordinatorInfo info = discoverCoordinator();
                if (info != null) {
                    coordinatorHost = info.getHost();
                    coordinatorPort = info.getPort();
                    logger.info("Discovered coordinator at {}:{}", coordinatorHost, coordinatorPort);
                } else {
                    logger.warn("No coordinator discovered, falling back to localhost");
                    coordinatorHost = "localhost";
                }
            }
            
            // Connect to coordinator
            connectToCoordinator(coordinatorHost, coordinatorPort, workerId, maxTasks, managementPort);
            
        } catch (Exception e) {
            logger.error("Failed to start worker", e);
            System.exit(1);
        }
    }
    
    private NetworkDiscovery.CoordinatorInfo discoverCoordinator() {
        try {
            CompletableFuture<NetworkDiscovery.CoordinatorInfo> discovery = NetworkDiscovery.discoverCoordinator();
            return discovery.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Coordinator discovery failed", e);
            return null;
        }
    }
    
    private void connectToCoordinator(String host, int port, String workerId, int maxTasks, int managementPort) {
        logger.info("Connecting to coordinator at {}:{}", host, port);
        
        try {
            // Start management server first
            startManagementServer(managementPort);
            
            // Create gRPC channel
            channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .build();
            
            // Initialize coordinator stub
            coordinatorStub = BuildServiceGrpc.newBlockingStub(channel);
            
            // Initialize worker service
            workerService = new WorkerService(workerId, maxTasks);
            
            // Register with coordinator
            registerWithCoordinator(workerId, host, port, maxTasks);
            
            // Start heartbeat and registration
            startHeartbeat(workerId, host, port);
            
            logger.info("Worker {} connected to coordinator and ready for tasks", workerId);
            logger.info("Worker management API available at http://localhost:{}/status", managementPort);
            
            // Keep running
            waitForShutdown();
            
        } catch (Exception e) {
            logger.error("Failed to connect to coordinator", e);
            shutdownNow();
            System.exit(1);
        }
    }
    
    private void registerWithCoordinator(String workerId, String host, int port, int maxTasks) {
        try {
            logger.info("Registering worker {} with coordinator at {}:{}", workerId, host, port);
            
            // Create registration request
            com.distbuild.common.grpc.BuildProto.RegisterWorkerRequest request = 
                com.distbuild.common.grpc.BuildProto.RegisterWorkerRequest.newBuilder()
                    .setWorkerId(workerId)
                    .setHost(host)
                    .setPort(port)
                    .setMaxConcurrentTasks(maxTasks)
                    .setJavaVersion(System.getProperty("java.version"))
                    .putCapabilities("os", System.getProperty("os.name"))
                    .putCapabilities("arch", System.getProperty("os.arch"))
                    .build();
            
            // Send registration request
            com.distbuild.common.grpc.BuildProto.RegisterWorkerResponse response = 
                coordinatorStub.withDeadlineAfter(5, TimeUnit.SECONDS).registerWorker(request);
            
            if (response.getSuccess()) {
                logger.info("Successfully registered worker {} with coordinator", workerId);
            } else {
                logger.error("Failed to register worker {}: {}", workerId, response.getMessage());
                throw new RuntimeException("Worker registration failed: " + response.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Failed to register worker {} with coordinator", workerId, e);
            
            // Report worker registration error
            DiagnosticError error = DiagnosticError.workerError(
                workerId,
                "Registration with coordinator",
                e
            );
            error.addContext("coordinatorHost", host);
            error.addContext("coordinatorPort", port);
            error.addContext("maxTasks", maxTasks);
            error.addContext("javaVersion", System.getProperty("java.version"));
            error.addContext("os", System.getProperty("os.name"));
            error.addContext("arch", System.getProperty("os.arch"));
            
            errorReporter.reportError(error);
            
            throw new RuntimeException("Worker registration failed", e);
        }
    }
    
    private void startHeartbeat(String workerId, String host, int port) {
        CompletableFuture.runAsync(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Send heartbeat to coordinator
                    logger.debug("Sending heartbeat from {}", workerId);
                    
                    com.distbuild.common.grpc.BuildProto.HeartbeatRequest heartbeat = 
                        com.distbuild.common.grpc.BuildProto.HeartbeatRequest.newBuilder()
                            .setWorkerId(workerId)
                            .setStatus(com.distbuild.common.grpc.BuildProto.WorkerStatus.WORKER_STATUS_AVAILABLE)
                            .setActiveTasks((int)(workerService != null ? workerService.getActiveTaskCount() : 0))
                            .build();
                    
                    coordinatorStub.withDeadlineAfter(5, TimeUnit.SECONDS).heartbeat(heartbeat);
                    
                    Thread.sleep(10000); // 10 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Heartbeat failed for {}", workerId, e);
                    
                    // Report heartbeat error (but don't overwhelm with too many)
                    if (e instanceof java.util.concurrent.TimeoutException || 
                        e.getMessage() != null && e.getMessage().contains("timeout")) {
                        DiagnosticError error = DiagnosticError.networkError(
                            "heartbeat",
                            "coordinator",
                            e
                        );
                        error.addContext("workerId", workerId);
                        error.addContext("coordinatorHost", host);
                        error.addContext("coordinatorPort", port);
                        error.addContext("activeTasks", workerService != null ? workerService.getActiveTaskCount() : 0);
                        
                        errorReporter.reportError(error);
                    }
                }
            }
        });
    }
    
    private void startManagementServer(int managementPort) throws IOException {
        managementServer = HttpServer.create(new InetSocketAddress(managementPort), 0);
        managementServer.createContext("/status", this::handleStatus);
        managementServer.createContext("/shutdown", this::handleShutdown);
        managementServer.createContext("/health", this::handleHealth);
        managementServer.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        managementServer.start();
        logger.info("Worker management HTTP server started on port {}", managementPort);
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String json = buildStatusJson();
        byte[] body = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String json = "{\"status\":\"healthy\",\"workerId\":\"" + currentWorkerId + "\"}";
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

        isShuttingDown.set(true);
        
        byte[] body = ("{\"message\":\"shutdown initiated\",\"workerId\":\"" + currentWorkerId + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();

        // Start graceful shutdown in background
        CompletableFuture.runAsync(this::gracefulShutdown);
    }

    private String buildStatusJson() {
        long activeTasks = workerService != null ? workerService.getActiveTaskCount() : 0;
        int maxTasks = workerService != null ? workerService.getMaxConcurrentTasks() : 0;
        long totalTasks = workerService != null ? workerService.getTotalTasksProcessed() : 0;
        long successfulTasks = workerService != null ? workerService.getSuccessfulTasks() : 0;
        long failedTasks = workerService != null ? workerService.getFailedTasks() : 0;
        
        return String.format(
            "{\"workerId\":\"%s\",\"status\":\"%s\",\"activeTasks\":%d,\"maxTasks\":%d,\"totalTasks\":%d,\"successfulTasks\":%d,\"failedTasks\":%d,\"successRate\":%.2f,\"isShuttingDown\":%s}",
            escapeJson(currentWorkerId),
            isShuttingDown.get() ? "SHUTTING_DOWN" : "RUNNING",
            activeTasks,
            maxTasks,
            totalTasks,
            successfulTasks,
            failedTasks,
            totalTasks > 0 ? (double) successfulTasks / totalTasks * 100 : 0.0,
            isShuttingDown.get()
        );
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void gracefulShutdown() {
        logger.info("Starting graceful shutdown for worker {}", currentWorkerId);
        
        try {
            // Wait for active tasks to complete (max 30 seconds)
            int waitTime = 0;
            while (workerService != null && workerService.getActiveTaskCount() > 0 && waitTime < 30000) {
                logger.info("Waiting for {} active tasks to complete...", workerService.getActiveTaskCount());
                Thread.sleep(1000);
                waitTime += 1000;
            }
            
            if (workerService != null && workerService.getActiveTaskCount() > 0) {
                logger.warn("Forcing shutdown with {} active tasks remaining", workerService.getActiveTaskCount());
            } else {
                logger.info("All tasks completed, shutting down gracefully");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Graceful shutdown interrupted");
        } finally {
            shutdownNow();
            System.exit(0);
        }
    }
    
    private void waitForShutdown() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down worker...");
            try {
                if (channel != null) {
                    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                logger.error("Error during shutdown", e);
            }
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
            if (channel != null) {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            }
            if (workerService != null) {
                workerService.shutdown();
            }
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
    }
    
    private void printHelp() {
        System.out.println("Enhanced Build Worker with Device Discovery");
        System.out.println("Usage: java -jar worker.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --worker-id <id>              Worker identifier (default: auto-generated)");
        System.out.println("  --coordinator-host <host>     Coordinator host (disables auto-discovery)");
        System.out.println("  --coordinator-port <port>     Coordinator port (default: 8080)");
        System.out.println("  --max-tasks <num>             Maximum concurrent tasks (default: 4)");
        System.out.println("  --management-port <port>       Worker management HTTP port (default: auto)");
        System.out.println("  --no-discovery                Disable automatic coordinator discovery");
        System.out.println("  --help                        Show this help message");
        System.out.println();
        System.out.println("Discovery Features:");
        System.out.println("  - Automatic coordinator discovery on local network");
        System.out.println("  - Zero-configuration connection");
        System.out.println("  - Fallback to manual configuration");
        System.out.println();
        System.out.println("Usage Examples:");
        System.out.println("  # Auto-discover coordinator:");
        System.out.println("  java -jar worker.jar");
        System.out.println();
        System.out.println("  # Manual connection:");
        System.out.println("  java -jar worker.jar --coordinator-host=192.168.1.100");
        System.out.println();
        System.out.println("  # Custom worker ID:");
        System.out.println("  java -jar worker.jar --worker-id=build-worker-1");
    }
}
