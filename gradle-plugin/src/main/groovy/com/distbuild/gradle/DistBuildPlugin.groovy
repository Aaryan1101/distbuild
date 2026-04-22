package com.distbuild.gradle

import com.distbuild.common.cache.BuildCache
import com.distbuild.common.cache.LocalDiskCache
import com.distbuild.common.cache.CompileResult
import com.distbuild.common.graph.ModuleGraph
import com.distbuild.common.model.ModuleInfo
import com.distbuild.common.parser.SimpleGradleParser
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.TaskProvider

/**
 * Gradle plugin for distributed Java builds
 */
class DistBuildPlugin implements Plugin<Project> {
    
    @Override
    void apply(Project project) {
        println "Applying DistBuild plugin to project: ${project.name}"
        
        // Apply Java plugin if not already applied
        if (!project.plugins.hasPlugin('java')) {
            project.plugins.apply(JavaPlugin.class)
        }
        
        // Create extension for configuration
        project.extensions.create('distbuild', DistBuildExtension)
        
        // Create build cache
        BuildCache cache = createCache(project)
        
        // Create distributed build task
        TaskProvider<Task> distBuildTask = project.tasks.register('distBuild') {
            group = 'build'
            description = 'Execute distributed build using coordinator'
            
            doLast {
                println "Executing distributed build for ${project.name}"
                try {
                    executeDistributedBuild(project, cache)
                } catch (Exception e) {
                    project.logger.error("Distributed build failed", e)
                    throw new RuntimeException("Distributed build failed", e)
                }
            }
        }
        
        // Configure compile tasks to use distributed build
        project.tasks.withType(org.gradle.api.tasks.compile.JavaCompile) { compileTask ->
            compileTask.doFirst {
                if (project.distbuild.enabled) {
                    println "Redirecting ${compileTask.name} to distributed build"
                    // In a full implementation, this would replace the compilation
                    // For now, we just log and continue with local compilation
                }
            }
        }
        
        // Add dependency to distBuild task for build lifecycle
        project.tasks.named('build').configure { buildTask ->
            buildTask.dependsOn(distBuildTask)
        }
    }
    
    private BuildCache createCache(Project project) {
        String cacheDir = project.distbuild.cacheDir ?: 
                           "${project.buildDir}/distbuild-cache"
        
        project.logger.info("Creating local disk cache at: ${cacheDir}")
        return new LocalDiskCache(project.file(cacheDir).toPath())
    }
    
    private void executeDistributedBuild(Project project, BuildCache cache) throws Exception {
        // Parse the project structure
        SimpleGradleParser parser = new SimpleGradleParser(project.projectDir.toPath())
        ModuleGraph moduleGraph = parser.parseProject()
        
        project.logger.info("Parsed ${moduleGraph.size()} modules")
        
        // For demonstration, we'll simulate distributed compilation
        // In a full implementation, this would connect to the coordinator
        simulateDistributedCompilation(moduleGraph, cache, project)
    }
    
    private void simulateDistributedCompilation(ModuleGraph moduleGraph, BuildCache cache, Project project) {
        try {
            // Get modules in compilation order
            List<List<String>> parallelBatches = moduleGraph.getParallelBatches()
            
            project.logger.info("Executing ${parallelBatches.size()} parallel batches")
            
            for (int batchIndex = 0; batchIndex < parallelBatches.size(); batchIndex++) {
                List<String> batch = parallelBatches.get(batchIndex)
                project.logger.info("Batch ${batchIndex + 1}: ${batch}")
                
                // Simulate compilation for each module in the batch
                for (String moduleName : batch) {
                    Optional<ModuleInfo> moduleOpt = moduleGraph.getModule(moduleName)
                    if (moduleOpt.isPresent()) {
                        ModuleInfo module = moduleOpt.get()
                        
                        // Check cache first
                        String cacheKey = generateCacheKey(module)
                        Optional<CompileResult> cached = cache.get(cacheKey)
                        
                        if (cached.isPresent()) {
                            project.logger.info("Cache hit for module: ${moduleName}")
                        } else {
                            project.logger.info("Compiling module: ${moduleName} (simulated)")
                            
                            // Simulate compilation
                            Thread.sleep(100 + (int)(Math.random() * 200))
                            
                            // Create mock result
                            CompileResult result = CompileResult.success(
                                module.getSourceFiles(),
                                Set.of("build/classes/${moduleName.replace('-', '/')}/Main.class"),
                                Map.of("build/classes/${moduleName.replace('-', '/')}/Main.class", "compiled".getBytes()),
                                100 + (int)(Math.random() * 200)
                            )
                            
                            // Cache the result
                            cache.put(cacheKey, result)
                        }
                    }
                }
            }
            
            project.logger.info("Distributed build completed successfully")
            
        } catch (Exception e) {
            project.logger.error("Distributed compilation failed", e)
            throw e
        }
    }
    
    private String generateCacheKey(ModuleInfo module) {
        // Simple cache key generation
        return "${module.name}-${module.getSourceFiles().hashCode()}-${module.getJavaVersion()}"
    }
}

/**
 * Extension for configuring the distributed build
 */
class DistBuildExtension {
    boolean enabled = true
    String coordinatorHost = "localhost"
    int coordinatorPort = 8080
    String cacheDir = null
    int maxConcurrentTasks = 4
    boolean fallbackToLocal = true
    
    // Configuration methods
    void enabled(boolean enabled) { this.enabled = enabled }
    void coordinatorHost(String host) { this.coordinatorHost = host }
    void coordinatorPort(int port) { this.coordinatorPort = port }
    void cacheDir(String dir) { this.cacheDir = dir }
    void maxConcurrentTasks(int tasks) { this.maxConcurrentTasks = tasks }
    void fallbackToLocal(boolean fallback) { this.fallbackToLocal = fallback }
}
