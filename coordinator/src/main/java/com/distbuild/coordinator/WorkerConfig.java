package com.distbuild.coordinator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Worker configuration for specializations
 */
public class WorkerConfig {
    
    public static class WorkersConfig {
        public int timeoutSeconds = 120;
        public int maxTasks = 4;
        public String javaVersion = "17";
        public List<WorkerSpecialization> specializations = new ArrayList<>();
        public Map<String, String> defaultCapabilities = new HashMap<>();
    }
    
    public static class WorkerSpecialization {
        public String name;
        public String pattern;
        public Map<String, String> capabilities = new HashMap<>();
        public Map<String, Object> preferences = new HashMap<>();
        public int priority = 0;
    }
}
