package com.distbuild.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main entry point for the build worker
 */
public class WorkerMain {
    private static final Logger logger = LoggerFactory.getLogger(WorkerMain.class);
    
    private WorkerService workerService;
    private ScheduledExecutorService heartbeatExecutor;

    public static void main(String[] args) {
        WorkerMain worker = new WorkerMain();
        worker.run(args);
    }

    public void run(String[] args) {
        try {
            // Parse command line arguments
            WorkerConfig config = parseArgs(args);
            
            logger.info("Starting Build Worker with config: {}", config);
            
            // Initialize components
            initializeComponents(config);
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
            
            logger.info("Build Worker {} started successfully", config.getWorkerId());
            
            // Keep the worker running
            keepRunning();
            
        } catch (Exception e) {
            logger.error("Failed to start Build Worker", e);
            System.exit(1);
        }
    }

    private void initializeComponents(WorkerConfig config) throws Exception {
        // Initialize worker service
        workerService = new WorkerService(config.getWorkerId());
        
        // Initialize heartbeat scheduler
        heartbeatExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "Worker-Heartbeat");
            t.setDaemon(true);
            return t;
        });
        
        // Start heartbeat (simplified - in real implementation would send to coordinator)
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            logger.debug("Worker {} heartbeat", config.getWorkerId());
        }, 10, 30, TimeUnit.SECONDS);
        
        logger.info("Worker components initialized successfully");
    }

    private void keepRunning() {
        logger.info("Worker is running. Press Ctrl+C to stop.");
        
        // Keep the main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            logger.info("Worker interrupted, shutting down");
            Thread.currentThread().interrupt();
        }
    }

    private void shutdown() {
        logger.info("Shutting down Build Worker");
        
        try {
            if (heartbeatExecutor != null) {
                heartbeatExecutor.shutdown();
                if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    heartbeatExecutor.shutdownNow();
                }
            }
            
            if (workerService != null) {
                workerService.shutdown();
            }
            
            logger.info("Build Worker shutdown complete");
            
        } catch (InterruptedException e) {
            logger.error("Shutdown interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
    }

    private WorkerConfig parseArgs(String[] args) {
        String workerId = "worker-" + System.currentTimeMillis();
        String coordinatorHost = "localhost";
        int coordinatorPort = 8080;
        int maxConcurrentTasks = 4;
        String javaVersion = "17";
        
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--worker-id":
                    if (i + 1 < args.length) {
                        workerId = args[i + 1];
                        i++;
                    }
                    break;
                case "--coordinator-host":
                    if (i + 1 < args.length) {
                        coordinatorHost = args[i + 1];
                        i++;
                    }
                    break;
                case "--coordinator-port":
                    if (i + 1 < args.length) {
                        coordinatorPort = Integer.parseInt(args[i + 1]);
                        i++;
                    }
                    break;
                case "--max-tasks":
                    if (i + 1 < args.length) {
                        maxConcurrentTasks = Integer.parseInt(args[i + 1]);
                        i++;
                    }
                    break;
                case "--java-version":
                    if (i + 1 < args.length) {
                        javaVersion = args[i + 1];
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
        
        return new WorkerConfig(workerId, coordinatorHost, coordinatorPort, maxConcurrentTasks, javaVersion);
    }

    private void printUsage() {
        System.out.println("Build Worker");
        System.out.println("Usage: java -jar worker.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --worker-id <id>              Worker identifier (default: auto-generated)");
        System.out.println("  --coordinator-host <host>     Coordinator host (default: localhost)");
        System.out.println("  --coordinator-port <port>     Coordinator port (default: 8080)");
        System.out.println("  --max-tasks <num>             Maximum concurrent tasks (default: 4)");
        System.out.println("  --java-version <version>      Java version (default: 17)");
        System.out.println("  --help                        Show this help message");
    }

    /**
     * Configuration for the worker
     */
    public static class WorkerConfig {
        private final String workerId;
        private final String coordinatorHost;
        private final int coordinatorPort;
        private final int maxConcurrentTasks;
        private final String javaVersion;

        public WorkerConfig(String workerId, String coordinatorHost, int coordinatorPort,
                          int maxConcurrentTasks, String javaVersion) {
            this.workerId = workerId;
            this.coordinatorHost = coordinatorHost;
            this.coordinatorPort = coordinatorPort;
            this.maxConcurrentTasks = maxConcurrentTasks;
            this.javaVersion = javaVersion;
        }

        public String getWorkerId() { return workerId; }
        public String getCoordinatorHost() { return coordinatorHost; }
        public int getCoordinatorPort() { return coordinatorPort; }
        public int getMaxConcurrentTasks() { return maxConcurrentTasks; }
        public String getJavaVersion() { return javaVersion; }

        @Override
        public String toString() {
            return "WorkerConfig{" +
                    "workerId='" + workerId + '\'' +
                    ", coordinatorHost='" + coordinatorHost + '\'' +
                    ", coordinatorPort=" + coordinatorPort +
                    ", maxConcurrentTasks=" + maxConcurrentTasks +
                    ", javaVersion='" + javaVersion + '\'' +
                    '}';
        }
    }
}
