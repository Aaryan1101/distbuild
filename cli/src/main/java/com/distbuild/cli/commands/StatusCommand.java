package com.distbuild.cli.commands;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Global status command
 */
@Command(
    name = "status",
    description = "Show all connected workers and system status"
)
public class StatusCommand implements Runnable {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final OkHttpClient CLIENT = new OkHttpClient();
    
    @Option(names = "--host", defaultValue = "localhost", description = "Coordinator host")
    String host;
    
    @Option(names = "--port", defaultValue = "8081", description = "Coordinator management port")
    int port;
    
    @Override
    public void run() {
        printCoordinatorStatus(host, port);
    }
    
    /**
     * Print coordinator status - used by other commands too
     */
    public static void printCoordinatorStatus(String host, int port) {
        try {
            String url = "http://" + host + ":" + port + "/health";
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IllegalStateException("HTTP " + response.code());
                }
                HealthResponse health = MAPPER.readValue(response.body().string(), HealthResponse.class);

                System.out.println("=== Distributed Build System Status ===");
                System.out.printf("Coordinator: %s:%d [%s]%n", host, port, health.status.toUpperCase());
                System.out.printf("gRPC Port: %d%n", health.grpcPort);
                System.out.printf(
                    "Workers: total=%d available=%d busy=%d offline=%d activeTasks=%d%n",
                    health.workerStats.total,
                    health.workerStats.available,
                    health.workerStats.busy,
                    health.workerStats.offline,
                    health.workerStats.activeTasks
                );
                System.out.println("Cache: " + health.cacheStats);
                System.out.println();
                System.out.printf("%-24s %-16s %-6s %-12s %-10s%n", "WORKER ID", "HOST", "PORT", "STATUS", "TASKS");
                System.out.println("----------------------------------------------------------------------");
                if (health.workers == null || health.workers.length == 0) {
                    System.out.println("(no workers connected)");
                } else {
                    for (WorkerInfo worker : health.workers) {
                        System.out.printf(
                            "%-24s %-16s %-6d %-12s %-10s%n",
                            worker.id,
                            worker.host,
                            worker.port,
                            worker.status,
                            worker.activeTasks + "/" + worker.maxTasks
                        );
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to get status: " + e.getMessage());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class HealthResponse {
        public String status;
        public int grpcPort;
        public WorkerInfo[] workers;
        public WorkerStats workerStats;
        public String cacheStats;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class WorkerStats {
        public int total;
        public int available;
        public int busy;
        public int offline;
        public int activeTasks;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class WorkerInfo {
        public String id;
        public String host;
        public int port;
        public String status;
        public int activeTasks;
        public int maxTasks;
    }
}
