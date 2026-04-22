package com.distbuild.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Centralized error reporting and diagnostics service
 */
public class ErrorReportingService {
    private static final Logger logger = LoggerFactory.getLogger(ErrorReportingService.class);
    
    private static final int MAX_ERROR_HISTORY = 1000;
    private static final long ERROR_RETENTION_HOURS = 24;
    
    private final Map<String, DiagnosticError> recentErrors = new ConcurrentHashMap<>();
    private final AtomicLong errorCounter = new AtomicLong(0);
    private final Map<DiagnosticError.ErrorCategory, AtomicLong> categoryCounts = new ConcurrentHashMap<>();
    private final Map<DiagnosticError.Severity, AtomicLong> severityCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> componentCounts = new ConcurrentHashMap<>();
    
    private static ErrorReportingService instance;
    
    private ErrorReportingService() {
        startCleanupTask();
    }
    
    public static synchronized ErrorReportingService getInstance() {
        if (instance == null) {
            instance = new ErrorReportingService();
        }
        return instance;
    }
    
    public void reportError(DiagnosticError error) {
        recentErrors.put(error.getErrorId(), error);
        errorCounter.incrementAndGet();
        
        categoryCounts.computeIfAbsent(error.getCategory(), k -> new AtomicLong(0)).incrementAndGet();
        severityCounts.computeIfAbsent(error.getSeverity(), k -> new AtomicLong(0)).incrementAndGet();
        componentCounts.computeIfAbsent(error.getComponent(), k -> new AtomicLong(0)).incrementAndGet();
        
        logger.error("DiagnosticError reported: {}", error.toDetailedString());
        
        if (error.getSeverity() == DiagnosticError.Severity.CRITICAL) {
            triggerCriticalAlert(error);
        }
        
        cleanupOldErrors();
    }
    
    public ErrorStatistics getStatistics() {
        return new ErrorStatistics(
            errorCounter.get(),
            new HashMap<>(categoryCounts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()))),
            new HashMap<>(severityCounts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()))),
            new HashMap<>(componentCounts.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()))),
            getRecentErrorsSummary()
        );
    }
    
    public List<DiagnosticError> getRecentErrors(int limit) {
        return recentErrors.values().stream()
            .sorted((e1, e2) -> e2.getTimestamp().compareTo(e1.getTimestamp()))
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    public List<DiagnosticError> getErrorsByCategory(DiagnosticError.ErrorCategory category, int limit) {
        return recentErrors.values().stream()
            .filter(error -> error.getCategory() == category)
            .sorted((e1, e2) -> e2.getTimestamp().compareTo(e1.getTimestamp()))
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    public List<DiagnosticError> getErrorsBySeverity(DiagnosticError.Severity severity, int limit) {
        return recentErrors.values().stream()
            .filter(error -> error.getSeverity() == severity)
            .sorted((e1, e2) -> e2.getTimestamp().compareTo(e1.getTimestamp()))
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    public ErrorTrends getTrends() {
        Instant now = Instant.now();
        long lastHour = recentErrors.values().stream()
            .filter(e -> e.getTimestamp().isAfter(now.minus(1, ChronoUnit.HOURS)))
            .count();
        long lastSixHours = recentErrors.values().stream()
            .filter(e -> e.getTimestamp().isAfter(now.minus(6, ChronoUnit.HOURS)))
            .count();
        long lastDay = recentErrors.values().stream()
            .filter(e -> e.getTimestamp().isAfter(now.minus(1, ChronoUnit.DAYS)))
            .count();
        return new ErrorTrends(lastHour, lastSixHours, lastDay);
    }
    
    public ErrorPatterns analyzePatterns() {
        Map<String, Long> messagePatterns = new HashMap<>();
        Map<String, Long> componentPatterns = new HashMap<>();
        
        recentErrors.values().stream()
            .collect(Collectors.groupingBy(DiagnosticError::getMessage, Collectors.counting()))
            .forEach((message, count) -> {
                if (count > 1) {
                    messagePatterns.put(message, count);
                }
            });
        
        componentCounts.forEach((component, count) -> {
            if (count.get() > 2) {
                componentPatterns.put(component, count.get());
            }
        });
        
        return new ErrorPatterns(messagePatterns, componentPatterns);
    }
    
    public SystemHealth getSystemHealth() {
        Instant fiveMinutesAgo = Instant.now().minus(5, ChronoUnit.MINUTES);
        long recentCriticalErrors = recentErrors.values().stream()
            .filter(e -> e.getSeverity() == DiagnosticError.Severity.CRITICAL)
            .filter(e -> e.getTimestamp().isAfter(fiveMinutesAgo))
            .count();
        long recentHighErrors = recentErrors.values().stream()
            .filter(e -> e.getSeverity() == DiagnosticError.Severity.HIGH)
            .filter(e -> e.getTimestamp().isAfter(fiveMinutesAgo))
            .count();
        double errorRate = getErrorRate();
        
        SystemHealth.HealthLevel healthLevel;
        if (recentCriticalErrors > 0) {
            healthLevel = SystemHealth.HealthLevel.CRITICAL;
        } else if (recentHighErrors > 5 || errorRate > 0.1) {
            healthLevel = SystemHealth.HealthLevel.WARNING;
        } else if (errorRate > 0.05) {
            healthLevel = SystemHealth.HealthLevel.CAUTION;
        } else {
            healthLevel = SystemHealth.HealthLevel.HEALTHY;
        }
        return new SystemHealth(healthLevel, recentCriticalErrors, recentHighErrors, errorRate);
    }
    
    private void triggerCriticalAlert(DiagnosticError error) {
        logger.error("CRITICAL ALERT: {}", error.getMessage());
    }
    
    private Map<String, Long> getRecentErrorsSummary() {
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        Map<String, Long> summary = new HashMap<>();
        recentErrors.values().stream()
            .filter(e -> e.getTimestamp().isAfter(oneHourAgo))
            .collect(Collectors.groupingBy(DiagnosticError::getCategory, Collectors.counting()))
            .forEach((category, count) -> summary.put(category.getDisplayName(), count));
        return summary;
    }
    
    private double getErrorRate() {
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        long recentErrorCount = recentErrors.values().stream()
            .filter(e -> e.getTimestamp().isAfter(oneHourAgo))
            .count();
        return Math.min(1.0, recentErrorCount / 60.0);
    }
    
    private void cleanupOldErrors() {
        if (recentErrors.size() > MAX_ERROR_HISTORY) {
            List<Map.Entry<String, DiagnosticError>> sortedErrors = recentErrors.entrySet().stream()
                .sorted(Map.Entry.comparingByValue((e1, e2) -> e1.getTimestamp().compareTo(e2.getTimestamp())))
                .collect(Collectors.toList());
            int toRemove = recentErrors.size() - MAX_ERROR_HISTORY;
            for (int i = 0; i < toRemove; i++) {
                recentErrors.remove(sortedErrors.get(i).getKey());
            }
        }
    }
    
    private void startCleanupTask() {
        Timer timer = new Timer("ErrorCleanup", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Instant cutoff = Instant.now().minus(ERROR_RETENTION_HOURS, ChronoUnit.HOURS);
                recentErrors.entrySet().removeIf(entry -> entry.getValue().getTimestamp().isBefore(cutoff));
            }
        }, ERROR_RETENTION_HOURS * 60 * 60 * 1000L, ERROR_RETENTION_HOURS * 60 * 60 * 1000L);
    }
    
    public static class ErrorStatistics {
        public final long totalErrors;
        public final Map<DiagnosticError.ErrorCategory, Long> categoryCounts;
        public final Map<DiagnosticError.Severity, Long> severityCounts;
        public final Map<String, Long> componentCounts;
        public final Map<String, Long> recentSummary;
        
        public ErrorStatistics(long totalErrors, Map<DiagnosticError.ErrorCategory, Long> categoryCounts,
                           Map<DiagnosticError.Severity, Long> severityCounts,
                           Map<String, Long> componentCounts, Map<String, Long> recentSummary) {
            this.totalErrors = totalErrors;
            this.categoryCounts = categoryCounts;
            this.severityCounts = severityCounts;
            this.componentCounts = componentCounts;
            this.recentSummary = recentSummary;
        }
    }
    
    public static class ErrorTrends {
        public final long lastHour;
        public final long lastSixHours;
        public final long lastDay;
        
        public ErrorTrends(long lastHour, long lastSixHours, long lastDay) {
            this.lastHour = lastHour;
            this.lastSixHours = lastSixHours;
            this.lastDay = lastDay;
        }
    }
    
    public static class ErrorPatterns {
        public final Map<String, Long> messagePatterns;
        public final Map<String, Long> componentPatterns;
        
        public ErrorPatterns(Map<String, Long> messagePatterns, Map<String, Long> componentPatterns) {
            this.messagePatterns = messagePatterns;
            this.componentPatterns = componentPatterns;
        }
    }
    
    public static class SystemHealth {
        public enum HealthLevel {
            HEALTHY("Healthy", "green"),
            CAUTION("Caution", "yellow"),
            WARNING("Warning", "orange"),
            CRITICAL("Critical", "red");
            
            private final String displayName;
            private final String color;
            
            HealthLevel(String displayName, String color) {
                this.displayName = displayName;
                this.color = color;
            }
            
            public String getDisplayName() { return displayName; }
            public String getColor() { return color; }
        }
        
        public final HealthLevel level;
        public final long criticalErrors;
        public final long highErrors;
        public final double errorRate;
        
        public SystemHealth(HealthLevel level, long criticalErrors, long highErrors, double errorRate) {
            this.level = level;
            this.criticalErrors = criticalErrors;
            this.highErrors = highErrors;
            this.errorRate = errorRate;
        }
    }
}
