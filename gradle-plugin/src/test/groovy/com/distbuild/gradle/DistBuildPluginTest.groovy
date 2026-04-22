package com.distbuild.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class DistBuildPluginTest {
    
    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder()
    
    @Test
    void testPluginApplication() {
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withPluginClass('com.distbuild.gradle.DistBuildPlugin')
            .build()
        
        assertTrue(result.task(':distBuild').outcome == TaskOutcome.SUCCESS)
        assertTrue(result.output.contains('Applying DistBuild plugin'))
    }
    
    @Test
    void testPluginWithJavaProject() {
        // Create a basic Java project structure
        def buildFile = testProjectDir.newFile('build.gradle')
        buildFile.text = """
            plugins {
                id 'java'
                id 'com.distbuild.gradle'
            }
            
            repositories {
                mavenCentral()
            }
            
            distbuild {
                enabled = true
                coordinatorHost = 'localhost'
                coordinatorPort = 8080
                cacheDir = './custom-cache'
            }
        """
        
        // Create source directory
        def srcDir = testProjectDir.newFolder('src', 'main', 'java')
        def packageDir = new File(srcDir, 'com', 'example')
        packageDir.mkdirs()
        
        // Create a simple Java file
        def javaFile = new File(packageDir, 'App.java')
        javaFile.text = """
            package com.example;
            
            public class App {
                public static void main(String[] args) {
                    System.out.println("Hello from distributed build!");
                }
            }
        """
        
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('distBuild')
            .build()
        
        assertTrue(result.task(':distBuild').outcome == TaskOutcome.SUCCESS)
        assertTrue(result.output.contains('Executing distributed build'))
        assertTrue(result.output.contains('local disk cache'))
    }
    
    @Test
    void testPluginConfiguration() {
        def buildFile = testProjectDir.newFile('build.gradle')
        buildFile.text = """
            plugins {
                id 'java'
                id 'com.distbuild.gradle'
            }
            
            distbuild {
                enabled = false
                coordinatorHost = 'remote-host'
                coordinatorPort = 9090
                maxConcurrentTasks = 8
                fallbackToLocal = false
            }
        """
        
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .build()
        
        assertTrue(result.task(':distBuild').outcome == TaskOutcome.SUCCESS)
        assertTrue(result.output.contains('Applying DistBuild plugin'))
    }
    
    @Test
    void testMultiModuleProject() {
        // Create settings.gradle for multi-module project
        def settingsFile = testProjectDir.newFile('settings.gradle')
        settingsFile.text = """
            rootProject.name = 'multi-module-project'
            include 'module-a', 'module-b'
        """
        
        // Create root build file
        def buildFile = testProjectDir.newFile('build.gradle')
        buildFile.text = """
            plugins {
                id 'java'
                id 'com.distbuild.gradle'
            }
            
            repositories {
                mavenCentral()
            }
            
            distbuild {
                enabled = true
            }
        """
        
        // Create module A
        def moduleADir = testProjectDir.newFolder('module-a')
        def moduleABuildFile = new File(moduleADir, 'build.gradle')
        moduleABuildFile.text = """
            plugins {
                id 'java'
            }
            
            repositories {
                mavenCentral()
            }
        """
        
        def moduleASrcDir = moduleADir.newFolder('src', 'main', 'java', 'modulea')
        def moduleAJavaFile = new File(moduleASrcDir, 'ModuleA.java')
        moduleAJavaFile.text = """
            package modulea;
            
            public class ModuleA {
                public String getMessage() {
                    return "Module A";
                }
            }
        """
        
        // Create module B
        def moduleBDir = testProjectDir.newFolder('module-b')
        def moduleBBuildFile = new File(moduleBDir, 'build.gradle')
        moduleBBuildFile.text = """
            plugins {
                id 'java'
            }
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                implementation project(':module-a')
            }
        """
        
        def moduleBSrcDir = moduleBDir.newFolder('src', 'main', 'java', 'moduleb')
        def moduleBJavaFile = new File(moduleBSrcDir, 'ModuleB.java')
        moduleBJavaFile.text = """
            package moduleb;
            
            import modulea.ModuleA;
            
            public class ModuleB {
                public String getMessage() {
                    ModuleA a = new ModuleA();
                    return "Module B with " + a.getMessage();
                }
            }
        """
        
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('distBuild')
            .build()
        
        assertTrue(result.task(':distBuild').outcome == TaskOutcome.SUCCESS)
        assertTrue(result.output.contains('Executing distributed build'))
        assertTrue(result.output.contains('2 modules'))
    }
    
    @Test
    void testPluginDisabled() {
        def buildFile = testProjectDir.newFile('build.gradle')
        buildFile.text = """
            plugins {
                id 'java'
                id 'com.distbuild.gradle'
            }
            
            distbuild {
                enabled = false
            }
        """
        
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .build()
        
        assertTrue(result.task(':distBuild').outcome == TaskOutcome.SUCCESS)
        // Plugin should still apply but distributed build should be disabled
    }
}
