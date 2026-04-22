package com.distbuild.cli.config;

import java.io.File;
import java.util.Map;
import java.util.HashMap;

/**
 * CLI-specific configuration
 */
public class CliConfig {
    public String coordinatorHost;
    public int coordinatorPort;
    public int managementPort;
    public String cacheDir;
    public String logLevel;
    
    public static CliConfig load() {
        CliConfig config = new CliConfig();
        
        // Load from environment variables
        config.coordinatorHost = System.getenv("DISTBUILD_COORDINATOR_HOST");
        if (config.coordinatorHost == null) {
            config.coordinatorHost = "localhost";
        }
        
        String portStr = System.getenv("DISTBUILD_COORDINATOR_GRPC_PORT");
        if (portStr != null) {
            try {
                config.coordinatorPort = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                config.coordinatorPort = 8080;
            }
        } else {
            config.coordinatorPort = 8080;
        }
        
        String mgmtPortStr = System.getenv("DISTBUILD_COORDINATOR_HTTP_PORT");
        if (mgmtPortStr != null) {
            try {
                config.managementPort = Integer.parseInt(mgmtPortStr);
            } catch (NumberFormatException e) {
                config.managementPort = config.coordinatorPort + 1;
            }
        } else {
            config.managementPort = config.coordinatorPort + 1;
        }
        
        config.cacheDir = System.getenv("DISTBUILD_CACHE_DIR");
        if (config.cacheDir == null) {
            config.cacheDir = "./distbuild-cache";
        }
        
        config.logLevel = System.getenv("DISTBUILD_LOG_LEVEL");
        if (config.logLevel == null) {
            config.logLevel = "INFO";
        }
        
        return config;
    }
    
    public String getCoordinatorHost() {
        return coordinatorHost;
    }
    
    public int getCoordinatorPort() {
        return coordinatorPort;
    }
    
    public int getManagementPort() {
        return managementPort;
    }
    
    public String getCacheDir() {
        return cacheDir;
    }
    
    public String getLogLevel() {
        return logLevel;
    }
}
