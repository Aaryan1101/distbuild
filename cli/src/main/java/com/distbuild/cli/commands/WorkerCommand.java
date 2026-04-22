package com.distbuild.cli.commands;

import com.distbuild.cli.config.DistBuildConfig;
import com.distbuild.worker.DiscoveryWorkerMain;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;

/**
 * Worker management commands
 */
@Command(
    name = "worker",
    description = "Manage worker agent on this machine.",
    subcommands = {
        WorkerCommand.Join.class,
        WorkerCommand.Leave.class,
        WorkerCommand.Status.class
    }
)
public class WorkerCommand implements Runnable {
    
    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
    
    @Command(
        name = "join",
        description = "Start worker and register with coordinator"
    )
    public static class Join implements Runnable {
        
        @Option(
            names = {"--config", "-c"},
            description = "Path to distbuild.yaml configuration file",
            defaultValue = "distbuild.yaml"
        )
        File configFile;
        
        @Option(
            names = {"--coordinator"},
            description = "Coordinator host (overrides config, disables auto-discovery)"
        )
        String coordinatorHost;
        
        @Option(names = "--coordinator-port", description = "Coordinator port (overrides config)")
        Integer coordinatorPort;
        
        @Option(names = "--worker-id", description = "Worker identifier")
        String workerId;
        
        @Option(names = "--max-tasks", description = "Maximum concurrent tasks (overrides config)")
        Integer maxTasks;
        
        @Option(names = "--management-port", description = "Worker management HTTP port (default: auto)")
        Integer managementPort;
        
        @Option(names = "--no-discovery", description = "Disable automatic coordinator discovery")
        Boolean noDiscovery;
        
        @Override
        public void run() {
            try {
                // Load configuration
                DistBuildConfig config = DistBuildConfig.load(configFile);
                
                // Generate worker ID if not provided
                if (workerId == null) {
                    workerId = "worker-" + System.currentTimeMillis();
                }
                
                // Apply CLI overrides
                if (coordinatorHost != null) {
                    // Manual coordinator host disables discovery
                    config.coordinator.discoveryEnabled = false;
                }
                if (coordinatorPort != null) {
                    config.coordinator.port = coordinatorPort;
                }
                if (maxTasks != null) {
                    config.workers.maxTasks = maxTasks;
                }
                if (noDiscovery != null) {
                    config.coordinator.discoveryEnabled = !noDiscovery;
                }
                config.validate();
                
                System.out.println("Starting worker...");
                System.out.println("  Worker ID: " + workerId);
                System.out.println("  Max tasks: " + config.workers.maxTasks);
                System.out.println("  Discovery: " + (config.coordinator.discoveryEnabled ? "enabled" : "disabled"));
                if (!config.coordinator.discoveryEnabled || coordinatorHost != null) {
                    String host = coordinatorHost != null ? coordinatorHost : config.coordinator.host;
                    System.out.println("  Coordinator: " + host + ":" + config.coordinator.port);
                }
                
                // Build arguments for existing DiscoveryWorkerMain
                String[] args = buildWorkerArgs(config, workerId, coordinatorHost, managementPort);
                
                // Start worker using existing main class
                DiscoveryWorkerMain workerMain = new DiscoveryWorkerMain();
                workerMain.start(args);
                
            } catch (Exception e) {
                System.err.println("Failed to start worker: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        }
        
        private String[] buildWorkerArgs(DistBuildConfig config, String workerId, String manualHost, Integer managementPort) {
            java.util.List<String> args = new java.util.ArrayList<>();
            
            args.add("--worker-id");
            args.add(workerId);
            
            args.add("--max-tasks");
            args.add(String.valueOf(config.workers.maxTasks));
            
            if (managementPort != null) {
                args.add("--management-port");
                args.add(String.valueOf(managementPort));
            }
            
            if (!config.coordinator.discoveryEnabled || manualHost != null) {
                String host = manualHost != null ? manualHost : config.coordinator.host;
                args.add("--coordinator-host");
                args.add(host);
                
                args.add("--coordinator-port");
                args.add(String.valueOf(config.coordinator.port));
            }
            
            return args.toArray(new String[0]);
        }
    }
    
    @Command(
        name = "leave",
        description = "Drain tasks and deregister this worker"
    )
    public static class Leave implements Runnable {
        
        @Option(names = "--port", description = "Worker management port", defaultValue = "0")
        int managementPort;
        
        @Override
        public void run() {
            try {
                // Find worker management port
                if (managementPort == 0) {
                    for (int port = 9000; port <= 9999; port++) {
                        if (checkWorkerHealth("localhost", port)) {
                            managementPort = port;
                            break;
                        }
                    }
                    if (managementPort == 0) {
                        System.err.println("No running worker found on ports 9000-9999");
                        System.err.println("Make sure a worker is running or specify --port");
                        System.exit(1);
                    }
                } else {
                    if (!checkWorkerHealth("localhost", managementPort)) {
                        System.err.println("No worker found on port " + managementPort);
                        System.exit(1);
                    }
                }
                
                // Send graceful shutdown signal
                sendGracefulShutdown("localhost", managementPort);
                
            } catch (Exception e) {
                System.err.println("Failed to leave worker pool: " + e.getMessage());
                System.exit(1);
            }
        }
        
        private boolean checkWorkerHealth(String host, int port) {
            try {
                java.net.URL url = new java.net.URL("http://" + host + ":" + port + "/health");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                return conn.getResponseCode() == 200;
            } catch (Exception e) {
                return false;
            }
        }
        
        private void sendGracefulShutdown(String host, int port) {
            try {
                System.out.println("Sending graceful shutdown signal to worker at " + host + ":" + port);
                
                java.net.URL url = new java.net.URL("http://" + host + ":" + port + "/shutdown");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                
                // Send empty JSON body
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = "{}".getBytes("utf-8");
                    os.write(input, 0, input.length);
                }
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    System.out.println("✓ Graceful shutdown signal sent successfully");
                    System.out.println("Worker will finish active tasks before shutting down");
                } else {
                    System.err.println("✗ Failed to send shutdown signal (HTTP " + responseCode + ")");
                }
                
            } catch (Exception e) {
                System.err.println("Failed to send shutdown signal: " + e.getMessage());
            }
        }
    }
    
    @Command(
        name = "status",
        description = "Show this worker's status"
    )
    public static class Status implements Runnable {
        
        @Option(names = "--port", description = "Worker management port", defaultValue = "0")
        int managementPort;
        
        @Override
        public void run() {
            try {
                // Find worker management port (try common range)
                if (managementPort == 0) {
                    for (int port = 9000; port <= 9999; port++) {
                        if (checkWorkerStatus("localhost", port)) {
                            managementPort = port;
                            break;
                        }
                    }
                    if (managementPort == 0) {
                        System.err.println("No running worker found on ports 9000-9999");
                        System.err.println("Make sure a worker is running or specify --port");
                        System.exit(1);
                    }
                } else {
                    if (!checkWorkerStatus("localhost", managementPort)) {
                        System.err.println("No worker found on port " + managementPort);
                        System.exit(1);
                    }
                }
                
                // Display detailed worker status
                displayWorkerStatus("localhost", managementPort);
                
            } catch (Exception e) {
                System.err.println("Failed to get worker status: " + e.getMessage());
                System.exit(1);
            }
        }
        
        private boolean checkWorkerStatus(String host, int port) {
            try {
                java.net.URL url = new java.net.URL("http://" + host + ":" + port + "/health");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                return conn.getResponseCode() == 200;
            } catch (Exception e) {
                return false;
            }
        }
        
        private void displayWorkerStatus(String host, int port) {
            try {
                java.net.URL url = new java.net.URL("http://" + host + ":" + port + "/status");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                
                if (conn.getResponseCode() == 200) {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream()))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        
                        // Parse and display JSON response
                        String json = response.toString();
                        System.out.println("=== Worker Status ===");
                        System.out.println("Management URL: http://" + host + ":" + port);
                        System.out.println();
                        
                        // Simple JSON parsing for display
                        if (json.contains("\"workerId\"")) {
                            String workerId = extractJsonValue(json, "workerId");
                            System.out.println("Worker ID: " + workerId);
                        }
                        if (json.contains("\"status\"")) {
                            String status = extractJsonValue(json, "status");
                            System.out.println("Status: " + status);
                        }
                        if (json.contains("\"activeTasks\"")) {
                            String activeTasks = extractJsonValue(json, "activeTasks");
                            System.out.println("Active Tasks: " + activeTasks);
                        }
                        if (json.contains("\"maxTasks\"")) {
                            String maxTasks = extractJsonValue(json, "maxTasks");
                            System.out.println("Max Tasks: " + maxTasks);
                        }
                        if (json.contains("\"totalTasks\"")) {
                            String totalTasks = extractJsonValue(json, "totalTasks");
                            System.out.println("Total Tasks: " + totalTasks);
                        }
                        if (json.contains("\"successRate\"")) {
                            String successRate = extractJsonValue(json, "successRate");
                            System.out.println("Success Rate: " + successRate + "%");
                        }
                    }
                } else {
                    System.err.println("Worker returned HTTP " + conn.getResponseCode());
                }
            } catch (Exception e) {
                System.err.println("Failed to get worker status: " + e.getMessage());
            }
        }
        
        private String extractJsonValue(String json, String key) {
            String pattern = "\"" + key + "\":\"";
            int start = json.indexOf(pattern);
            if (start == -1) return "N/A";
            start += pattern.length();
            int end = json.indexOf("\"", start);
            if (end == -1) return "N/A";
            return json.substring(start, end);
        }
    }
}
