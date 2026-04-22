package com.distbuild.coordinator.android;

import com.distbuild.coordinator.BuildCoordinator;
import com.distbuild.coordinator.android.AndroidJarCache;
import com.distbuild.common.grpc.BuildProto;
import com.distbuild.common.grpc.BuildServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Handles Android-specific build coordination
 */
public class AndroidBuildService {
    private static final Logger logger = LoggerFactory.getLogger(AndroidBuildService.class);
    
    private final BuildCoordinator coordinator;
    private final AndroidJarCache androidJarCache;
    
    public AndroidBuildService(BuildCoordinator coordinator, AndroidJarCache androidJarCache) {
        this.coordinator = coordinator;
        this.androidJarCache = androidJarCache;
    }
    
    /**
     * Build Android module using hybrid approach (Java distributed, Android local)
     */
    public CompletableFuture<AndroidBuildResult> buildAndroidModule(AndroidBuildRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Starting Android build for module: {} (type: {})", 
                           request.moduleName, request.moduleType);
                
                // Step 1: Ensure android.jar is available to workers
                if (!ensureAndroidJarAvailable(request.compileSdkVersion)) {
                    return AndroidBuildResult.failure("Failed to ensure android.jar availability");
                }
                
                // Step 2: Build Java components using distributed workers
                JavaBuildResult javaResult = buildJavaComponents(request);
                if (!javaResult.success) {
                    return AndroidBuildResult.failure("Java compilation failed: " + javaResult.errorMessage);
                }
                
                // Step 3: Handle Android-specific tasks locally
                AndroidLocalBuildResult localResult = handleAndroidLocalTasks(request, javaResult);
                if (!localResult.success) {
                    return AndroidBuildResult.failure("Android local tasks failed: " + localResult.errorMessage);
                }
                
                // Step 4: Assemble final APK/AAB
                String outputPath = assembleFinalArtifact(request, javaResult, localResult);
                if (outputPath == null) {
                    return AndroidBuildResult.failure("Failed to assemble final artifact");
                }
                
                return AndroidBuildResult.success(outputPath);
                
            } catch (Exception e) {
                logger.error("Android build failed for module: {}", request.moduleName, e);
                return AndroidBuildResult.failure("Build error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Ensure android.jar is available to workers
     */
    private boolean ensureAndroidJarAvailable(int compileSdkVersion) {
        try {
            return androidJarCache.ensureAndroidJarAvailable(compileSdkVersion).get();
        } catch (Exception e) {
            logger.error("Failed to ensure android.jar availability", e);
            return false;
        }
    }
    
    /**
     * Build Java components using distributed workers
     */
    private JavaBuildResult buildJavaComponents(AndroidBuildRequest request) {
        try {
            logger.info("Building Java components for {} using distributed workers", request.moduleName);
            
            // Create compile task for Java sources
            BuildProto.CompileTaskRequest javaTask = createJavaCompileTask(request);
            
            // Send to worker
            String workerId = selectWorkerForJavaCompilation(request);
            if (workerId == null) {
                return JavaBuildResult.failure("No available workers for Java compilation");
            }
            
            // Execute on worker
            BuildProto.CompileTaskResponse response = executeTaskOnWorker(workerId, javaTask);
            
            if (response.getSuccess()) {
                logger.info("Java compilation completed successfully for {}", request.moduleName);
                // Use the output directory from the request since response doesn't have it
                return JavaBuildResult.success(request.javaOutputDir);
            } else {
                return JavaBuildResult.failure(response.getErrorMessage());
            }
            
        } catch (Exception e) {
            logger.error("Java compilation failed for {}", request.moduleName, e);
            return JavaBuildResult.failure("Java compilation error: " + e.getMessage());
        }
    }
    
    /**
     * Create Java compile task request
     */
    private BuildProto.CompileTaskRequest createJavaCompileTask(AndroidBuildRequest request) {
        String taskId = "android-java-" + request.moduleName + "-" + System.currentTimeMillis();
        String androidJarCacheKey = androidJarCache.getAndroidJarCacheKey(request.compileSdkVersion);
        
        BuildProto.CompileTaskRequest.Builder builder = BuildProto.CompileTaskRequest.newBuilder()
            .setTaskId(taskId)
            .setModuleName(request.moduleName)
            .setJavaVersion(request.javaVersion)
            .addAllSourceFiles(request.javaSourceFiles)
            .addAllClasspathJars(request.javaClasspath)
            .setOutputDirectory(request.javaOutputDir)
            .putCompilerOptions("android", "true")
            .putCompilerOptions("android.jar.cache.key", androidJarCacheKey)
            .putCompilerOptions("compile.sdk.version", String.valueOf(request.compileSdkVersion));
        
        // Add Android-specific classpath entries
        builder.addAllClasspathJars(createAndroidClasspath(request));
        
        return builder.build();
    }
    
    /**
     * Create Android-specific classpath
     */
    private List<String> createAndroidClasspath(AndroidBuildRequest request) {
        List<String> androidClasspath = new ArrayList<>();
        
        // Add android.jar (via cache)
        String androidJarCacheKey = androidJarCache.getAndroidJarCacheKey(request.compileSdkVersion);
        androidClasspath.add("cache://" + androidJarCacheKey);
        
        // Add additional Android libraries
        androidClasspath.add("cache://android-annotations.jar");
        androidClasspath.add("cache://android-data.jar");
        
        return androidClasspath;
    }
    
    /**
     * Select appropriate worker for Java compilation
     */
    private String selectWorkerForJavaCompilation(AndroidBuildRequest request) {
        // For now, select any available worker
        // In future, could consider worker specialization, load, etc.
        List<String> availableWorkers = coordinator.getAvailableWorkers();
        
        if (availableWorkers.isEmpty()) {
            return null;
        }
        
        // Prefer workers with Android specialization if available
        for (String workerId : availableWorkers) {
            if (coordinator.isWorkerSpecialized(workerId, "android-compilation")) {
                return workerId;
            }
        }
        
        // Fall back to any available worker
        return availableWorkers.get(0);
    }
    
    /**
     * Execute task on worker
     */
    private BuildProto.CompileTaskResponse executeTaskOnWorker(String workerId, BuildProto.CompileTaskRequest task) {
        // Get worker connection
        ManagedChannel channel = coordinator.getWorkerChannel(workerId);
        if (channel == null) {
            return BuildProto.CompileTaskResponse.newBuilder()
                .setTaskId(task.getTaskId())
                .setSuccess(false)
                .setErrorMessage("Cannot connect to worker: " + workerId)
                .build();
        }
        
        try {
            BuildServiceGrpc.BuildServiceBlockingStub stub = BuildServiceGrpc.newBlockingStub(channel);
            return stub.compileTask(task);
        } catch (Exception e) {
            logger.error("Failed to execute task on worker: {}", workerId, e);
            return BuildProto.CompileTaskResponse.newBuilder()
                .setTaskId(task.getTaskId())
                .setSuccess(false)
                .setErrorMessage("Worker communication error: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * Handle Android-specific tasks locally (dex, resources, etc.)
     */
    private AndroidLocalBuildResult handleAndroidLocalTasks(AndroidBuildRequest request, JavaBuildResult javaResult) {
        try {
            logger.info("Handling Android local tasks for {}", request.moduleName);
            
            // This would run locally on the coordinator machine
            // For now, simulate the process
            
            // Step 1: Process Android resources
            if (!processAndroidResources(request)) {
                return AndroidLocalBuildResult.failure("Resource processing failed");
            }
            
            // Step 2: Generate R.java if needed
            if (request.moduleType == AndroidModuleType.APPLICATION) {
                if (!generateRJava(request)) {
                    return AndroidLocalBuildResult.failure("R.java generation failed");
                }
            }
            
            // Step 3: Dex compilation
            String dexOutput = performDexCompilation(request, javaResult.outputDirectory);
            if (dexOutput == null) {
                return AndroidLocalBuildResult.failure("Dex compilation failed");
            }
            
            // Step 4: For libraries, create AAR
            if (request.moduleType == AndroidModuleType.LIBRARY) {
                String aarOutput = createAar(request, javaResult.outputDirectory, dexOutput);
                if (aarOutput == null) {
                    return AndroidLocalBuildResult.failure("AAR creation failed");
                }
                return AndroidLocalBuildResult.success(aarOutput, "aar");
            }
            
            return AndroidLocalBuildResult.success(dexOutput, "dex");
            
        } catch (Exception e) {
            logger.error("Android local tasks failed for {}", request.moduleName, e);
            return AndroidLocalBuildResult.failure("Local tasks error: " + e.getMessage());
        }
    }
    
    /**
     * Process Android resources (placeholder implementation)
     */
    private boolean processAndroidResources(AndroidBuildRequest request) {
        // TODO: Implement aapt2 resource processing
        logger.debug("Processing Android resources for {}", request.moduleName);
        return true;
    }
    
    /**
     * Generate R.java (placeholder implementation)
     */
    private boolean generateRJava(AndroidBuildRequest request) {
        // TODO: Implement R.java generation
        logger.debug("Generating R.java for {}", request.moduleName);
        return true;
    }
    
    /**
     * Perform dex compilation (placeholder implementation)
     */
    private String performDexCompilation(AndroidBuildRequest request, String javaOutputDir) {
        // TODO: Implement d8 dex compilation
        String dexOutput = request.outputDirectory + "/dex";
        logger.debug("Performing dex compilation for {} -> {}", request.moduleName, dexOutput);
        return dexOutput;
    }
    
    /**
     * Create AAR (placeholder implementation)
     */
    private String createAar(AndroidBuildRequest request, String javaOutputDir, String dexOutput) {
        // TODO: Implement AAR creation
        String aarOutput = request.outputDirectory + "/" + request.moduleName + ".aar";
        logger.debug("Creating AAR for {} -> {}", request.moduleName, aarOutput);
        return aarOutput;
    }
    
    /**
     * Assemble final APK/AAB
     */
    private String assembleFinalArtifact(AndroidBuildRequest request, JavaBuildResult javaResult, AndroidLocalBuildResult localResult) {
        try {
            if (request.moduleType == AndroidModuleType.APPLICATION) {
                return assembleApk(request, javaResult, localResult);
            } else {
                return localResult.outputPath; // AAR already created
            }
        } catch (Exception e) {
            logger.error("Failed to assemble final artifact for {}", request.moduleName, e);
            return null;
        }
    }
    
    /**
     * Assemble APK
     */
    private String assembleApk(AndroidBuildRequest request, JavaBuildResult javaResult, AndroidLocalBuildResult localResult) {
        // TODO: Implement APK assembly with signing
        String apkOutput = request.outputDirectory + "/" + request.moduleName + "-" + request.buildType + ".apk";
        logger.info("Assembling APK for {} -> {}", request.moduleName, apkOutput);
        return apkOutput;
    }
    
    // Result classes
    public static class AndroidBuildRequest {
        public final String moduleName;
        public final AndroidModuleType moduleType;
        public final String buildType;
        public final int compileSdkVersion;
        public final String javaVersion;
        public final List<String> javaSourceFiles;
        public final List<String> javaClasspath;
        public final String javaOutputDir;
        public final String outputDirectory;
        
        public AndroidBuildRequest(String moduleName, AndroidModuleType moduleType, String buildType,
                                 int compileSdkVersion, String javaVersion, List<String> javaSourceFiles,
                                 List<String> javaClasspath, String javaOutputDir, String outputDirectory) {
            this.moduleName = moduleName;
            this.moduleType = moduleType;
            this.buildType = buildType;
            this.compileSdkVersion = compileSdkVersion;
            this.javaVersion = javaVersion;
            this.javaSourceFiles = javaSourceFiles;
            this.javaClasspath = javaClasspath;
            this.javaOutputDir = javaOutputDir;
            this.outputDirectory = outputDirectory;
        }
    }
    
    public enum AndroidModuleType {
        APPLICATION, LIBRARY
    }
    
    public static class JavaBuildResult {
        public final boolean success;
        public final String errorMessage;
        public final String outputDirectory;
        
        private JavaBuildResult(boolean success, String errorMessage, String outputDirectory) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.outputDirectory = outputDirectory;
        }
        
        public static JavaBuildResult success(String outputDirectory) {
            return new JavaBuildResult(true, null, outputDirectory);
        }
        
        public static JavaBuildResult failure(String errorMessage) {
            return new JavaBuildResult(false, errorMessage, null);
        }
    }
    
    public static class AndroidLocalBuildResult {
        public final boolean success;
        public final String errorMessage;
        public final String outputPath;
        public final String outputType;
        
        private AndroidLocalBuildResult(boolean success, String errorMessage, String outputPath, String outputType) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.outputPath = outputPath;
            this.outputType = outputType;
        }
        
        public static AndroidLocalBuildResult success(String outputPath, String outputType) {
            return new AndroidLocalBuildResult(true, null, outputPath, outputType);
        }
        
        public static AndroidLocalBuildResult failure(String errorMessage) {
            return new AndroidLocalBuildResult(false, errorMessage, null, null);
        }
    }
    
    public static class AndroidBuildResult {
        public final boolean success;
        public final String errorMessage;
        public final String outputPath;
        
        private AndroidBuildResult(boolean success, String errorMessage, String outputPath) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.outputPath = outputPath;
        }
        
        public static AndroidBuildResult success(String outputPath) {
            return new AndroidBuildResult(true, null, outputPath);
        }
        
        public static AndroidBuildResult failure(String errorMessage) {
            return new AndroidBuildResult(false, errorMessage, null);
        }
    }
}
