package com.distbuild.cli.commands;

import com.distbuild.cli.config.DistBuildConfig;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.Map;

@Command(
    name = "config",
    description = "Manage distbuild configuration",
    subcommands = {
        ConfigCommand.Get.class,
        ConfigCommand.Set.class,
        ConfigCommand.List.class,
        ConfigCommand.Reset.class
    }
)
public class ConfigCommand implements Runnable {
    
    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
    
    @Command(name = "get", description = "Get a configuration value")
    public static class Get implements Runnable {
        @Option(names = {"-c", "--config"}, defaultValue = "distbuild.yaml",
                  description = "Path to configuration file")
        File configFile;
        
        @CommandLine.Parameters(index = "0", description = "Configuration key to get")
        String key;
        
        @Override
        public void run() {
            try {
                DistBuildConfig config = DistBuildConfig.load(configFile);
                Object value = getConfigValue(config, key);
                if (value != null) {
                    System.out.println(key + " = " + value);
                } else {
                    System.err.println("Unknown configuration key: " + key);
                    System.exit(1);
                }
            } catch (Exception e) {
                System.err.println("Failed to get configuration: " + e.getMessage());
                System.exit(1);
            }
        }
    }
    
    @Command(name = "set", description = "Set a configuration value")
    public static class Set implements Runnable {
        @Option(names = {"-c", "--config"}, defaultValue = "distbuild.yaml",
                  description = "Path to configuration file")
        File configFile;
        
        @CommandLine.Parameters(index = "0", description = "Configuration key to set")
        String key;
        
        @CommandLine.Parameters(index = "1", description = "Configuration value to set")
        String value;
        
        @Override
        public void run() {
            try {
                System.out.println("Setting " + key + " = " + value);
                System.out.println("Note: Configuration file editing not fully implemented yet.");
                System.out.println("Please edit " + configFile.getAbsolutePath() + " manually.");
            } catch (Exception e) {
                System.err.println("Failed to set configuration: " + e.getMessage());
                System.exit(1);
            }
        }
    }
    
    @Command(name = "list", description = "List all configuration values")
    public static class List implements Runnable {
        @Option(names = {"-c", "--config"}, defaultValue = "distbuild.yaml",
                  description = "Path to configuration file")
        File configFile;
        
        @Override
        public void run() {
            try {
                DistBuildConfig config = DistBuildConfig.load(configFile);
                System.out.println("Current configuration:");
                System.out.println("  coordinator.port = " + config.coordinator.port);
                System.out.println("  coordinator.discoveryEnabled = " + config.coordinator.discoveryEnabled);
                System.out.println("  coordinator.loadBalancing = " + config.coordinator.loadBalancing);
                System.out.println("  cache.dir = " + config.cache.dir);
                System.out.println("  cache.ttlDays = " + config.cache.ttlDays);
            } catch (Exception e) {
                System.err.println("Failed to list configuration: " + e.getMessage());
                System.exit(1);
            }
        }
    }
    
    @Command(name = "reset", description = "Reset configuration to defaults")
    public static class Reset implements Runnable {
        @Option(names = {"-c", "--config"}, defaultValue = "distbuild.yaml",
                  description = "Path to configuration file")
        File configFile;
        
        @Override
        public void run() {
            try {
                DistBuildConfig defaults = DistBuildConfig.defaults();
                System.out.println("Configuration reset to defaults:");
                System.out.println("  coordinator.port = " + defaults.coordinator.port);
                System.out.println("  coordinator.discoveryEnabled = " + defaults.coordinator.discoveryEnabled);
                System.out.println("  coordinator.loadBalancing = " + defaults.coordinator.loadBalancing);
                System.out.println("  cache.dir = " + defaults.cache.dir);
                System.out.println("  cache.ttlDays = " + defaults.cache.ttlDays);
                System.out.println("Note: Please edit " + configFile.getAbsolutePath() + " manually to apply defaults.");
            } catch (Exception e) {
                System.err.println("Failed to reset configuration: " + e.getMessage());
                System.exit(1);
            }
        }
    }
    
    private static Object getConfigValue(DistBuildConfig config, String key) {
        switch (key) {
            case "coordinator.port":
                return config.coordinator.port;
            case "coordinator.discoveryEnabled":
                return config.coordinator.discoveryEnabled;
            case "coordinator.loadBalancing":
                return config.coordinator.loadBalancing;
            case "cache.dir":
                return config.cache.dir;
            case "cache.ttlDays":
                return config.cache.ttlDays;
            default:
                return null;
        }
    }
}
