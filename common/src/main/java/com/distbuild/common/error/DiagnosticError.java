package com.distbuild.common.error;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

/**
 * Enhanced error with detailed diagnostics information
 */
public class DiagnosticError {
    
    public enum ErrorCategory {
        COMPILATION("Compilation Error"),
        NETWORK("Network Error"), 
        CONFIGURATION("Configuration Error"),
        WORKER("Worker Error"),
        COORDINATOR("Coordinator Error"),
        CACHE("Cache Error"),
        TIMEOUT("Timeout Error"),
        RESOURCE("Resource Error"),
        AUTHENTICATION("Authentication Error"),
        VALIDATION("Validation Error"),
        UNKNOWN("Unknown Error");
        
        private final String displayName;
        
        ErrorCategory(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public enum Severity {
        LOW("Low", 1),
        MEDIUM("Medium", 2),
        HIGH("High", 3),
        CRITICAL("Critical", 4);
        
        private final String displayName;
        private final int level;
        
        Severity(String displayName, int level) {
            this.displayName = displayName;
            this.level = level;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    private final String errorId;
    private final ErrorCategory category;
    private final Severity severity;
    private final String message;
    private final String details;
    private final Throwable cause;
    private final Instant timestamp;
    private final Map<String, Object> context;
    private final String component;
    private final String suggestion;
    
    public DiagnosticError(String errorId, ErrorCategory category, Severity severity,
                        String message, String details, Throwable cause,
                        String component, String suggestion) {
        this.errorId = errorId;
        this.category = category;
        this.severity = severity;
        this.message = message;
        this.details = details;
        this.cause = cause;
        this.timestamp = Instant.now();
        this.context = new HashMap<>();
        this.component = component;
        this.suggestion = suggestion;
    }
    
    // Getters
    public String getErrorId() { return errorId; }
    public ErrorCategory getCategory() { return category; }
    public Severity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public String getDetails() { return details; }
    public Throwable getCause() { return cause; }
    public Instant getTimestamp() { return timestamp; }
    public Map<String, Object> getContext() { return new HashMap<>(context); }
    public String getComponent() { return component; }
    public String getSuggestion() { return suggestion; }
    
    // Context manipulation
    public void addContext(String key, Object value) {
        context.put(key, value);
    }
    
    public void addContext(Map<String, Object> additionalContext) {
        context.putAll(additionalContext);
    }
    
    // Factory methods for common error types
    public static DiagnosticError compilationError(String taskId, String message, Throwable cause) {
        return new DiagnosticError(
            "COMP-" + System.currentTimeMillis(),
            ErrorCategory.COMPILATION,
            Severity.HIGH,
            message,
            "Task " + taskId + " failed to compile",
            cause,
            "WorkerService",
            "Check source files for syntax errors and ensure dependencies are available"
        );
    }
    
    public static DiagnosticError compilationTaskError(String taskId, String message, Throwable cause) {
        return new DiagnosticError(
            "COMP-" + System.currentTimeMillis(),
            ErrorCategory.COMPILATION,
            Severity.HIGH,
            message,
            "Task " + taskId + " compilation error",
            cause,
            "WorkerService",
            "Check source files for syntax errors and ensure dependencies are available"
        );
    }
    
    public static DiagnosticError networkError(String operation, String target, Throwable cause) {
        return new DiagnosticError(
            "NET-" + System.currentTimeMillis(),
            ErrorCategory.NETWORK,
            Severity.MEDIUM,
            "Network operation failed: " + operation,
            "Failed to connect to " + target,
            cause,
            "NetworkLayer",
            "Check network connectivity and target service availability"
        );
    }
    
    public static DiagnosticError workerError(String workerId, String operation, Throwable cause) {
        return new DiagnosticError(
            "WORK-" + System.currentTimeMillis(),
            ErrorCategory.WORKER,
            Severity.HIGH,
            "Worker operation failed: " + operation,
            "Worker " + workerId + " encountered an error",
            cause,
            "WorkerManager",
            "Check worker logs and restart worker if necessary"
        );
    }
    
    public static DiagnosticError configurationError(String component, String configKey, String invalidValue) {
        return new DiagnosticError(
            "CFG-" + System.currentTimeMillis(),
            ErrorCategory.CONFIGURATION,
            Severity.MEDIUM,
            "Invalid configuration value",
            "Configuration key '" + configKey + "' has invalid value: " + invalidValue,
            null,
            component,
            "Check configuration file and provide valid value for " + configKey
        );
    }
    
    public static DiagnosticError timeoutError(String operation, long timeoutMs) {
        return new DiagnosticError(
            "TIME-" + System.currentTimeMillis(),
            ErrorCategory.TIMEOUT,
            Severity.MEDIUM,
            "Operation timed out",
            "Operation " + operation + " timed out after " + timeoutMs + "ms",
            null,
            "TimeoutManager",
            "Increase timeout value or optimize operation performance"
        );
    }
    
    @Override
    public String toString() {
        return String.format(
            "[%s] %s - %s: %s (%s)",
            severity.getDisplayName(),
            category.getDisplayName(),
            component,
            message,
            errorId
        );
    }
    
    public String toDetailedString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Error ID: ").append(errorId).append("\n");
        sb.append("Timestamp: ").append(timestamp).append("\n");
        sb.append("Severity: ").append(severity.getDisplayName()).append("\n");
        sb.append("Category: ").append(category.getDisplayName()).append("\n");
        sb.append("Component: ").append(component).append("\n");
        sb.append("Message: ").append(message).append("\n");
        if (details != null && !details.isEmpty()) {
            sb.append("Details: ").append(details).append("\n");
        }
        if (suggestion != null && !suggestion.isEmpty()) {
            sb.append("Suggestion: ").append(suggestion).append("\n");
        }
        if (!context.isEmpty()) {
            sb.append("Context:\n");
            context.forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
        }
        if (cause != null) {
            sb.append("Cause: ").append(cause.toString()).append("\n");
        }
        return sb.toString();
    }
}
