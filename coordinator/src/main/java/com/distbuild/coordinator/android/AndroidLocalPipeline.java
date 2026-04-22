package com.distbuild.coordinator.android;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Handles Android-specific local build tasks (dex, aapt2, signing)
 */
public class AndroidLocalPipeline {
    private static final Logger logger = LoggerFactory.getLogger(AndroidLocalPipeline.class);
    
    private final Path androidSdkPath;
    private final Path buildToolsPath;
    
    public AndroidLocalPipeline(Path androidSdkPath) {
        this.androidSdkPath = androidSdkPath;
        this.buildToolsPath = findBuildTools(androidSdkPath);
    }
    
    /**
     * Process Android resources using aapt2
     */
    public CompletableFuture<ResourceProcessResult> processResources(ResourceProcessRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Processing Android resources for module: {}", request.moduleName);
                
                Path resourcesDir = Path.of(request.moduleRoot, "src/main/res");
                if (!Files.exists(resourcesDir)) {
                    logger.debug("No resources directory found for {}", request.moduleName);
                    return ResourceProcessResult.success(null, null);
                }
                
                // Create output directory
                Path compiledResourcesDir = Path.of(request.outputDirectory, "compiled_res");
                Files.createDirectories(compiledResourcesDir);
                
                // Compile resources using aapt2
                List<String> command = buildAapt2CompileCommand(resourcesDir, compiledResourcesDir);
                
                ProcessResult result = executeCommand(command);
                if (!result.success) {
                    return ResourceProcessResult.failure("Resource compilation failed: " + result.errorMessage);
                }
                
                // Link resources
                Path linkedResourcesDir = Path.of(request.outputDirectory, "linked_res");
                Files.createDirectories(linkedResourcesDir);
                
                List<String> linkCommand = buildAapt2LinkCommand(request, compiledResourcesDir, linkedResourcesDir);
                ProcessResult linkResult = executeCommand(linkCommand);
                
                if (!linkResult.success) {
                    return ResourceProcessResult.failure("Resource linking failed: " + linkResult.errorMessage);
                }
                
                // Generate R.java
                String rJavaPath = generateRJava(request, linkedResourcesDir);
                if (rJavaPath == null) {
                    return ResourceProcessResult.failure("R.java generation failed");
                }
                
                return ResourceProcessResult.success(linkedResourcesDir.toString(), rJavaPath);
                
            } catch (Exception e) {
                logger.error("Resource processing failed for {}", request.moduleName, e);
                return ResourceProcessResult.failure("Resource processing error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Perform dex compilation using d8
     */
    public CompletableFuture<DexResult> performDexCompilation(DexRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Performing dex compilation for module: {}", request.moduleName);
                
                // Create output directory
                Path dexOutputDir = Path.of(request.outputDirectory, "dex");
                Files.createDirectories(dexOutputDir);
                
                // Build d8 command
                List<String> command = buildD8Command(request, dexOutputDir);
                
                ProcessResult result = executeCommand(command);
                if (!result.success) {
                    return DexResult.failure("Dex compilation failed: " + result.errorMessage);
                }
                
                // Verify dex files were created
                Path dexFile = dexOutputDir.resolve("classes.dex");
                if (!Files.exists(dexFile)) {
                    return DexResult.failure("No dex file generated");
                }
                
                return DexResult.success(dexOutputDir.toString());
                
            } catch (Exception e) {
                logger.error("Dex compilation failed for {}", request.moduleName, e);
                return DexResult.failure("Dex compilation error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Create AAR for Android library modules
     */
    public CompletableFuture<AarResult> createAar(AarRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Creating AAR for library module: {}", request.moduleName);
                
                Path aarOutputDir = Path.of(request.outputDirectory);
                Files.createDirectories(aarOutputDir);
                
                Path aarFile = aarOutputDir.resolve(request.moduleName + ".aar");
                
                // Build AAR using aapt2
                List<String> command = buildAarCommand(request, aarFile);
                
                ProcessResult result = executeCommand(command);
                if (!result.success) {
                    return AarResult.failure("AAR creation failed: " + result.errorMessage);
                }
                
                if (!Files.exists(aarFile)) {
                    return AarResult.failure("AAR file not created");
                }
                
                return AarResult.success(aarFile.toString());
                
            } catch (Exception e) {
                logger.error("AAR creation failed for {}", request.moduleName, e);
                return AarResult.failure("AAR creation error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Assemble APK for Android application modules
     */
    public CompletableFuture<ApkResult> assembleApk(ApkRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Assembling APK for application module: {}", request.moduleName);
                
                Path apkOutputDir = Path.of(request.outputDirectory);
                Files.createDirectories(apkOutputDir);
                
                String apkName = request.moduleName + "-" + request.buildType + ".apk";
                Path apkFile = apkOutputDir.resolve(apkName);
                
                // Build APK using aapt2
                List<String> command = buildApkCommand(request, apkFile);
                
                ProcessResult result = executeCommand(command);
                if (!result.success) {
                    return ApkResult.failure("APK assembly failed: " + result.errorMessage);
                }
                
                if (!Files.exists(apkFile)) {
                    return ApkResult.failure("APK file not created");
                }
                
                // Sign APK if release build
                if ("release".equals(request.buildType) && request.signingConfig != null) {
                    String signedApkPath = signApk(apkFile, request.signingConfig);
                    if (signedApkPath == null) {
                        return ApkResult.failure("APK signing failed");
                    }
                    return ApkResult.success(signedApkPath);
                }
                
                return ApkResult.success(apkFile.toString());
                
            } catch (Exception e) {
                logger.error("APK assembly failed for {}", request.moduleName, e);
                return ApkResult.failure("APK assembly error: " + e.getMessage());
            }
        });
    }
    
    /**
     * Sign APK using apksigner
     */
    private String signApk(Path apkFile, SigningConfig signingConfig) {
        try {
            logger.info("Signing APK: {}", apkFile.getFileName());
            
            Path signedApkFile = apkFile.getParent().resolve(apkFile.getFileName().toString().replace(".apk", "-signed.apk"));
            
            List<String> command = buildApksignerCommand(apkFile, signedApkFile, signingConfig);
            ProcessResult result = executeCommand(command);
            
            if (!result.success) {
                logger.error("APK signing failed: {}", result.errorMessage);
                return null;
            }
            
            if (!Files.exists(signedApkFile)) {
                logger.error("Signed APK file not created");
                return null;
            }
            
            logger.info("Successfully signed APK: {}", signedApkFile.getFileName());
            return signedApkFile.toString();
            
        } catch (Exception e) {
            logger.error("APK signing error", e);
            return null;
        }
    }
    
    // Command builders
    private List<String> buildAapt2CompileCommand(Path resourcesDir, Path outputDir) {
        List<String> command = new ArrayList<>();
        command.add(buildToolsPath.resolve("aapt2").toString());
        command.add("compile");
        command.add(resourcesDir.toString());
        command.add("-o");
        command.add(outputDir.toString());
        command.add("--dir");
        return command;
    }
    
    private List<String> buildAapt2LinkCommand(ResourceProcessRequest request, Path compiledRes, Path outputDir) {
        List<String> command = new ArrayList<>();
        command.add(buildToolsPath.resolve("aapt2").toString());
        command.add("link");
        command.add(compiledRes.toString());
        command.add("-o");
        command.add(outputDir.resolve("resources.apk").toString());
        command.add("--manifest");
        command.add(Path.of(request.moduleRoot, "src/main/AndroidManifest.xml").toString());
        command.add("-I");
        command.add(androidSdkPath.resolve("platforms/android-" + request.compileSdkVersion + "/android.jar").toString());
        command.add("--java");
        command.add(Path.of(request.outputDirectory, "gen").toString());
        command.add("--custom-package");
        command.add(request.packageName);
        return command;
    }
    
    private List<String> buildD8Command(DexRequest request, Path outputDir) {
        List<String> command = new ArrayList<>();
        command.add(buildToolsPath.resolve("d8").toString());
        command.add("--output");
        command.add(outputDir.toString());
        command.add("--lib");
        command.add(androidSdkPath.resolve("platforms/android-" + request.compileSdkVersion + "/android.jar").toString());
        
        // Add compiled classes
        for (String classFile : request.classFiles) {
            command.add(classFile);
        }
        
        return command;
    }
    
    private List<String> buildAarCommand(AarRequest request, Path aarFile) {
        List<String> command = new ArrayList<>();
        command.add(buildToolsPath.resolve("aapt2").toString());
        command.add("link");
        command.add("--manifest");
        command.add(Path.of(request.moduleRoot, "src/main/AndroidManifest.xml").toString());
        command.add("-o");
        command.add(aarFile.toString());
        command.add("-I");
        command.add(androidSdkPath.resolve("platforms/android-" + request.compileSdkVersion + "/android.jar").toString());
        
        return command;
    }
    
    private List<String> buildApkCommand(ApkRequest request, Path apkFile) {
        List<String> command = new ArrayList<>();
        command.add(buildToolsPath.resolve("aapt2").toString());
        command.add("link");
        command.add("--manifest");
        command.add(Path.of(request.moduleRoot, "src/main/AndroidManifest.xml").toString());
        command.add("-o");
        command.add(apkFile.toString());
        command.add("-I");
        command.add(androidSdkPath.resolve("platforms/android-" + request.compileSdkVersion + "/android.jar").toString());
        
        return command;
    }
    
    private List<String> buildApksignerCommand(Path apkFile, Path signedApkFile, SigningConfig signingConfig) {
        List<String> command = new ArrayList<>();
        command.add(buildToolsPath.resolve("apksigner").toString());
        command.add("sign");
        command.add("--ks");
        command.add(signingConfig.keystorePath);
        command.add("--ks-pass");
        command.add("pass:" + signingConfig.keystorePassword);
        command.add("--ks-key-alias");
        command.add(signingConfig.keyAlias);
        command.add("--key-pass");
        command.add("pass:" + signingConfig.keyPassword);
        command.add("--out");
        command.add(signedApkFile.toString());
        command.add(apkFile.toString());
        
        return command;
    }
    
    private String generateRJava(ResourceProcessRequest request, Path linkedResourcesDir) {
        // R.java should be generated by aapt2 link command
        Path genDir = Path.of(request.outputDirectory, "gen");
        Path rJavaFile = genDir.resolve(request.packageName.replace('.', '/')).resolve("R.java");
        
        if (Files.exists(rJavaFile)) {
            return rJavaFile.toString();
        }
        
        return null;
    }
    
    private Path findBuildTools(Path androidSdkPath) {
        Path buildToolsDir = androidSdkPath.resolve("build-tools");
        if (!Files.exists(buildToolsDir)) {
            throw new RuntimeException("Build tools not found in Android SDK: " + androidSdkPath);
        }
        
        try {
            return Files.list(buildToolsDir)
                .filter(Files::isDirectory)
                .sorted((d1, d2) -> d2.getFileName().toString().compareTo(d1.getFileName().toString()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No build tools found"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to find build tools", e);
        }
    }
    
    private ProcessResult executeCommand(List<String> command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                return ProcessResult.success(output.toString());
            } else {
                return ProcessResult.failure("Command failed with exit code " + exitCode + ": " + output.toString());
            }
            
        } catch (Exception e) {
            return ProcessResult.failure("Command execution error: " + e.getMessage());
        }
    }
    
    // Request and result classes
    public static class ResourceProcessRequest {
        public final String moduleName;
        public final String moduleRoot;
        public final String outputDirectory;
        public final String packageName;
        public final int compileSdkVersion;
        
        public ResourceProcessRequest(String moduleName, String moduleRoot, String outputDirectory, 
                                     String packageName, int compileSdkVersion) {
            this.moduleName = moduleName;
            this.moduleRoot = moduleRoot;
            this.outputDirectory = outputDirectory;
            this.packageName = packageName;
            this.compileSdkVersion = compileSdkVersion;
        }
    }
    
    public static class ResourceProcessResult {
        public final boolean success;
        public final String errorMessage;
        public final String resourcesPath;
        public final String rJavaPath;
        
        private ResourceProcessResult(boolean success, String errorMessage, String resourcesPath, String rJavaPath) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.resourcesPath = resourcesPath;
            this.rJavaPath = rJavaPath;
        }
        
        public static ResourceProcessResult success(String resourcesPath, String rJavaPath) {
            return new ResourceProcessResult(true, null, resourcesPath, rJavaPath);
        }
        
        public static ResourceProcessResult failure(String errorMessage) {
            return new ResourceProcessResult(false, errorMessage, null, null);
        }
    }
    
    public static class DexRequest {
        public final String moduleName;
        public final List<String> classFiles;
        public final String outputDirectory;
        public final int compileSdkVersion;
        
        public DexRequest(String moduleName, List<String> classFiles, String outputDirectory, int compileSdkVersion) {
            this.moduleName = moduleName;
            this.classFiles = classFiles;
            this.outputDirectory = outputDirectory;
            this.compileSdkVersion = compileSdkVersion;
        }
    }
    
    public static class DexResult {
        public final boolean success;
        public final String errorMessage;
        public final String dexOutputPath;
        
        private DexResult(boolean success, String errorMessage, String dexOutputPath) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.dexOutputPath = dexOutputPath;
        }
        
        public static DexResult success(String dexOutputPath) {
            return new DexResult(true, null, dexOutputPath);
        }
        
        public static DexResult failure(String errorMessage) {
            return new DexResult(false, errorMessage, null);
        }
    }
    
    public static class AarRequest {
        public final String moduleName;
        public final String moduleRoot;
        public final String outputDirectory;
        public final int compileSdkVersion;
        
        public AarRequest(String moduleName, String moduleRoot, String outputDirectory, int compileSdkVersion) {
            this.moduleName = moduleName;
            this.moduleRoot = moduleRoot;
            this.outputDirectory = outputDirectory;
            this.compileSdkVersion = compileSdkVersion;
        }
    }
    
    public static class AarResult {
        public final boolean success;
        public final String errorMessage;
        public final String aarPath;
        
        private AarResult(boolean success, String errorMessage, String aarPath) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.aarPath = aarPath;
        }
        
        public static AarResult success(String aarPath) {
            return new AarResult(true, null, aarPath);
        }
        
        public static AarResult failure(String errorMessage) {
            return new AarResult(false, errorMessage, null);
        }
    }
    
    public static class ApkRequest {
        public final String moduleName;
        public final String moduleRoot;
        public final String outputDirectory;
        public final String buildType;
        public final int compileSdkVersion;
        public final SigningConfig signingConfig;
        
        public ApkRequest(String moduleName, String moduleRoot, String outputDirectory, 
                         String buildType, int compileSdkVersion, SigningConfig signingConfig) {
            this.moduleName = moduleName;
            this.moduleRoot = moduleRoot;
            this.outputDirectory = outputDirectory;
            this.buildType = buildType;
            this.compileSdkVersion = compileSdkVersion;
            this.signingConfig = signingConfig;
        }
    }
    
    public static class ApkResult {
        public final boolean success;
        public final String errorMessage;
        public final String apkPath;
        
        private ApkResult(boolean success, String errorMessage, String apkPath) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.apkPath = apkPath;
        }
        
        public static ApkResult success(String apkPath) {
            return new ApkResult(true, null, apkPath);
        }
        
        public static ApkResult failure(String errorMessage) {
            return new ApkResult(false, errorMessage, null);
        }
    }
    
    public static class SigningConfig {
        public final String keystorePath;
        public final String keystorePassword;
        public final String keyAlias;
        public final String keyPassword;
        
        public SigningConfig(String keystorePath, String keystorePassword, String keyAlias, String keyPassword) {
            this.keystorePath = keystorePath;
            this.keystorePassword = keystorePassword;
            this.keyAlias = keyAlias;
            this.keyPassword = keyPassword;
        }
    }
    
    public static class ProcessResult {
        public final boolean success;
        public final String errorMessage;
        public final String output;
        
        private ProcessResult(boolean success, String errorMessage, String output) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.output = output;
        }
        
        public static ProcessResult success(String output) {
            return new ProcessResult(true, null, output);
        }
        
        public static ProcessResult failure(String errorMessage) {
            return new ProcessResult(false, errorMessage, null);
        }
    }
}
