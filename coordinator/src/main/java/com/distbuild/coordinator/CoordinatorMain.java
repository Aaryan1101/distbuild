package com.distbuild.coordinator;

import com.distbuild.common.cache.BuildCache;
import com.distbuild.common.cache.LocalDiskCache;
import com.distbuild.common.grpc.BuildServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Main entry point for the build coordinator
 */
public class CoordinatorMain {
    private static final Logger logger = LoggerFactory.getLogger(CoordinatorMain.class);
    
    private Server server;
    private BuildCoordinator buildCoordinator;
    private WorkerManager workerManager;

    public static void main(String[] args) {
        CoordinatorMain coordinator = new CoordinatorMain();
        coordinator.run(args);
    }

    public void run(String[] args) {
        try {
            // Parse command line arguments
            CoordinatorConfig config = parseArgs(args);
            
            logger.info("Starting Build Coordinator with config: {}", config);
            
            // Initialize components
            initializeComponents(config);
            
            // Start gRPC server
            startGrpcServer(config.getPort());
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
            
            logger.info("Build Coordinator started successfully on port {}", config.getPort());
            
            // Wait for termination
            server.awaitTermination();
            
        } catch (Exception e) {
            logger.error("Failed to start Build Coordinator", e);
            System.exit(1);
        }
    }

    private void initializeComponents(CoordinatorConfig config) throws Exception {
        // Initialize cache
        BuildCache cache = new LocalDiskCache(Paths.get(config.getCacheDir()));
        
        // Initialize worker manager
        workerManager = new WorkerManager();
        
        // Initialize build coordinator
        buildCoordinator = new BuildCoordinator(cache, workerManager);
        
        logger.info("Components initialized successfully");
    }

    private void startGrpcServer(int port) throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new CoordinatorGrpcService(workerManager))
                .build();
        
        server.start();
        logger.info("gRPC server started on port {}", port);
    }

    private void shutdown() {
        logger.info("Shutting down Build Coordinator");
        
        try {
            if (server != null) {
                server.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            }
            
            if (buildCoordinator != null) {
                buildCoordinator.shutdown();
            }
            
            if (workerManager != null) {
                workerManager.shutdown();
            }
            
            logger.info("Build Coordinator shutdown complete");
            
        } catch (InterruptedException e) {
            logger.error("Shutdown interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
    }

    private CoordinatorConfig parseArgs(String[] args) {
        int port = 8080;
        String cacheDir = "./coordinator-cache";
        String redisUrl = "redis://localhost:6379";
        
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port":
                    if (i + 1 < args.length) {
                        port = Integer.parseInt(args[i + 1]);
                        i++;
                    }
                    break;
                case "--cache-dir":
                    if (i + 1 < args.length) {
                        cacheDir = args[i + 1];
                        i++;
                    }
                    break;
                case "--redis":
                    if (i + 1 < args.length) {
                        redisUrl = args[i + 1];
                        i++;
                    }
                    break;
                case "--help":
                    printUsage();
                    System.exit(0);
                    break;
                default:
                    if (args[i].startsWith("-")) {
                        System.err.println("Unknown option: " + args[i]);
                        printUsage();
                        System.exit(1);
                    }
            }
        }
        
        return new CoordinatorConfig(port, cacheDir, redisUrl);
    }

    private void printUsage() {
        System.out.println("Build Coordinator");
        System.out.println("Usage: java -jar coordinator.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --port <port>           Server port (default: 8080)");
        System.out.println("  --cache-dir <dir>       Cache directory (default: ./coordinator-cache)");
        System.out.println("  --redis <url>           Redis URL (default: redis://localhost:6379)");
        System.out.println("  --help                  Show this help message");
    }

    /**
     * Configuration for the coordinator
     */
    public static class CoordinatorConfig {
        private final int port;
        private final String cacheDir;
        private final String redisUrl;

        public CoordinatorConfig(int port, String cacheDir, String redisUrl) {
            this.port = port;
            this.cacheDir = cacheDir;
            this.redisUrl = redisUrl;
        }

        public int getPort() { return port; }
        public String getCacheDir() { return cacheDir; }
        public String getRedisUrl() { return redisUrl; }

        @Override
        public String toString() {
            return "CoordinatorConfig{" +
                    "port=" + port +
                    ", cacheDir='" + cacheDir + '\'' +
                    ", redisUrl='" + redisUrl + '\'' +
                    '}';
        }
    }
}
