package com.distbuild.cli.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration model for distbuild.yaml
 */
public class DistBuildConfig {
    
    @JsonProperty("coordinator")
    public CoordinatorConfig coordinator = new CoordinatorConfig();
    
    @JsonProperty("cache")
    public CacheConfig cache = new CacheConfig();
    
    @JsonProperty("workers")
    public WorkersConfig workers = new WorkersConfig();
    
    public static class CoordinatorConfig {
        @JsonProperty("host")
        public String host = "localhost";
        
        @JsonProperty("port")
        public int port = 8080;

        @JsonProperty("management-port")
        public int managementPort = 0; // 0 means auto (port + 1)
        
        @JsonProperty("cache-dir")
        public String cacheDir = "./coordinator-cache";
        
        @JsonProperty("discovery-enabled")
        public boolean discoveryEnabled = true;
        
        @JsonProperty("load-balancing")
        public String loadBalancing = "LEAST_LOADED";
    }
    
    public static class CacheConfig {
        @JsonProperty("dir")
        public String dir = "./distbuild-cache";
        
        @JsonProperty("ttl-days")
        public int ttlDays = 7;
        
        @JsonProperty("max-size-mb")
        public int maxSizeMb = 1024; // 1GB
    }
    
    public static class WorkersConfig {
        @JsonProperty("timeout-seconds")
        public int timeoutSeconds = 120;
        
        @JsonProperty("max-tasks")
        public int maxTasks = 4;
        
        @JsonProperty("java-version")
        public String javaVersion = "17";
        
        @JsonProperty("specializations")
        public java.util.List<WorkerSpecialization> specializations = new java.util.ArrayList<>();
        
        @JsonProperty("default-capabilities")
        public java.util.Map<String, String> defaultCapabilities = new java.util.HashMap<>();
    }
    
    public static class WorkerSpecialization {
        @JsonProperty("name")
        public String name;
        
        @JsonProperty("pattern")
        public String pattern; // Regex or glob pattern for matching
        
        @JsonProperty("capabilities")
        public java.util.Map<String, String> capabilities = new java.util.HashMap<>();
        
        @JsonProperty("preferences")
        public java.util.Map<String, Object> preferences = new java.util.HashMap<>();
        
        @JsonProperty("priority")
        public int priority = 0; // Higher priority = preferred for matching tasks
    }
    
    /**
     * Load configuration from YAML file
     */
    public static DistBuildConfig load(File configFile) throws IOException {
        if (configFile.exists()) {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            return mapper.readValue(configFile, DistBuildConfig.class);
        }
        return defaults();
    }
    
    /**
     * Create default configuration
     */
    public static DistBuildConfig defaults() {
        return new DistBuildConfig();
    }
    
    /**
     * Save configuration to YAML file
     */
    public void save(File configFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, this);
    }
    
    /**
     * Validate configuration
     */
    public void validate() throws IllegalArgumentException {
        if (coordinator.port <= 0 || coordinator.port > 65535) {
            throw new IllegalArgumentException("Invalid coordinator port: " + coordinator.port);
        }
        if (coordinator.managementPort < 0 || coordinator.managementPort > 65535) {
            throw new IllegalArgumentException("Invalid management port: " + coordinator.managementPort);
        }
        if (workers.maxTasks <= 0) {
            throw new IllegalArgumentException("Invalid max tasks: " + workers.maxTasks);
        }
        if (workers.timeoutSeconds <= 0) {
            throw new IllegalArgumentException("Invalid timeout: " + workers.timeoutSeconds);
        }
    }
}
