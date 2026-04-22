package com.distbuild.common.model;

import java.util.*;
import java.nio.file.Path;

/**
 * Represents a single module in a Gradle project
 */
public class ModuleInfo {
    private final String name;
    private final Path projectPath;
    private final Set<String> dependencies;
    private final Set<String> sourceFiles;
    private final Set<String> classpathJars;
    private final Path outputDirectory;
    private final Map<String, String> compilerOptions;
    private final String javaVersion;
    private final boolean hasAnnotationProcessors;

    public ModuleInfo(String name, Path projectPath, Set<String> dependencies, 
                     Set<String> sourceFiles, Set<String> classpathJars,
                     Path outputDirectory, Map<String, String> compilerOptions,
                     String javaVersion, boolean hasAnnotationProcessors) {
        this.name = Objects.requireNonNull(name);
        this.projectPath = Objects.requireNonNull(projectPath);
        this.dependencies = new HashSet<>(dependencies);
        this.sourceFiles = new HashSet<>(sourceFiles);
        this.classpathJars = new HashSet<>(classpathJars);
        this.outputDirectory = Objects.requireNonNull(outputDirectory);
        this.compilerOptions = new HashMap<>(compilerOptions);
        this.javaVersion = Objects.requireNonNull(javaVersion);
        this.hasAnnotationProcessors = hasAnnotationProcessors;
    }

    public String getName() { return name; }
    public Path getProjectPath() { return projectPath; }
    public Set<String> getDependencies() { return new HashSet<>(dependencies); }
    public Set<String> getSourceFiles() { return new HashSet<>(sourceFiles); }
    public Set<String> getClasspathJars() { return new HashSet<>(classpathJars); }
    public Path getOutputDirectory() { return outputDirectory; }
    public Map<String, String> getCompilerOptions() { return new HashMap<>(compilerOptions); }
    public String getJavaVersion() { return javaVersion; }
    public boolean hasAnnotationProcessors() { return hasAnnotationProcessors; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModuleInfo that = (ModuleInfo) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "ModuleInfo{" +
                "name='" + name + '\'' +
                ", dependencies=" + dependencies +
                ", sourceFiles=" + sourceFiles.size() +
                ", classpathJars=" + classpathJars.size() +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private Path projectPath;
        private Set<String> dependencies = new HashSet<>();
        private Set<String> sourceFiles = new HashSet<>();
        private Set<String> classpathJars = new HashSet<>();
        private Path outputDirectory;
        private Map<String, String> compilerOptions = new HashMap<>();
        private String javaVersion = "17";
        private boolean hasAnnotationProcessors = false;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder projectPath(Path projectPath) {
            this.projectPath = projectPath;
            return this;
        }

        public Builder addDependency(String dependency) {
            this.dependencies.add(dependency);
            return this;
        }

        public Builder dependencies(Set<String> dependencies) {
            this.dependencies = new HashSet<>(dependencies);
            return this;
        }

        public Builder addSourceFile(String sourceFile) {
            this.sourceFiles.add(sourceFile);
            return this;
        }

        public Builder sourceFiles(Set<String> sourceFiles) {
            this.sourceFiles = new HashSet<>(sourceFiles);
            return this;
        }

        public Builder addClasspathJar(String jar) {
            this.classpathJars.add(jar);
            return this;
        }

        public Builder classpathJars(Set<String> classpathJars) {
            this.classpathJars = new HashSet<>(classpathJars);
            return this;
        }

        public Builder outputDirectory(Path outputDirectory) {
            this.outputDirectory = outputDirectory;
            return this;
        }

        public Builder compilerOption(String key, String value) {
            this.compilerOptions.put(key, value);
            return this;
        }

        public Builder compilerOptions(Map<String, String> compilerOptions) {
            this.compilerOptions = new HashMap<>(compilerOptions);
            return this;
        }

        public Builder javaVersion(String javaVersion) {
            this.javaVersion = javaVersion;
            return this;
        }

        public Builder hasAnnotationProcessors(boolean hasAnnotationProcessors) {
            this.hasAnnotationProcessors = hasAnnotationProcessors;
            return this;
        }

        public ModuleInfo build() {
            return new ModuleInfo(name, projectPath, dependencies, sourceFiles, 
                                classpathJars, outputDirectory, compilerOptions, 
                                javaVersion, hasAnnotationProcessors);
        }
    }
}
