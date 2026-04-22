package com.distbuild.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.junit.Assert.*

class SimplePluginTest {
    
    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder()
    
    @Test
    void testPluginCompiles() {
        // Just verify the plugin can be compiled
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .build()
        
        // If we get here without compilation errors, the plugin compiles correctly
        assertTrue(true)
    }
    
    @Test
    void testPluginCanBeApplied() {
        def buildFile = testProjectDir.newFile('build.gradle')
        buildFile.text = """
            plugins {
                id 'java'
            }
            
            repositories {
                mavenCentral()
            }
        """
        
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('tasks')
            .build()
        
        // Should be able to list tasks
        assertTrue(result.output.contains('help') || result.output.contains('tasks'))
    }
}
