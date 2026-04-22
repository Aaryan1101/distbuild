package com.distbuild.cli.commands;

import com.distbuild.cli.config.CliConfig;
import com.distbuild.cli.config.DistBuildConfig;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Command(
    name = "doctor",
    description = "Diagnose distbuild setup and connectivity issues"
)
public class DoctorCommand implements Runnable {
    
    @Option(names = {"-v", "--verbose"}, description = "Verbose diagnostic output")
    private boolean verbose;
    
    @Option(names = {"--fix"}, description = "Attempt to fix common issues automatically")
    private boolean autoFix;
    
    @Override
    public void run() {
        System.out.println("DistBuild Doctor - Diagnosing your setup...\n");
        
        List<String> issues = new ArrayList<>();
        List<String> fixes = new ArrayList<>();
        
        // Check 1: Configuration file
        checkConfiguration(issues, fixes);
        
        // Check 2: Java version
        checkJavaVersion(issues, fixes);
        
        // Check 3: Network connectivity
        checkNetworkConnectivity(issues, fixes);
        
        // Check 4: Directories and permissions
        checkDirectories(issues, fixes);
        
        // Check 5: Cache functionality
        checkCache(issues, fixes);
        
        // Report results
        reportResults(issues, fixes);
        
        if (autoFix && !fixes.isEmpty()) {
            attemptFixes(fixes);
        }
        
        // Exit with error code if issues found
        if (!issues.isEmpty()) {
            System.exit(1);
        }
    }
    
    private void checkConfiguration(List<String> issues, List<String> fixes) {
        System.out.println("Checking configuration...");
        
        try {
            DistBuildConfig config = DistBuildConfig.load(new File("distbuild.yaml"));
            System.out.println("  ✓ Configuration file loaded successfully");
            
            if (config.coordinator.port < 1 || config.coordinator.port > 65535) {
                issues.add("Invalid coordinator port: " + config.coordinator.port);
                fixes.add("Set coordinator.port to a valid port (1-65535)");
            }
            
            if (config.cache.dir == null || config.cache.dir.trim().isEmpty()) {
                issues.add("Cache directory not specified");
                fixes.add("Set cache.dir in configuration file");
            }
            
        } catch (Exception e) {
            issues.add("Failed to load configuration: " + e.getMessage());
            fixes.add("Create distbuild.yaml with proper configuration");
            System.out.println("  ✗ Configuration file error: " + e.getMessage());
        }
    }
    
    private void checkJavaVersion(List<String> issues, List<String> fixes) {
        System.out.println("Checking Java environment...");
        
        String javaVersion = System.getProperty("java.version");
        System.out.println("  Java version: " + javaVersion);
        
        if (javaVersion.startsWith("1.") || javaVersion.startsWith("8.") || 
            javaVersion.startsWith("9.") || javaVersion.startsWith("10.") || 
            javaVersion.startsWith("11.")) {
            issues.add("Java version " + javaVersion + " is not recommended");
            fixes.add("Upgrade to Java 17 or later for best compatibility");
            System.out.println("  Warning: Java version is outdated - recommend Java 17+");
        } else {
            System.out.println("  ✓ Java version is compatible");
        }
        
        // Check available memory
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024); // MB
        System.out.println("  Max memory: " + maxMemory + "MB");
        
        if (maxMemory < 512) {
            issues.add("Low memory available: " + maxMemory + "MB");
            fixes.add("Increase JVM memory with -Xmx flag");
            System.out.println("  Warning: Low memory - recommend at least 512MB");
        }
    }
    
    private void checkNetworkConnectivity(List<String> issues, List<String> fixes) {
        System.out.println("Checking network connectivity...");
        
        try {
            DistBuildConfig config = DistBuildConfig.load(new File("distbuild.yaml"));
            String host = "localhost";
            int port = config.coordinator.port;
            
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 5000);
                System.out.println("  ✓ Can connect to coordinator on port " + port);
            }
            
            // Check management port
            int mgmtPort = port + 1;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, mgmtPort), 5000);
                System.out.println("  ✓ Can connect to management API on port " + mgmtPort);
            }
            
        } catch (Exception e) {
            issues.add("Cannot connect to coordinator: " + e.getMessage());
            fixes.add("Start coordinator with 'distbuild coordinator start'");
            System.out.println("  ✗ Network connectivity issue: " + e.getMessage());
        }
    }
    
    private void checkDirectories(List<String> issues, List<String> fixes) {
        System.out.println("Checking directories and permissions...");
        
        try {
            DistBuildConfig config = DistBuildConfig.load(new File("distbuild.yaml"));
            
            // Check cache directory
            Path cacheDir = Paths.get(config.cache.dir);
            if (!Files.exists(cacheDir)) {
                if (autoFix) {
                    Files.createDirectories(cacheDir);
                    System.out.println("  ✓ Created cache directory: " + cacheDir);
                } else {
                    issues.add("Cache directory does not exist: " + cacheDir);
                    fixes.add("Create cache directory: " + cacheDir);
                    System.out.println("  ✗ Cache directory missing: " + cacheDir);
                }
            } else if (!Files.isWritable(cacheDir)) {
                issues.add("Cache directory is not writable: " + cacheDir);
                fixes.add("Check permissions for cache directory");
                System.out.println("  ✗ Cache directory not writable: " + cacheDir);
            } else {
                System.out.println("  ✓ Cache directory is accessible");
            }
            
            // Check logs directory
            Path logsDir = Paths.get("logs");
            if (!Files.exists(logsDir)) {
                if (autoFix) {
                    Files.createDirectories(logsDir);
                    System.out.println("  ✓ Created logs directory: " + logsDir);
                } else {
                    issues.add("Logs directory does not exist: " + logsDir);
                    fixes.add("Create logs directory: " + logsDir);
                    System.out.println("  ✗ Logs directory missing: " + logsDir);
                }
            }
            
        } catch (Exception e) {
            issues.add("Directory check failed: " + e.getMessage());
            fixes.add("Check file system permissions");
        }
    }
    
    private void checkCache(List<String> issues, List<String> fixes) {
        System.out.println("Checking cache functionality...");
        
        try {
            DistBuildConfig config = DistBuildConfig.load(new File("distbuild.yaml"));
            Path cacheDir = Paths.get(config.cache.dir);
            
            if (Files.exists(cacheDir)) {
                // Test write permissions
                Path testFile = cacheDir.resolve("distbuild-doctor-test");
                try {
                    Files.writeString(testFile, "test");
                    Files.delete(testFile);
                    System.out.println("  ✓ Cache directory is writable");
                } catch (IOException e) {
                    issues.add("Cannot write to cache directory: " + e.getMessage());
                    fixes.add("Check cache directory permissions");
                    System.out.println("  ✗ Cache write test failed: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            issues.add("Cache check failed: " + e.getMessage());
            fixes.add("Verify cache configuration and permissions");
        }
    }
    
    private void reportResults(List<String> issues, List<String> fixes) {
        System.out.println("\n" + "=".repeat(50));
        
        if (issues.isEmpty()) {
            System.out.println("All checks passed! Your distbuild setup is healthy.");
        } else {
            System.out.println("Found " + issues.size() + " issue(s):");
            for (int i = 0; i < issues.size(); i++) {
                System.out.println("  " + (i + 1) + ". " + issues.get(i));
            }
            
            if (!fixes.isEmpty()) {
                System.out.println("\nSuggested fixes:");
                for (int i = 0; i < fixes.size(); i++) {
                    System.out.println("  " + (i + 1) + ". " + fixes.get(i));
                }
            }
        }
        
        System.out.println("=".repeat(50));
    }
    
    private void attemptFixes(List<String> fixes) {
        System.out.println("\nAttempting automatic fixes...");
        
        for (String fix : fixes) {
            if (fix.contains("Create cache directory") || fix.contains("Create logs directory")) {
                System.out.println("  ✓ " + fix);
            } else {
                System.out.println("  Warning: Cannot auto-fix: " + fix);
            }
        }
    }
}
