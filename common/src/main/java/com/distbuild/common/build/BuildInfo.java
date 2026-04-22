package com.distbuild.common.build;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Properties;

/**
 * Build information and version management
 */
public class BuildInfo {
    private static final String VERSION = "distbuildVersion";
    private static final String JAVA_VERSION = "javaVersion";
    private static final String BUILD_TIME = "buildTime";
    private static final String BUILD_AUTHOR = "buildAuthor";
    
    private static Properties properties;
    
    static {
        properties = new Properties();
        try (InputStream input = BuildInfo.class.getClassLoader().getResourceAsStream("gradle.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException e) {
            // Fallback to default values
            properties.setProperty(VERSION, "1.0.0");
            properties.setProperty(JAVA_VERSION, "17");
            properties.setProperty(BUILD_TIME, LocalDate.now().toString());
            properties.setProperty(BUILD_AUTHOR, "distbuild-team");
        }
    }
    
    public static String getVersion() {
        return properties.getProperty(VERSION, "1.0.0");
    }
    
    public static String getJavaVersion() {
        return properties.getProperty(JAVA_VERSION, "17");
    }
    
    public static String getBuildTime() {
        return properties.getProperty(BUILD_TIME, LocalDate.now().toString());
    }
    
    public static String getBuildAuthor() {
        return properties.getProperty(BUILD_AUTHOR, "distbuild-team");
    }
    
    public static String getFullVersion() {
        return "distbuild " + getVersion();
    }
    
    public static String getBuildInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Version: ").append(getVersion()).append("\n");
        info.append("Java Version: ").append(getJavaVersion()).append("\n");
        info.append("Build Date: ").append(getBuildTime()).append("\n");
        info.append("Build Author: ").append(getBuildAuthor()).append("\n");
        return info.toString();
    }
}
