package com.distbuild.cli.android;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;

/**
 * Detects Android projects and their structure
 */
public class AndroidProjectDetector {
    
    public static class AndroidProject {
        public final Path projectRoot;
        public final Path buildGradle;
        public final Path appModule;
        public final Path settingsGradle;
        public final List<String> modules;
        public final boolean isKotlin;
        
        public AndroidProject(Path projectRoot, Path buildGradle, Path appModule, 
                            Path settingsGradle, List<String> modules, boolean isKotlin) {
            this.projectRoot = projectRoot;
            this.buildGradle = buildGradle;
            this.appModule = appModule;
            this.settingsGradle = settingsGradle;
            this.modules = modules;
            this.isKotlin = isKotlin;
        }
    }
    
    /**
     * Detect Android project from current directory or parent directories
     */
    public static AndroidProject detectProject(Path startDir) throws IOException {
        Path current = startDir.toAbsolutePath();
        
        // Search up the directory tree for Android project markers
        while (current != null) {
            if (isAndroidProjectRoot(current)) {
                return analyzeAndroidProject(current);
            }
            current = current.getParent();
            if (current != null && current.toString().equals(current.getRoot().toString())) {
                break; // Reached filesystem root
            }
        }
        
        throw new IOException("No Android project found in " + startDir + " or parent directories");
    }
    
    /**
     * Check if directory is an Android project root
     */
    private static boolean isAndroidProjectRoot(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return false;
        
        // Check for key Android project files
        Path buildGradle = dir.resolve("build.gradle");
        Path settingsGradle = dir.resolve("settings.gradle");
        Path gradleProperties = dir.resolve("gradle.properties");
        
        boolean hasBuildGradle = Files.exists(buildGradle);
        boolean hasSettingsGradle = Files.exists(settingsGradle);
        
        if (!hasBuildGradle) return false;
        
        // Check build.gradle for Android plugins
        String content = Files.readString(buildGradle);
        boolean hasAndroidPlugin = content.contains("com.android.application") || 
                                  content.contains("com.android.library") ||
                                  content.contains("android {");
        
        return hasAndroidPlugin;
    }
    
    /**
     * Analyze Android project structure
     */
    private static AndroidProject analyzeAndroidProject(Path projectRoot) throws IOException {
        Path buildGradle = projectRoot.resolve("build.gradle");
        Path settingsGradle = projectRoot.resolve("settings.gradle");
        Path appModule = projectRoot.resolve("app");
        
        // Parse modules from settings.gradle
        List<String> modules = parseModules(settingsGradle);
        
        // Check if Kotlin is used
        boolean isKotlin = checkKotlinUsage(projectRoot);
        
        return new AndroidProject(projectRoot, buildGradle, appModule, settingsGradle, modules, isKotlin);
    }
    
    /**
     * Parse module declarations from settings.gradle
     */
    private static List<String> parseModules(Path settingsGradle) throws IOException {
        List<String> modules = new ArrayList<>();
        
        if (!Files.exists(settingsGradle)) return modules;
        
        String content = Files.readString(settingsGradle);
        String[] lines = content.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("include ")) {
                String module = line.substring(8).trim();
                // Remove quotes if present
                module = module.replaceAll("^['\"]|['\"]$", "");
                modules.add(module);
            }
        }
        
        return modules;
    }
    
    /**
     * Check if project uses Kotlin
     */
    private static boolean checkKotlinUsage(Path projectRoot) throws IOException {
        // Check for Kotlin files in app module
        Path appModule = projectRoot.resolve("app");
        if (!Files.exists(appModule)) return false;
        
        Path srcMain = appModule.resolve("src/main");
        if (!Files.exists(srcMain)) return false;
        
        // Look for .kt files
        try (var stream = Files.walk(srcMain)) {
            return stream.anyMatch(path -> path.toString().endsWith(".kt"));
        }
    }
    
    /**
     * Find all Android modules in project
     */
    public static List<String> findAndroidModules(AndroidProject project) throws IOException {
        List<String> androidModules = new ArrayList<>();
        
        for (String module : project.modules) {
            Path moduleDir = project.projectRoot.resolve(module.substring(1)); // Remove ':'
            Path buildGradle = moduleDir.resolve("build.gradle");
            
            if (Files.exists(buildGradle)) {
                String content = Files.readString(buildGradle);
                if (content.contains("com.android.application") || 
                    content.contains("com.android.library")) {
                    androidModules.add(module);
                }
            }
        }
        
        return androidModules;
    }
    
    /**
     * Get Android SDK path from ANDROID_HOME or local.properties
     */
    public static Path findAndroidSdk(AndroidProject project) throws IOException {
        // Check environment variable first
        String androidHome = System.getenv("ANDROID_HOME");
        if (androidHome != null) {
            Path sdkPath = Paths.get(androidHome);
            if (Files.exists(sdkPath)) {
                return sdkPath;
            }
        }
        
        // Check local.properties
        Path localProperties = project.projectRoot.resolve("local.properties");
        if (Files.exists(localProperties)) {
            String content = Files.readString(localProperties);
            String[] lines = content.split("\n");
            
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("sdk.dir=")) {
                    String sdkPath = line.substring(8).trim();
                    // Remove quotes if present
                    sdkPath = sdkPath.replaceAll("^['\"]|['\"]$", "");
                    return Paths.get(sdkPath);
                }
            }
        }
        
        throw new IOException("Android SDK not found. Set ANDROID_HOME or configure local.properties");
    }
    
    /**
     * Get compile SDK version from build.gradle
     */
    public static int getCompileSdkVersion(AndroidProject project) throws IOException {
        Path appBuildGradle = project.appModule.resolve("build.gradle");
        if (!Files.exists(appBuildGradle)) {
            appBuildGradle = project.buildGradle; // Fallback to root
        }
        
        String content = Files.readString(appBuildGradle);
        
        // Look for compileSdk version
        if (content.contains("compileSdk")) {
            String[] lines = content.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.contains("compileSdk")) {
                    // Extract version number
                    String[] parts = line.split("\\s+");
                    for (String part : parts) {
                        if (part.matches("\\d+")) {
                            return Integer.parseInt(part);
                        }
                    }
                }
            }
        }
        
        // Default to recent version
        return 34;
    }
}
