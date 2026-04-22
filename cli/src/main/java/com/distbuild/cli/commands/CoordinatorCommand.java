package com.distbuild.cli.commands;

import com.distbuild.cli.config.DistBuildConfig;
import com.distbuild.coordinator.DiscoveryCoordinatorMain;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Coordinator management commands
 */
@Command(
    name = "coordinator",
    description = "Manage coordinator node.",
    subcommands = {
        CoordinatorCommand.Start.class,
        CoordinatorCommand.Stop.class,
        CoordinatorCommand.Status.class
    }
)
public class CoordinatorCommand implements Runnable {
    
    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
    
    @Command(
        name = "start",
        description = "Start coordinator node with configuration from distbuild.yaml"
    )
    public static class Start implements Runnable {
        
        @Option(
            names = {"--config", "-c"},
            description = "Path to distbuild.yaml configuration file",
            defaultValue = "distbuild.yaml"
        )
        File configFile;
        
        @Option(names = "--port", description = "Coordinator port (overrides config)")
        Integer port;
        
        @Option(names = "--cache-dir", description = "Cache directory (overrides config)")
        String cacheDir;

        @Option(names = "--management-port", description = "Management HTTP port (default: coordinator port + 1)")
        Integer managementPort;
        
        @Option(names = "--no-discovery", description = "Disable automatic device discovery")
        Boolean noDiscovery;
        
        @Option(names = "--load-balancing", description = "Load balancing strategy (overrides config)")
        String loadBalancing;
        
        @Option(names = "--worker-config", description = "Worker specialization configuration file")
        String workerConfig;
        
        @Override
        public void run() {
            try {
                // Load configuration
                DistBuildConfig config = DistBuildConfig.load(configFile);
                
                // Apply CLI overrides
                if (port != null) {
                    config.coordinator.port = port;
                }
                if (cacheDir != null) {
                    config.coordinator.cacheDir = cacheDir;
                }
                if (noDiscovery != null) {
                    config.coordinator.discoveryEnabled = !noDiscovery;
                }
                if (managementPort != null) {
                    config.coordinator.managementPort = managementPort;
                }
                if (loadBalancing != null) {
                    config.coordinator.loadBalancing = loadBalancing;
                }
                config.validate();
                
                System.out.println("Starting coordinator...");
                System.out.println("  Port: " + config.coordinator.port);
                System.out.println("  Management port: " + (config.coordinator.managementPort > 0
                    ? config.coordinator.managementPort
                    : config.coordinator.port + 1));
                System.out.println("  Cache: " + config.coordinator.cacheDir);
                System.out.println("  Discovery: " + (config.coordinator.discoveryEnabled ? "enabled" : "disabled"));
                System.out.println("  Load Balancing: " + config.coordinator.loadBalancing);
                
                // Build arguments for existing DiscoveryCoordinatorMain
                String[] args = buildCoordinatorArgs(config);
                
                // Start coordinator using existing main class
                DiscoveryCoordinatorMain coordinatorMain = new DiscoveryCoordinatorMain();
                coordinatorMain.start(args);
                
            } catch (Exception e) {
                System.err.println("Failed to start coordinator: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        }
        
        private String[] buildCoordinatorArgs(DistBuildConfig config) {
            java.util.List<String> args = new java.util.ArrayList<>();
            
            args.add("--port");
            args.add(String.valueOf(config.coordinator.port));
            
            args.add("--cache-dir");
            args.add(config.coordinator.cacheDir);

            int mgmtPort = config.coordinator.managementPort > 0
                ? config.coordinator.managementPort
                : config.coordinator.port + 1;
            args.add("--management-port");
            args.add(String.valueOf(mgmtPort));
            
            if (!config.coordinator.discoveryEnabled) {
                args.add("--no-discovery");
            }
            
            args.add("--load-balancing");
            args.add(config.coordinator.loadBalancing);
            
            // Add worker configuration file if specified
            if (workerConfig != null) {
                args.add("--worker-config");
                args.add(workerConfig);
            }
            
            return args.toArray(new String[0]);
        }
    }
    
    @Command(
        name = "stop",
        description = "Gracefully stop coordinator"
    )
    public static class Stop implements Runnable {
        
        @Option(names = "--host", defaultValue = "localhost", description = "Coordinator host")
        String host;
        
        @Option(names = "--port", defaultValue = "8080", description = "Coordinator port")
        int port;

        @Option(names = "--management-port", defaultValue = "-1", description = "Management HTTP port (default: port + 1)")
        int managementPort;
        
        @Override
        public void run() {
            try {
                int targetPort = managementPort > 0 ? managementPort : port + 1;
                String url = "http://" + host + ":" + targetPort + "/shutdown";
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create("{}", MediaType.parse("application/json")))
                    .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IllegalStateException("Coordinator returned HTTP " + response.code());
                    }
                }

                System.out.println("Coordinator shutdown signal sent successfully");
                
            } catch (Exception e) {
                System.err.println("Failed to stop coordinator: " + e.getMessage());
                System.exit(1);
            }
        }
    }
    
    @Command(
        name = "status",
        description = "Show coordinator health and connected workers"
    )
    public static class Status implements Runnable {
        
        @Option(names = "--host", defaultValue = "localhost", description = "Coordinator host")
        String host;
        
        @Option(names = "--port", defaultValue = "8080", description = "Coordinator port")
        int port;

        @Option(names = "--management-port", defaultValue = "-1", description = "Management HTTP port (default: port + 1)")
        int managementPort;
        
        @Override
        public void run() {
            try {
                // Delegate to StatusCommand for implementation
                int targetPort = managementPort > 0 ? managementPort : port + 1;
                StatusCommand.printCoordinatorStatus(host, targetPort);
            } catch (Exception e) {
                System.err.println("Failed to get coordinator status: " + e.getMessage());
                System.exit(1);
            }
        }
    }
}
