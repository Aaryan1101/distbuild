package com.distbuild.coordinator;

import java.util.HashMap;
import java.util.Map;

/**
 * Worker specialization configuration
 */
public class WorkerSpecialization {
    private final String name;
    private final String pattern;
    private final Map<String, String> capabilities;
    private final Map<String, Object> preferences;
    private final int priority;
    
    public WorkerSpecialization(String name, String pattern, Map<String, String> capabilities,
                            Map<String, Object> preferences, int priority) {
        this.name = name;
        this.pattern = pattern;
        this.capabilities = new HashMap<>(capabilities);
        this.preferences = new HashMap<>(preferences);
        this.priority = priority;
    }
    
    public String getName() { return name; }
    public String getPattern() { return pattern; }
    public Map<String, String> getCapabilities() { return new HashMap<>(capabilities); }
    public Map<String, Object> getPreferences() { return new HashMap<>(preferences); }
    public int getPriority() { return priority; }
    
    public boolean matches(String taskName) {
        try {
            return taskName.matches(pattern);
        } catch (Exception e) {
            return false;
        }
    }
}
