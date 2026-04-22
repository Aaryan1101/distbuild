package com.distbuild.cli.commands;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

@Command(
    name = "logs",
    description = "View distbuild logs"
)
public class LogsCommand implements Runnable {
    
    @Option(names = {"-f", "--follow"}, description = "Follow log output")
    private boolean follow;
    
    @Option(names = {"-n", "--lines"}, defaultValue = "50",
              description = "Number of lines to show")
    private int lines;
    
    @Option(names = {"--coordinator"}, description = "Show coordinator logs")
    private boolean coordinator;
    
    @Option(names = {"--worker"}, description = "Show worker logs")
    private String workerId;
    
    @Option(names = {"--level"}, description = "Filter by log level (DEBUG, INFO, WARN, ERROR)")
    private String level;
    
    @Override
    public void run() {
        try {
            if (coordinator) {
                showCoordinatorLogs();
            } else if (workerId != null) {
                showWorkerLogs(workerId);
            } else {
                showAllLogs();
            }
        } catch (Exception e) {
            System.err.println("Failed to read logs: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private void showCoordinatorLogs() throws IOException {
        Path logFile = Paths.get("logs", "coordinator.log");
        if (!Files.exists(logFile)) {
            System.err.println("Coordinator log file not found: " + logFile);
            return;
        }
        
        System.out.println("=== Coordinator Logs ===");
        displayLogFile(logFile.toFile());
    }
    
    private void showWorkerLogs(String workerId) throws IOException {
        Path logFile = Paths.get("logs", "worker-" + workerId + ".log");
        if (!Files.exists(logFile)) {
            System.err.println("Worker log file not found: " + logFile);
            return;
        }
        
        System.out.println("=== Worker Logs: " + workerId + " ===");
        displayLogFile(logFile.toFile());
    }
    
    private void showAllLogs() throws IOException {
        Path logsDir = Paths.get("logs");
        if (!Files.exists(logsDir)) {
            System.err.println("Logs directory not found: " + logsDir);
            return;
        }
        
        System.out.println("=== Available Log Files ===");
        Files.list(logsDir)
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".log"))
            .sorted()
            .forEach(path -> {
                System.out.println("  " + path.getFileName());
            });
        
        System.out.println("\nUse --coordinator for coordinator logs or --worker <id> for specific worker logs.");
    }
    
    private void displayLogFile(File logFile) throws IOException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            int lineCount = 0;
            
            // Skip to last N lines if specified
            if (lines > 0) {
                java.util.List<String> allLines = new java.util.ArrayList<>();
                while ((line = reader.readLine()) != null) {
                    allLines.add(line);
                }
                
                int startIndex = Math.max(0, allLines.size() - lines);
                for (int i = startIndex; i < allLines.size(); i++) {
                    line = allLines.get(i);
                    if (shouldDisplayLine(line)) {
                        System.out.println(formatLogLine(line, dateFormat));
                    }
                }
            } else {
                while ((line = reader.readLine()) != null) {
                    if (shouldDisplayLine(line)) {
                        System.out.println(formatLogLine(line, dateFormat));
                    }
                }
            }
        }
        
        if (follow) {
            System.out.println("\nFollowing log output... (Ctrl+C to stop)");
            followLogFile(logFile);
        }
    }
    
    private boolean shouldDisplayLine(String line) {
        if (level == null) {
            return true;
        }
        
        // Simple level filtering - look for level indicators in log lines
        String upperLine = line.toUpperCase();
        String upperLevel = level.toUpperCase();
        
        return upperLine.contains(" " + upperLevel + " ") || 
               upperLine.startsWith(upperLevel + " ") ||
               upperLine.contains("[" + upperLevel + "]");
    }
    
    private String formatLogLine(String line, SimpleDateFormat dateFormat) {
        // Try to extract timestamp and format it nicely
        if (line.matches("^\\d{4}-\\d{2}-\\d{2}.*")) {
            // Already has timestamp, keep as is
            return line;
        }
        
        // Add current timestamp if not present
        return dateFormat.format(new Date()) + " " + line;
    }
    
    private void followLogFile(File logFile) {
        try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(logFile, "r")) {
            long fileLength = file.length();
            file.seek(fileLength);
            
            byte[] buffer = new byte[1024];
            while (true) {
                int bytesRead = file.read(buffer);
                if (bytesRead > 0) {
                    String chunk = new String(buffer, 0, bytesRead);
                    String[] lines = chunk.split("\n");
                    for (String line : lines) {
                        if (!line.isEmpty() && shouldDisplayLine(line)) {
                            System.out.println(formatLogLine(line, new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")));
                        }
                    }
                }
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            System.out.println("\nStopped following logs.");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("Error following logs: " + e.getMessage());
        }
    }
}
