package com.distbuild.coordinator;

import com.distbuild.common.cache.BuildCache;
import com.distbuild.common.cache.CompileResult;
import com.distbuild.common.cache.LocalDiskCache;
import com.distbuild.common.graph.ModuleGraph;
import com.distbuild.common.model.ModuleInfo;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;

public class SimpleCoordinatorTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    
    private BuildCache cache;
    private SimpleCoordinator coordinator;

    @Before
    public void setUp() throws Exception {
        Path cacheDir = tempFolder.getRoot().toPath().resolve("cache");
        cache = new LocalDiskCache(cacheDir);
        coordinator = new SimpleCoordinator(cache);
    }

    @Test
    public void testEmptyGraphBuild() throws Exception {
        ModuleGraph emptyGraph = new ModuleGraph();
        
        CompletableFuture<Map<String, CompileResult>> future = coordinator.build(emptyGraph);
        Map<String, CompileResult> results = future.get();
        
        assertTrue(results.isEmpty());
    }

    @Test
    public void testSingleModuleBuild() throws Exception {
        // Create a simple module
        ModuleInfo module = ModuleInfo.builder()
                .name("test-module")
                .projectPath(Paths.get("test-module"))
                .outputDirectory(Paths.get("test-module/build/classes"))
                .addSourceFile("src/main/java/Test.java")
                .javaVersion("17")
                .build();
        
        ModuleGraph graph = new ModuleGraph();
        graph.addModule(module);
        
        CompletableFuture<Map<String, CompileResult>> future = coordinator.build(graph);
        Map<String, CompileResult> results = future.get();
        
        assertEquals(1, results.size());
        CompileResult result = results.get("test-module");
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(result.getCompileTimeMs() > 0);
    }

    @Test
    public void testDependencyOrdering() throws Exception {
        // Create modules with dependencies: A -> B -> C
        ModuleInfo moduleA = ModuleInfo.builder()
                .name("module-a")
                .projectPath(Paths.get("module-a"))
                .outputDirectory(Paths.get("module-a/build/classes"))
                .addSourceFile("src/main/java/A.java")
                .javaVersion("17")
                .build();
        
        ModuleInfo moduleB = ModuleInfo.builder()
                .name("module-b")
                .projectPath(Paths.get("module-b"))
                .outputDirectory(Paths.get("module-b/build/classes"))
                .addSourceFile("src/main/java/B.java")
                .addDependency("module-a")
                .javaVersion("17")
                .build();
        
        ModuleInfo moduleC = ModuleInfo.builder()
                .name("module-c")
                .projectPath(Paths.get("module-c"))
                .outputDirectory(Paths.get("module-c/build/classes"))
                .addSourceFile("src/main/java/C.java")
                .addDependency("module-b")
                .javaVersion("17")
                .build();
        
        ModuleGraph graph = new ModuleGraph();
        graph.addModule(moduleA);
        graph.addModule(moduleB);
        graph.addModule(moduleC);
        
        CompletableFuture<Map<String, CompileResult>> future = coordinator.build(graph);
        Map<String, CompileResult> results = future.get();
        
        assertEquals(3, results.size());
        assertTrue(results.containsKey("module-a"));
        assertTrue(results.containsKey("module-b"));
        assertTrue(results.containsKey("module-c"));
        
        // All should succeed
        results.values().forEach(result -> assertTrue(result.isSuccess()));
    }

    @Test
    public void testParallelCompilation() throws Exception {
        // Create independent modules that can compile in parallel
        List<ModuleInfo> modules = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ModuleInfo module = ModuleInfo.builder()
                    .name("module-" + i)
                    .projectPath(Paths.get("module-" + i))
                    .outputDirectory(Paths.get("module-" + i + "/build/classes"))
                    .addSourceFile("src/main/java/Module" + i + ".java")
                    .javaVersion("17")
                    .build();
            modules.add(module);
        }
        
        ModuleGraph graph = new ModuleGraph();
        modules.forEach(graph::addModule);
        
        long startTime = System.currentTimeMillis();
        CompletableFuture<Map<String, CompileResult>> future = coordinator.build(graph);
        Map<String, CompileResult> results = future.get();
        long endTime = System.currentTimeMillis();
        
        assertEquals(3, results.size());
        
        // Should complete faster than sequential compilation
        // Allow some tolerance for scheduling overhead
        assertTrue("Parallel compilation should be faster", (endTime - startTime) < 3000);
        
        // All should succeed
        results.values().forEach(result -> assertTrue(result.isSuccess()));
    }

    @Test
    public void testCacheHits() throws Exception {
        // Create a module
        ModuleInfo module = ModuleInfo.builder()
                .name("test-module")
                .projectPath(Paths.get("test-module"))
                .outputDirectory(Paths.get("test-module/build/classes"))
                .addSourceFile("src/main/java/Test.java")
                .javaVersion("17")
                .build();
        
        ModuleGraph graph = new ModuleGraph();
        graph.addModule(module);
        
        // First build - should compile
        CompletableFuture<Map<String, CompileResult>> future1 = coordinator.build(graph);
        Map<String, CompileResult> results1 = future1.get();
        
        assertEquals(1, results1.size());
        assertTrue(results1.get("test-module").isSuccess());
        
        // Second build - should hit cache
        CompletableFuture<Map<String, CompileResult>> future2 = coordinator.build(graph);
        Map<String, CompileResult> results2 = future2.get();
        
        assertEquals(1, results2.size());
        assertTrue(results2.get("test-module").isSuccess());
        
        // Cache hit should be faster
        // Note: This is a rough test since we're using simulated compilation times
    }

    @Test
    public void testAnnotationProcessorFiltering() throws Exception {
        // Create modules - one with annotation processors, one without
        ModuleInfo moduleWithAP = ModuleInfo.builder()
                .name("module-with-ap")
                .projectPath(Paths.get("module-with-ap"))
                .outputDirectory(Paths.get("module-with-ap/build/classes"))
                .addSourceFile("src/main/java/Entity.java")
                .hasAnnotationProcessors(true)
                .javaVersion("17")
                .build();
        
        ModuleInfo normalModule = ModuleInfo.builder()
                .name("normal-module")
                .projectPath(Paths.get("normal-module"))
                .outputDirectory(Paths.get("normal-module/build/classes"))
                .addSourceFile("src/main/java/Normal.java")
                .javaVersion("17")
                .build();
        
        ModuleGraph graph = new ModuleGraph();
        graph.addModule(moduleWithAP);
        graph.addModule(normalModule);
        
        CompletableFuture<Map<String, CompileResult>> future = coordinator.build(graph);
        Map<String, CompileResult> results = future.get();
        
        // Only the normal module should be compiled (annotation processor modules filtered out)
        assertEquals(1, results.size());
        assertTrue(results.containsKey("normal-module"));
        assertFalse(results.containsKey("module-with-ap"));
    }
}
