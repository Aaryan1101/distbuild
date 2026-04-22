package com.distbuild.cli.commands;

import com.distbuild.cli.config.DistBuildConfig;
import picocli.CommandLine.Command;

import java.io.File;
import java.util.Scanner;

/**
 * Interactive setup wizard
 */
@Command(
    name = "init",
    description = "Interactive setup — writes distbuild.yaml"
)
public class InitCommand implements Runnable {
    
    @Override
    public void run() {
        try {
            Scanner in = new Scanner(System.in);
            System.out.println("=== distbuild init ===");
            System.out.println("This writes distbuild.yaml in the current directory.\n");
            
            // Prompt for configuration values
            String coordinatorHost = prompt(in, "Coordinator host", "localhost");
            String coordinatorPort = prompt(in, "Coordinator port", "8080");
            String managementPort = prompt(in, "Coordinator management port", "8081");
            String cacheDir = prompt(in, "Cache directory", "./distbuild-cache");
            String discoveryEnabled = prompt(in, "Enable device discovery", "true");
            String maxTasks = prompt(in, "Maximum worker tasks", "4");
            String timeout = prompt(in, "Worker timeout (seconds)", "120");
            
            // Create configuration
            DistBuildConfig config = new DistBuildConfig();
            config.coordinator.host = coordinatorHost;
            config.coordinator.port = Integer.parseInt(coordinatorPort);
            config.coordinator.managementPort = Integer.parseInt(managementPort);
            config.coordinator.cacheDir = cacheDir;
            config.coordinator.discoveryEnabled = Boolean.parseBoolean(discoveryEnabled);
            config.cache.dir = cacheDir;
            config.workers.maxTasks = Integer.parseInt(maxTasks);
            config.workers.timeoutSeconds = Integer.parseInt(timeout);
            
            // Validate configuration
            config.validate();
            
            // Write configuration file
            File configFile = new File("distbuild.yaml");
            config.save(configFile);
            
            System.out.println();
            System.out.println("✓ distbuild.yaml written successfully!");
            System.out.println();
            System.out.println("Quick start:");
            System.out.println("  distbuild coordinator start    # Start coordinator");
            System.out.println("  distbuild worker join          # Start worker");
            System.out.println("  distbuild status               # Check status");
            
        } catch (Exception e) {
            System.err.println("Failed to initialize: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private String prompt(Scanner in, String label, String defaultValue) {
        System.out.printf("  %s [%s]: ", label, defaultValue);
        String input = in.nextLine().trim();
        return input.isEmpty() ? defaultValue : input;
    }
}
