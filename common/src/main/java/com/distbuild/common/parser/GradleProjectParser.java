package com.distbuild.common.parser;

import com.distbuild.common.model.ModuleInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Parses Gradle project structure and extracts module information
 */
public class GradleProjectParser {
    private static final Logger logger = LoggerFactory.getLogger(GradleProjectParser.class);
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path projectRoot;

    public GradleProjectParser(Path projectRoot) {
        this.projectRoot = Objects.requireNonNull(projectRoot);
    }

    /**
     * Parses the entire multi-module Gradle project
     */
    public GradleProject parseProject() throws IOException {
        logger.info("Parsing Gradle project at: {}", projectRoot);
        
        // First, generate project information using Gradle
        Map<String, ModuleInfo> modules = extractModulesFromGradle();
        
        return new GradleProject(projectRoot, modules);
    }

    /**
     * Extracts module information by running Gradle commands
     */
    private Map<String, ModuleInfo> extractModulesFromGradle() throws IOException {
        Map<String, ModuleInfo> modules = new HashMap<>();
        
        // Generate project information using Gradle
        Path projectInfoFile = generateProjectInfoFile();
        
        try {
            JsonNode projectInfo = objectMapper.readTree(projectInfoFile.toFile());
            
            // Parse each module
            for (JsonNode moduleNode : projectInfo) {
                ModuleInfo moduleInfo = parseModule(moduleNode);
                if (moduleInfo != null) {
                    modules.put(moduleInfo.getName(), moduleInfo);
                }
            }
            
            logger.info("Parsed {} modules from Gradle project", modules.size());
            
        } finally {
            // Clean up temporary file
            Files.deleteIfExists(projectInfoFile);
        }
        
        return modules;
    }

    /**
     * Generates project information using Gradle
     */
    private Path generateProjectInfoFile() throws IOException {
        Path tempFile = Files.createTempFile("gradle-project-info", ".json");
        
        // Run Gradle to extract project information
        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(projectRoot.toFile());
        pb.command(
            System.getProperty("os.name").toLowerCase().contains("win") ? "gradlew.bat" : "./gradlew",
            "projects",
            "--configuration-cache",
            "--quiet",
            "--console=plain"
        );
        
        // Redirect output to our temp file
        pb.redirectOutput(tempFile.toFile());
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        
        try {
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                throw new IOException("Gradle command failed with exit code: " + exitCode);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Gradle command interrupted", e);
        }
        
        return tempFile;
    }

    /**
     * Parses a single module from JSON node
     */
    private ModuleInfo parseModule(JsonNode moduleNode) {
        try {
            String name = moduleNode.path("name").asText();
            String path = moduleNode.path("path").asText();
            
            // Skip root project if it doesn't have source code
            if (name.equals("root") && !hasJavaSources(projectRoot.resolve(path))) {
                return null;
            }
            
            // Extract dependencies
            Set<String> dependencies = extractDependencies(moduleNode);
            
            // Extract source files
            Set<String> sourceFiles = findJavaSources(projectRoot.resolve(path));
            
            // Extract classpath JARs
            Set<String> classpathJars = extractClasspathJars(name);
            
            // Extract compiler options
            Map<String, String> compilerOptions = extractCompilerOptions(moduleNode);
            
            // Check for annotation processors
            boolean hasAnnotationProcessors = hasAnnotationProcessors(moduleNode);
            
            // Get Java version
            String javaVersion = extractJavaVersion(moduleNode);
            
            Path modulePath = projectRoot.resolve(path);
            Path outputDirectory = modulePath.resolve("build/classes/java/main");
            
            return ModuleInfo.builder()
                    .name(name)
                    .projectPath(modulePath)
                    .dependencies(dependencies)
                    .sourceFiles(sourceFiles)
                    .classpathJars(classpathJars)
                    .outputDirectory(outputDirectory)
                    .compilerOptions(compilerOptions)
                    .javaVersion(javaVersion)
                    .hasAnnotationProcessors(hasAnnotationProcessors)
                    .build();
                    
        } catch (Exception e) {
            logger.warn("Failed to parse module: {}", moduleNode, e);
            return null;
        }
    }

    /**
     * Extracts module dependencies from Gradle project info
     */
    private Set<String> extractDependencies(JsonNode moduleNode) {
        Set<String> dependencies = new HashSet<>();
        
        JsonNode dependenciesNode = moduleNode.path("dependencies");
        if (dependenciesNode.isArray()) {
            for (JsonNode dep : dependenciesNode) {
                String depName = dep.path("name").asText();
                // Only include project dependencies, not external libraries
                if (dep.path("group").asText().isEmpty() && !depName.startsWith("project :")) {
                    dependencies.add(depName);
                }
            }
        }
        
        return dependencies;
    }

    /**
     * Finds all Java source files in a module
     */
    private Set<String> findJavaSources(Path modulePath) throws IOException {
        Set<String> sourceFiles = new HashSet<>();
        
        Path srcDir = modulePath.resolve("src/main/java");
        if (Files.exists(srcDir)) {
            Files.walk(srcDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> sourceFiles.add(modulePath.relativize(path).toString()));
        }
        
        return sourceFiles;
    }

    /**
     * Checks if a directory contains Java source files
     */
    private boolean hasJavaSources(Path modulePath) {
        Path srcDir = modulePath.resolve("src/main/java");
        if (!Files.exists(srcDir)) {
            return false;
        }
        
        try {
            return Files.walk(srcDir)
                .anyMatch(path -> path.toString().endsWith(".java"));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Extracts classpath JARs for a module
     */
    private Set<String> extractClasspathJars(String moduleName) throws IOException {
        Set<String> classpathJars = new HashSet<>();
        
        Path classpathFile = generateClasspathInfo(moduleName);
        
        try {
            List<String> lines = Files.readAllLines(classpathFile);
            for (String line : lines) {
                if (line.endsWith(".jar")) {
                    // Convert absolute path to project-relative path if possible
                    Path jarPath = Path.of(line);
                    if (jarPath.startsWith(projectRoot)) {
                        classpathJars.add(projectRoot.relativize(jarPath).toString());
                    } else {
                        classpathJars.add(line);
                    }
                }
            }
        } finally {
            Files.deleteIfExists(classpathFile);
        }
        
        return classpathJars;
    }

    /**
     * Generates classpath information for a module
     */
    private Path generateClasspathInfo(String moduleName) throws IOException {
        Path tempFile = Files.createTempFile("gradle-classpath-" + moduleName, ".txt");
        
        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(projectRoot.toFile());
        pb.command(
            System.getProperty("os.name").toLowerCase().contains("win") ? "gradlew.bat" : "./gradlew",
            ":" + moduleName + ":compileJava",
            "--configuration-cache",
            "--quiet",
            "--console=plain",
            "--dry-run"
        );
        
        pb.redirectOutput(tempFile.toFile());
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        
        try {
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                throw new IOException("Gradle classpath command failed with exit code: " + exitCode);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Gradle classpath command interrupted", e);
        }
        
        return tempFile;
    }

    /**
     * Extracts compiler options from module configuration
     */
    private Map<String, String> extractCompilerOptions(JsonNode moduleNode) {
        Map<String, String> options = new HashMap<>();
        
        // Extract Java compiler options
        JsonNode compileOptions = moduleNode.path("compileOptions");
        if (compileOptions.isObject()) {
            compileOptions.fields().forEachRemaining(entry -> {
                options.put(entry.getKey(), entry.getValue().asText());
            });
        }
        
        return options;
    }

    /**
     * Checks if module uses annotation processors
     */
    private boolean hasAnnotationProcessors(JsonNode moduleNode) {
        JsonNode annotationProcessors = moduleNode.path("annotationProcessors");
        return annotationProcessors.isArray() && annotationProcessors.size() > 0;
    }

    /**
     * Extracts Java version for the module
     */
    private String extractJavaVersion(JsonNode moduleNode) {
        JsonNode javaVersion = moduleNode.path("javaVersion");
        return javaVersion.asText("17"); // Default to Java 17
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
