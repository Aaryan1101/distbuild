package com.distbuild.common.parser;

import com.distbuild.common.model.ModuleInfo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.Assert.*;

public class SimpleGradleParserTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testEmptyProject() throws IOException {
        Path projectRoot = tempFolder.getRoot().toPath();
        SimpleGradleParser parser = new SimpleGradleParser(projectRoot);

        SimpleGradleParser.GradleProject project = parser.parseProject();

        assertEquals(0, project.getModuleCount());
        assertTrue(project.getModules().isEmpty());
    }

    @Test
    public void testSingleModuleProject() throws IOException {
        Path projectRoot = tempFolder.getRoot().toPath();
        
        // Create build.gradle
        Path buildFile = projectRoot.resolve("build.gradle");
        Files.write(buildFile, """
            plugins {
                id 'java'
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                implementation 'org.junit:junit:4.13.2'
            }
            """.getBytes());

        // Create source directory and file
        Path srcDir = projectRoot.resolve("src/main/java");
        Files.createDirectories(srcDir);
        Path sourceFile = srcDir.resolve("Main.java");
        Files.write(sourceFile, "public class Main {}".getBytes());

        SimpleGradleParser parser = new SimpleGradleParser(projectRoot);
        SimpleGradleParser.GradleProject project = parser.parseProject();

        assertEquals(1, project.getModuleCount());
        assertTrue(project.getModules().containsKey("root"));
        
        ModuleInfo module = project.getModule("root");
        assertNotNull(module);
        assertEquals("root", module.getName());
        assertEquals(projectRoot, module.getProjectPath());
        assertTrue(module.getSourceFiles().stream().anyMatch(path -> 
            path.replace("\\", "/").equals("src/main/java/Main.java")));
        assertFalse(module.hasAnnotationProcessors());
    }

    @Test
    public void testMultiModuleProject() throws IOException {
        Path projectRoot = tempFolder.getRoot().toPath();
        
        // Create settings.gradle
        Path settingsFile = projectRoot.resolve("settings.gradle");
        Files.write(settingsFile, "include 'module-a', 'module-b'".getBytes());

        // Create module-a
        Path moduleA = projectRoot.resolve("module-a");
        Files.createDirectories(moduleA);
        Path buildA = moduleA.resolve("build.gradle");
        Files.write(buildA, """
            plugins {
                id 'java'
            }
            """.getBytes());
        
        Path srcA = moduleA.resolve("src/main/java");
        Files.createDirectories(srcA);
        Files.write(srcA.resolve("A.java"), "public class A {}".getBytes());

        // Create module-b that depends on module-a
        Path moduleB = projectRoot.resolve("module-b");
        Files.createDirectories(moduleB);
        Path buildB = moduleB.resolve("build.gradle");
        Files.write(buildB, """
            plugins {
                id 'java'
            }
            
            dependencies {
                implementation project(':module-a')
            }
            """.getBytes());
        
        Path srcB = moduleB.resolve("src/main/java");
        Files.createDirectories(srcB);
        Files.write(srcB.resolve("B.java"), "public class B {}".getBytes());

        SimpleGradleParser parser = new SimpleGradleParser(projectRoot);
        SimpleGradleParser.GradleProject project = parser.parseProject();

        assertEquals(2, project.getModuleCount());
        assertTrue(project.getModules().containsKey("module-a"));
        assertTrue(project.getModules().containsKey("module-b"));
        
        ModuleInfo moduleAInfo = project.getModule("module-a");
        ModuleInfo moduleBInfo = project.getModule("module-b");
        
        // Check module-a
        assertEquals("module-a", moduleAInfo.getName());
        assertTrue(moduleAInfo.getDependencies().isEmpty());
        
        // Check module-b
        assertEquals("module-b", moduleBInfo.getName());
        assertEquals(Set.of("module-a"), moduleBInfo.getDependencies());
    }

    @Test
    public void testAnnotationProcessorDetection() throws IOException {
        Path projectRoot = tempFolder.getRoot().toPath();
        
        // Create build.gradle with Lombok
        Path buildFile = projectRoot.resolve("build.gradle");
        Files.write(buildFile, """
            plugins {
                id 'java'
            }
            
            dependencies {
                annotationProcessor 'org.projectlombok:lombok:1.18.28'
                implementation 'org.projectlombok:lombok:1.18.28'
            }
            """.getBytes());

        // Create source directory and file
        Path srcDir = projectRoot.resolve("src/main/java");
        Files.createDirectories(srcDir);
        Path sourceFile = srcDir.resolve("Main.java");
        Files.write(sourceFile, "public class Main {}".getBytes());

        SimpleGradleParser parser = new SimpleGradleParser(projectRoot);
        SimpleGradleParser.GradleProject project = parser.parseProject();

        assertEquals(1, project.getModuleCount());
        ModuleInfo module = project.getModule("root");
        assertTrue(module.hasAnnotationProcessors());
    }

    @Test
    public void testKotlinBuildScript() throws IOException {
        Path projectRoot = tempFolder.getRoot().toPath();
        
        // Create build.gradle.kts
        Path buildFile = projectRoot.resolve("build.gradle.kts");
        Files.write(buildFile, """
            plugins {
                java
            }
            
            repositories {
                mavenCentral()
            }
            """.getBytes());

        // Create source directory and file
        Path srcDir = projectRoot.resolve("src/main/java");
        Files.createDirectories(srcDir);
        Path sourceFile = srcDir.resolve("Main.java");
        Files.write(sourceFile, "public class Main {}".getBytes());

        SimpleGradleParser parser = new SimpleGradleParser(projectRoot);
        SimpleGradleParser.GradleProject project = parser.parseProject();

        assertEquals(1, project.getModuleCount());
        assertTrue(project.getModules().containsKey("root"));
    }

    @Test
    public void testModuleWithoutJavaSources() throws IOException {
        Path projectRoot = tempFolder.getRoot().toPath();
        
        // Create build.gradle but no Java sources
        Path buildFile = projectRoot.resolve("build.gradle");
        Files.write(buildFile, "plugins { id 'java' }".getBytes());

        SimpleGradleParser parser = new SimpleGradleParser(projectRoot);
        SimpleGradleParser.GradleProject project = parser.parseProject();

        assertEquals(0, project.getModuleCount());
    }

    @Test
    public void testDependencyResolution() throws IOException {
        Path projectRoot = tempFolder.getRoot().toPath();
        
        // Create three modules: a -> b -> c
        createModule(projectRoot, "module-a", "implementation project(':module-b')");
        createModule(projectRoot, "module-b", "implementation project(':module-c')");
        createModule(projectRoot, "module-c", ""); // No dependencies

        SimpleGradleParser parser = new SimpleGradleParser(projectRoot);
        SimpleGradleParser.GradleProject project = parser.parseProject();

        assertEquals(3, project.getModuleCount());
        
        ModuleInfo moduleA = project.getModule("module-a");
        ModuleInfo moduleB = project.getModule("module-b");
        ModuleInfo moduleC = project.getModule("module-c");
        
        assertEquals(Set.of("module-b"), moduleA.getDependencies());
        assertEquals(Set.of("module-c"), moduleB.getDependencies());
        assertTrue(moduleC.getDependencies().isEmpty());
    }

    private void createModule(Path projectRoot, String moduleName, String dependencies) throws IOException {
        Path moduleDir = projectRoot.resolve(moduleName);
        Files.createDirectories(moduleDir);
        
        Path buildFile = moduleDir.resolve("build.gradle");
        String buildContent = """
            plugins {
                id 'java'
            }
            """ + (dependencies.isEmpty() ? "" : "\n" + dependencies);
        Files.write(buildFile, buildContent.getBytes());
        
        Path srcDir = moduleDir.resolve("src/main/java");
        Files.createDirectories(srcDir);
        Files.write(srcDir.resolve(moduleName.replace("-", "") + ".java"), 
                   ("public class " + moduleName.replace("-", "") + " {}").getBytes());
    }
}
