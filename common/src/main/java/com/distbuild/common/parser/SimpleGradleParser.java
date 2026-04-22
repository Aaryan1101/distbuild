package com.distbuild.common.parser;

import com.distbuild.common.model.ModuleInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple Gradle project parser that works by examining the file system
 * and basic Gradle structure
 */
public class SimpleGradleParser {
    private static final Logger logger = LoggerFactory.getLogger(SimpleGradleParser.class);
    
    private final Path projectRoot;

    public SimpleGradleParser(Path projectRoot) {
        this.projectRoot = Objects.requireNonNull(projectRoot);
    }

    /**
     * Parses the Gradle project by examining settings.gradle and module directories
     */
    public GradleProject parseProject() throws IOException {
        logger.info("Parsing Gradle project at: {}", projectRoot);
        
        Map<String, ModuleInfo> modules = new HashMap<>();
        
        // Find all subdirectories that contain build.gradle or build.gradle.kts
        Set<Path> moduleDirectories = findModuleDirectories();
        
        for (Path moduleDir : moduleDirectories) {
            ModuleInfo moduleInfo = parseModule(moduleDir);
            if (moduleInfo != null) {
                modules.put(moduleInfo.getName(), moduleInfo);
            }
        }
        
        // Resolve dependencies by examining build files
        resolveDependencies(modules);
        
        logger.info("Parsed {} modules from Gradle project", modules.size());
        return new GradleProject(projectRoot, modules);
    }

    /**
     * Finds all module directories by looking for build.gradle files
     */
    private Set<Path> findModuleDirectories() throws IOException {
        Set<Path> moduleDirs = new HashSet<>();
        
        // Check root directory
        if (hasBuildFile(projectRoot)) {
            moduleDirs.add(projectRoot);
        }
        
        // Check subdirectories
        try (var stream = Files.list(projectRoot)) {
            List<Path> subDirs = stream
                .filter(Files::isDirectory)
                .filter(this::hasBuildFile)
                .collect(Collectors.toList());
            
            moduleDirs.addAll(subDirs);
        }
        
        return moduleDirs;
    }

    /**
     * Checks if a directory contains a build.gradle file
     */
    private boolean hasBuildFile(Path dir) {
        return Files.exists(dir.resolve("build.gradle")) || 
               Files.exists(dir.resolve("build.gradle.kts"));
    }

    /**
     * Parses a single module from its directory
     */
    private ModuleInfo parseModule(Path moduleDir) {
        try {
            String moduleName = getModuleName(moduleDir);
            
            // Find Java source files
            Set<String> sourceFiles = findJavaSources(moduleDir);
            
            // Skip modules without Java sources
            if (sourceFiles.isEmpty()) {
                logger.debug("Skipping module {} - no Java sources found", moduleName);
                return null;
            }
            
            // Default values for now - will be enhanced later
            Set<String> dependencies = new HashSet<>();
            Set<String> classpathJars = new HashSet<>();
            Map<String, String> compilerOptions = new HashMap<>();
            String javaVersion = "17";
            boolean hasAnnotationProcessors = false;
            
            // Try to extract basic information from build file
            Path buildFile = getBuildFile(moduleDir);
            if (buildFile != null) {
                dependencies = extractDependenciesFromBuildFile(buildFile);
                hasAnnotationProcessors = checkForAnnotationProcessors(buildFile);
            }
            
            Path outputDirectory = moduleDir.resolve("build/classes/java/main");
            
            return ModuleInfo.builder()
                    .name(moduleName)
                    .projectPath(moduleDir)
                    .dependencies(dependencies)
                    .sourceFiles(sourceFiles)
                    .classpathJars(classpathJars)
                    .outputDirectory(outputDirectory)
                    .compilerOptions(compilerOptions)
                    .javaVersion(javaVersion)
                    .hasAnnotationProcessors(hasAnnotationProcessors)
                    .build();
                    
        } catch (Exception e) {
            logger.warn("Failed to parse module at: {}", moduleDir, e);
            return null;
        }
    }

    /**
     * Gets the module name from the directory path
     */
    private String getModuleName(Path moduleDir) {
        if (moduleDir.equals(projectRoot)) {
            return "root";
        }
        return moduleDir.getFileName().toString();
    }

    /**
     * Finds all Java source files in a module directory
     */
    private Set<String> findJavaSources(Path moduleDir) throws IOException {
        Set<String> sourceFiles = new HashSet<>();
        
        Path srcDir = moduleDir.resolve("src/main/java");
        if (Files.exists(srcDir)) {
            try (var stream = Files.walk(srcDir)) {
                List<String> files = stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> moduleDir.relativize(path).toString())
                    .collect(Collectors.toList());
                
                sourceFiles.addAll(files);
            }
        }
        
        return sourceFiles;
    }

    /**
     * Gets the build file for a module (build.gradle or build.gradle.kts)
     */
    private Path getBuildFile(Path moduleDir) {
        Path gradleFile = moduleDir.resolve("build.gradle");
        if (Files.exists(gradleFile)) {
            return gradleFile;
        }
        
        Path kotlinFile = moduleDir.resolve("build.gradle.kts");
        if (Files.exists(kotlinFile)) {
            return kotlinFile;
        }
        
        return null;
    }

    /**
     * Extracts dependencies from a build file by simple text parsing
     */
    private Set<String> extractDependenciesFromBuildFile(Path buildFile) throws IOException {
        Set<String> dependencies = new HashSet<>();
        
        List<String> lines = Files.readAllLines(buildFile);
        
        for (String line : lines) {
            line = line.trim();
            
            // Look for project dependencies
            if (line.contains("implementation project(") || line.contains("compile project(")) {
                // Extract project name from quotes
                int start = line.indexOf('\'');
                if (start == -1) start = line.indexOf('"');
                if (start != -1) {
                    int end = line.indexOf('\'', start + 1);
                    if (end == -1) end = line.indexOf('"', start + 1);
                    if (end != -1) {
                        String projectPath = line.substring(start + 1, end);
                        // Convert project path to module name
                        String moduleName = projectPath.replace(":", "");
                        if (!moduleName.isEmpty() && !moduleName.equals("root")) {
                            dependencies.add(moduleName);
                        }
                    }
                }
            }
        }
        
        return dependencies;
    }

    /**
     * Checks if a build file contains annotation processor configuration
     */
    private boolean checkForAnnotationProcessors(Path buildFile) throws IOException {
        List<String> lines = Files.readAllLines(buildFile);
        
        for (String line : lines) {
            line = line.toLowerCase().trim();
            if (line.contains("annotationprocessor") || 
                line.contains("lombok") || 
                line.contains("mapstruct") || 
                line.contains("dagger")) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Resolves and validates dependencies between modules
     */
    private void resolveDependencies(Map<String, ModuleInfo> modules) {
        // Remove dependencies that don't exist in our module map
        for (ModuleInfo module : modules.values()) {
            Set<String> validDeps = module.getDependencies().stream()
                .filter(modules::containsKey)
                .collect(Collectors.toSet());
            
            // Update the module with filtered dependencies
            ModuleInfo updatedModule = ModuleInfo.builder()
                .name(module.getName())
                .projectPath(module.getProjectPath())
                .dependencies(validDeps)
                .sourceFiles(module.getSourceFiles())
                .classpathJars(module.getClasspathJars())
                .outputDirectory(module.getOutputDirectory())
                .compilerOptions(module.getCompilerOptions())
                .javaVersion(module.getJavaVersion())
                .hasAnnotationProcessors(module.hasAnnotationProcessors())
                .build();
            
            modules.put(module.getName(), updatedModule);
        }
    }

    /**
     * Represents a parsed Gradle project
     */
    public static class GradleProject {
        private final Path projectRoot;
        private final Map<String, ModuleInfo> modules;

        public GradleProject(Path projectRoot, Map<String, ModuleInfo> modules) {
            this.projectRoot = projectRoot;
            this.modules = new HashMap<>(modules);
        }

        public Path getProjectRoot() { return projectRoot; }
        public Map<String, ModuleInfo> getModules() { return new HashMap<>(modules); }
        public ModuleInfo getModule(String name) { return modules.get(name); }
        public Set<String> getModuleNames() { return modules.keySet(); }
        public int getModuleCount() { return modules.size(); }

        @Override
        public String toString() {
            return "GradleProject{" +
                    "projectRoot=" + projectRoot +
                    ", modules=" + modules.keySet() +
                    '}';
        }
    }
}
