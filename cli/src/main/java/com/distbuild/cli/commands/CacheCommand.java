package com.distbuild.cli.commands;

import com.distbuild.cli.config.DistBuildConfig;
import com.distbuild.common.cache.CacheStats;
import com.distbuild.common.cache.LocalDiskCache;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Cache management commands
 */
@Command(
    name = "cache",
    description = "Manage build cache",
    subcommands = {
        CacheCommand.Stats.class,
        CacheCommand.Clear.class
    }
)
public class CacheCommand implements Runnable {
    
    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
    
    @Command(
        name = "stats",
        description = "Show cache statistics"
    )
    public static class Stats implements Runnable {
        
        @Option(
            names = {"--config", "-c"},
            description = "Path to distbuild.yaml configuration file",
            defaultValue = "distbuild.yaml"
        )
        File configFile;
        
        @Override
        public void run() {
            try {
                // Load configuration
                DistBuildConfig config = DistBuildConfig.load(configFile);
                
                // Initialize cache to get stats
                LocalDiskCache cache = new LocalDiskCache(Paths.get(config.cache.dir));
                CacheStats stats = cache.getStats();
                long cacheSizeBytes = computeDirectorySize(Paths.get(config.cache.dir));
                long cacheSizeMb = cacheSizeBytes / (1024 * 1024);
                
                System.out.println("=== Cache Statistics ===");
                System.out.println();
                System.out.println("Cache directory: " + config.cache.dir);
                System.out.println("TTL (days): " + config.cache.ttlDays);
                System.out.println("Max size (MB): " + config.cache.maxSizeMb);
                System.out.println();
                System.out.println("Hits: " + stats.getHits());
                System.out.println("Misses: " + stats.getMisses());
                System.out.println("Puts: " + stats.getPuts());
                System.out.println("Evictions: " + stats.getEvictions());
                System.out.println("Errors: " + stats.getErrors());
                System.out.printf("Hit rate: %.2f%%%n", stats.getHitRate() * 100);
                System.out.println("Current size (MB): " + cacheSizeMb);
                cache.close();
                
            } catch (Exception e) {
                System.err.println("Failed to get cache stats: " + e.getMessage());
                System.exit(1);
            }
        }
    }
    
    @Command(
        name = "clear",
        description = "Clear all cache entries"
    )
    public static class Clear implements Runnable {
        
        @Option(
            names = {"--config", "-c"},
            description = "Path to distbuild.yaml configuration file",
            defaultValue = "distbuild.yaml"
        )
        File configFile;
        
        @Option(names = "--confirm", description = "Skip confirmation prompt")
        Boolean confirm;
        
        @Override
        public void run() {
            try {
                // Load configuration
                DistBuildConfig config = DistBuildConfig.load(configFile);
                
                if (confirm == null || !confirm) {
                    System.out.println("This will clear all cache entries in: " + config.cache.dir);
                    System.out.print("Are you sure? [y/N]: ");
                    
                    java.util.Scanner scanner = new java.util.Scanner(System.in);
                    String response = scanner.nextLine().trim().toLowerCase();
                    if (!response.equals("y") && !response.equals("yes")) {
                        System.out.println("Cancelled");
                        return;
                    }
                }
                
                LocalDiskCache cache = new LocalDiskCache(Paths.get(config.cache.dir));
                cache.clear();
                cache.close();
                System.out.println("Cache cleared successfully: " + config.cache.dir);
                
            } catch (Exception e) {
                System.err.println("Failed to clear cache: " + e.getMessage());
                System.exit(1);
            }
        }
    }

    private static long computeDirectorySize(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return 0L;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            return paths
                .filter(Files::isRegularFile)
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException e) {
                        return 0L;
                    }
                })
                .sum();
        }
    }
}
