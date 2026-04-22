package com.distbuild.common.cache;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Represents the result of a compilation operation
 */
public class CompileResult {
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    
    private final boolean success;
    private final String errorMessage;
    private final Set<String> compiledFiles;
    private final Set<String> classFiles;
    private final long compileTimeMs;
    private final byte[] incrementalState;
    private final Instant timestamp;
    private final Map<String, byte[]> classFileContents;
    private final String javaVersion;
    private final Map<String, String> metadata;

    @JsonCreator
    private CompileResult(
            @JsonProperty("success") boolean success,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("compiledFiles") Set<String> compiledFiles,
            @JsonProperty("classFiles") Set<String> classFiles,
            @JsonProperty("compileTimeMs") long compileTimeMs,
            @JsonProperty("incrementalState") byte[] incrementalState,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("classFileContents") Map<String, byte[]> classFileContents,
            @JsonProperty("javaVersion") String javaVersion,
            @JsonProperty("metadata") Map<String, String> metadata) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.compiledFiles = compiledFiles != null ? new HashSet<>(compiledFiles) : new HashSet<>();
        this.classFiles = classFiles != null ? new HashSet<>(classFiles) : new HashSet<>();
        this.compileTimeMs = compileTimeMs;
        this.incrementalState = incrementalState;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.classFileContents = classFileContents != null ? new HashMap<>(classFileContents) : new HashMap<>();
        this.javaVersion = javaVersion != null ? javaVersion : "17";
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    private CompileResult(Builder builder) {
        this(builder.success, builder.errorMessage, builder.compiledFiles, builder.classFiles,
             builder.compileTimeMs, builder.incrementalState, builder.timestamp, 
             builder.classFileContents, builder.javaVersion, builder.metadata);
    }

    @JsonProperty("success")
    public boolean isSuccess() { return success; }

    @JsonProperty("errorMessage")
    public String getErrorMessage() { 
        return errorMessage; 
    }
    
    @JsonIgnore
    public Optional<String> getErrorMessageOptional() { 
        return Optional.ofNullable(errorMessage); 
    }

    @JsonProperty("compiledFiles")
    public Set<String> getCompiledFiles() { 
        return new HashSet<>(compiledFiles); 
    }

    @JsonProperty("classFiles")
    public Set<String> getClassFiles() { 
        return new HashSet<>(classFiles); 
    }

    @JsonProperty("compileTimeMs")
    public long getCompileTimeMs() { return compileTimeMs; }

    @JsonProperty("incrementalState")
    public byte[] getIncrementalState() { 
        return incrementalState; 
    }
    
    @JsonIgnore
    public Optional<byte[]> getIncrementalStateOptional() { 
        return Optional.ofNullable(incrementalState); 
    }

    @JsonProperty("timestamp")
    public Instant getTimestamp() { return timestamp; }

    @JsonProperty("classFileContents")
    public Map<String, byte[]> getClassFileContents() { 
        return new HashMap<>(classFileContents); 
    }

    @JsonProperty("javaVersion")
    public String getJavaVersion() { return javaVersion; }

    @JsonProperty("metadata")
    public Map<String, String> getMetadata() { 
        return new HashMap<>(metadata); 
    }

    /**
     * Serializes the compile result to JSON
     */
    public byte[] toJson() throws IOException {
        return mapper.writeValueAsBytes(this);
    }

    /**
     * Deserializes the compile result from JSON
     */
    public static CompileResult fromJson(byte[] json) throws IOException {
        return mapper.readValue(json, CompileResult.class);
    }

    /**
     * Creates a successful compile result
     */
    public static CompileResult success(Set<String> compiledFiles, Set<String> classFiles,
                                       Map<String, byte[]> classFileContents, long compileTimeMs) {
        return new Builder()
                .success(true)
                .compiledFiles(compiledFiles)
                .classFiles(classFiles)
                .classFileContents(classFileContents)
                .compileTimeMs(compileTimeMs)
                .build();
    }

    /**
     * Creates a failed compile result
     */
    public static CompileResult failure(String errorMessage, long compileTimeMs) {
        return new Builder()
                .success(false)
                .errorMessage(errorMessage)
                .compileTimeMs(compileTimeMs)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompileResult that = (CompileResult) o;
        return success == that.success &&
                compileTimeMs == that.compileTimeMs &&
                Objects.equals(errorMessage, that.errorMessage) &&
                Objects.equals(compiledFiles, that.compiledFiles) &&
                Objects.equals(classFiles, that.classFiles) &&
                Arrays.equals(incrementalState, that.incrementalState) &&
                Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(classFileContents, that.classFileContents) &&
                Objects.equals(javaVersion, that.javaVersion) &&
                Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, errorMessage, compiledFiles, classFiles, 
                           compileTimeMs, timestamp, javaVersion, metadata);
    }

    @Override
    public String toString() {
        return "CompileResult{" +
                "success=" + success +
                ", compiledFiles=" + compiledFiles.size() +
                ", classFiles=" + classFiles.size() +
                ", compileTimeMs=" + compileTimeMs +
                ", timestamp=" + timestamp +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean success = false;
        private String errorMessage;
        private Set<String> compiledFiles = new HashSet<>();
        private Set<String> classFiles = new HashSet<>();
        private long compileTimeMs = 0;
        private byte[] incrementalState;
        private Instant timestamp;
        private Map<String, byte[]> classFileContents = new HashMap<>();
        private String javaVersion = "17";
        private Map<String, String> metadata = new HashMap<>();

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder compiledFiles(Set<String> compiledFiles) {
            this.compiledFiles = new HashSet<>(compiledFiles);
            return this;
        }

        public Builder addCompiledFile(String compiledFile) {
            this.compiledFiles.add(compiledFile);
            return this;
        }

        public Builder classFiles(Set<String> classFiles) {
            this.classFiles = new HashSet<>(classFiles);
            return this;
        }

        public Builder addClassFile(String classFile) {
            this.classFiles.add(classFile);
            return this;
        }

        public Builder compileTimeMs(long compileTimeMs) {
            this.compileTimeMs = compileTimeMs;
            return this;
        }

        public Builder incrementalState(byte[] incrementalState) {
            this.incrementalState = incrementalState;
            return this;
        }
        
        public Optional<byte[]> getIncrementalState() {
            return Optional.ofNullable(incrementalState);
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder classFileContents(Map<String, byte[]> classFileContents) {
            this.classFileContents = new HashMap<>(classFileContents);
            return this;
        }

        public Builder addClassFileContent(String path, byte[] content) {
            this.classFileContents.put(path, content);
            return this;
        }

        public Builder javaVersion(String javaVersion) {
            this.javaVersion = javaVersion;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = new HashMap<>(metadata);
            return this;
        }

        public Builder addMetadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public CompileResult build() {
            return new CompileResult(this);
        }
    }
}
