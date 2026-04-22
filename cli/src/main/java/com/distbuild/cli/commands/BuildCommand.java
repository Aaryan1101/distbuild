package com.distbuild.cli.commands;

import com.distbuild.cli.config.CliConfig;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Command(
    name = "build",
    description = "Trigger a distributed build"
)
public class BuildCommand implements Runnable {
    
    @Option(names = {"-t", "--type"}, defaultValue = "DEBUG", 
              description = "Build type: DEBUG or RELEASE")
    private BuildType buildType;
    
    @Option(names = {"--no-cache"}, description = "Skip cache lookup")
    private boolean noCache;
    
    @Option(names = {"-w", "--workers"}, defaultValue = "0", 
              description = "Max workers (0 = auto)")
    private int workers;
    
    @Option(names = {"-v", "--verbose"})
    private boolean verbose;
    
    @Option(names = {"--dry-run"})
    private boolean dryRun;
    
    @Override
    public void run() {
        try {
            var config = CliConfig.load();
            
            System.out.println("Starting distributed build...");
            System.out.println("Build type: " + buildType);
            System.out.println("Max workers: " + (workers == 0 ? "auto" : workers));
            System.out.println("Cache: " + (noCache ? "disabled" : "enabled"));
            System.out.println("Dry run: " + dryRun);
            
            if (dryRun) {
                System.out.println("DRY RUN: Not actually building");
                return;
            }
            
            // For now, just show what would be built
            System.out.println("Project root: " + Paths.get("").toAbsolutePath());
            System.out.println("Note: Full distributed build integration coming soon!");
            
        } catch (Exception e) {
            System.err.println("Build failed: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }
    
    public enum BuildType {
        DEBUG, RELEASE;
        
        public String compilerFlags() {
            return this == DEBUG ? "-g" : "-O";
        }
        
        public String outputDir() {
            return "build/" + name().toLowerCase() + "/";
        }
        
        public String cacheKeySuffix() {
            return "#" + name().toLowerCase();
        }
    }
}
