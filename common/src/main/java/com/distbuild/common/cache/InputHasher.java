package com.distbuild.common.cache;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes content-addressed hashes for build inputs
 */
public class InputHasher {
    private static final Logger logger = LoggerFactory.getLogger(InputHasher.class);
    
    private final Path projectRoot;
    private final boolean includeIncrementalState;

    public InputHasher(Path projectRoot) {
        this(projectRoot, true);
    }

    public InputHasher(Path projectRoot, boolean includeIncrementalState) {
        this.projectRoot = Objects.requireNonNull(projectRoot);
        this.includeIncrementalState = includeIncrementalState;
    }

    public String computeHash(ModuleInputs inputs) throws IOException {
        logger.debug("Computing hash for module: {}", inputs.getModuleName());
        
        MessageDigest md = DigestUtils.getSha256Digest();
        
        // Add module name
        updateDigest(md, inputs.getModuleName());
        
        // Add Java version
        updateDigest(md, inputs.getJavaVersion());
        
        // Add compiler options (sorted for consistency)
        Map<String, String> options = inputs.getCompilerOptions();
        options.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    updateDigest(md, entry.getKey());
                    updateDigest(md, entry.getValue());
                });
        
        // Add source files (sorted for consistency)
        List<String> sourceFiles = new ArrayList<>(inputs.getSourceFiles());
        Collections.sort(sourceFiles);
        for (String sourceFile : sourceFiles) {
            hashFile(md, projectRoot.resolve(sourceFile));
        }
        
        // Add classpath JARs (by hash, not by path)
        List<String> jarHashes = new ArrayList<>(inputs.getJarHashes().values());
        Collections.sort(jarHashes);
        for (String jarHash : jarHashes) {
            updateDigest(md, jarHash);
        }
        
        // Add incremental state if available and enabled
        if (includeIncrementalState && inputs.getIncrementalState() != null) {
            updateDigest(md, inputs.getIncrementalState());
        }
        
        String hash = DigestUtils.sha256Hex(md.digest());
        logger.debug("Computed hash for {}: {}", inputs.getModuleName(), hash);
        return hash;
    }

    public String computeJarHash(Path jarPath) throws IOException {
        if (!Files.exists(jarPath)) {
            throw new IOException("JAR file does not exist: " + jarPath);
        }
        
        logger.debug("Computing hash for JAR: {}", jarPath);
        
        try (InputStream is = Files.newInputStream(jarPath)) {
            String hash = DigestUtils.sha256Hex(is);
            logger.debug("JAR hash for {}: {}", jarPath, hash);
            return hash;
        }
    }

    public Map<String, String> computeJarHashes(Set<String> jarPaths) throws IOException {
        Map<String, String> hashes = new HashMap<>();
        
        for (String jarPath : jarPaths) {
            Path fullPath = projectRoot.resolve(jarPath);
            try {
                String hash = computeJarHash(fullPath);
                hashes.put(jarPath, hash);
            } catch (IOException e) {
                logger.warn("Failed to compute hash for JAR {}: {}", jarPath, e.getMessage());
                throw e;
            }
        }
        
        return hashes;
    }

    private void hashFile(MessageDigest md, Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            throw new IOException("Source file does not exist: " + filePath);
        }
        
        logger.trace("Hashing file: {}", filePath);
        
        try (InputStream is = Files.newInputStream(filePath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }
    }

    private void updateDigest(MessageDigest md, String value) {
        if (value != null) {
            md.update(value.getBytes());
        }
    }

    private void updateDigest(MessageDigest md, byte[] value) {
        if (value != null) {
            md.update(value);
        }
    }

    /**
     * Represents the inputs for a module compilation
     */
    public static class ModuleInputs {
        private final String moduleName;
        private final Set<String> sourceFiles;
        private final Set<String> classpathJars;
        private final Map<String, String> jarHashes;
        private final Map<String, String> compilerOptions;
        private final String javaVersion;
        private final byte[] incrementalState;

        public ModuleInputs(String moduleName, Set<String> sourceFiles, Set<String> classpathJars,
                           Map<String, String> jarHashes, Map<String, String> compilerOptions,
                           String javaVersion, byte[] incrementalState) {
            this.moduleName = Objects.requireNonNull(moduleName);
            this.sourceFiles = new HashSet<>(sourceFiles);
            this.classpathJars = new HashSet<>(classpathJars);
            this.jarHashes = new HashMap<>(jarHashes);
            this.compilerOptions = new HashMap<>(compilerOptions);
            this.javaVersion = Objects.requireNonNull(javaVersion);
            this.incrementalState = incrementalState;
        }

        public String getModuleName() { return moduleName; }
        public Set<String> getSourceFiles() { return new HashSet<>(sourceFiles); }
        public Set<String> getClasspathJars() { return new HashSet<>(classpathJars); }
        public Map<String, String> getJarHashes() { return new HashMap<>(jarHashes); }
        public Map<String, String> getCompilerOptions() { return new HashMap<>(compilerOptions); }
        public String getJavaVersion() { return javaVersion; }
        public byte[] getIncrementalState() { return incrementalState; }

        @Override
        public String toString() {
            return "ModuleInputs{" +
                    "moduleName='" + moduleName + '\'' +
                    ", sourceFiles=" + sourceFiles.size() +
                    ", classpathJars=" + classpathJars.size() +
                    ", javaVersion='" + javaVersion + '\'' +
                    '}';
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String moduleName;
            private Set<String> sourceFiles = new HashSet<>();
            private Set<String> classpathJars = new HashSet<>();
            private Map<String, String> jarHashes = new HashMap<>();
            private Map<String, String> compilerOptions = new HashMap<>();
            private String javaVersion = "17";
            private byte[] incrementalState;

            public Builder moduleName(String moduleName) {
                this.moduleName = moduleName;
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

            public Builder jarHashes(Map<String, String> jarHashes) {
                this.jarHashes = new HashMap<>(jarHashes);
                return this;
            }

            public Builder addJarHash(String jarPath, String hash) {
                this.jarHashes.put(jarPath, hash);
                return this;
            }

            public Builder compilerOptions(Map<String, String> compilerOptions) {
                this.compilerOptions = new HashMap<>(compilerOptions);
                return this;
            }

            public Builder addCompilerOption(String key, String value) {
                this.compilerOptions.put(key, value);
                return this;
            }

            public Builder javaVersion(String javaVersion) {
                this.javaVersion = javaVersion;
                return this;
            }

            public Builder incrementalState(byte[] incrementalState) {
                this.incrementalState = incrementalState;
                return this;
            }

            public ModuleInputs build() {
                return new ModuleInputs(moduleName, sourceFiles, classpathJars, 
                                     jarHashes, compilerOptions, javaVersion, incrementalState);
            }
        }
    }
}
