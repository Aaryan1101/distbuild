package com.distbuild.common.graph;

import com.distbuild.common.model.ModuleInfo;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.*;

public class ModuleGraphTest {

    @Test
    public void testEmptyGraph() {
        ModuleGraph graph = new ModuleGraph();
        assertTrue(graph.isEmpty());
        assertEquals(0, graph.size());
        assertFalse(graph.hasCycle());
    }

    @Test
    public void testSingleModule() {
        ModuleInfo module = ModuleInfo.builder()
                .name("module-a")
                .projectPath(Paths.get("module-a"))
                .outputDirectory(Paths.get("module-a/build/classes"))
                .build();

        ModuleGraph graph = ModuleGraph.builder()
                .addModule(module)
                .build();

        assertFalse(graph.isEmpty());
        assertEquals(1, graph.size());
        assertFalse(graph.hasCycle());
        assertTrue(graph.getAllModules().contains("module-a"));
        assertTrue(graph.getDependencies("module-a").isEmpty());
        assertTrue(graph.getDependents("module-a").isEmpty());
    }

    @Test
    public void testSimpleDependencyChain() {
        ModuleInfo moduleA = ModuleInfo.builder()
                .name("module-a")
                .projectPath(Paths.get("module-a"))
                .outputDirectory(Paths.get("module-a/build/classes"))
                .build();

        ModuleInfo moduleB = ModuleInfo.builder()
                .name("module-b")
                .projectPath(Paths.get("module-b"))
                .outputDirectory(Paths.get("module-b/build/classes"))
                .addDependency("module-a")
                .build();

        ModuleInfo moduleC = ModuleInfo.builder()
                .name("module-c")
                .projectPath(Paths.get("module-c"))
                .outputDirectory(Paths.get("module-c/build/classes"))
                .addDependency("module-b")
                .build();

        ModuleGraph graph = ModuleGraph.builder()
                .addModule(moduleA)
                .addModule(moduleB)
                .addModule(moduleC)
                .build();

        assertEquals(3, graph.size());
        assertFalse(graph.hasCycle());

        // Check dependencies
        assertTrue(graph.getDependencies("module-a").isEmpty());
        assertEquals(Set.of("module-a"), graph.getDependencies("module-b"));
        assertEquals(Set.of("module-b"), graph.getDependencies("module-c"));

        // Check dependents
        assertEquals(Set.of("module-b"), graph.getDependents("module-a"));
        assertEquals(Set.of("module-c"), graph.getDependents("module-b"));
        assertTrue(graph.getDependents("module-c").isEmpty());
    }

    @Test
    public void testTopologicalOrder() throws Exception {
        ModuleInfo moduleA = ModuleInfo.builder()
                .name("module-a")
                .projectPath(Paths.get("module-a"))
                .outputDirectory(Paths.get("module-a/build/classes"))
                .build();

        ModuleInfo moduleB = ModuleInfo.builder()
                .name("module-b")
                .projectPath(Paths.get("module-b"))
                .outputDirectory(Paths.get("module-b/build/classes"))
                .addDependency("module-a")
                .build();

        ModuleInfo moduleC = ModuleInfo.builder()
                .name("module-c")
                .projectPath(Paths.get("module-c"))
                .outputDirectory(Paths.get("module-c/build/classes"))
                .addDependency("module-a")
                .addDependency("module-b")
                .build();

        ModuleGraph graph = ModuleGraph.builder()
                .addModule(moduleA)
                .addModule(moduleB)
                .addModule(moduleC)
                .build();

        List<String> order = graph.getTopologicalOrder();
        assertEquals(3, order.size());
        
        // module-a must come first (no dependencies)
        assertEquals("module-a", order.get(0));
        
        // module-b must come before module-c
        int bIndex = order.indexOf("module-b");
        int cIndex = order.indexOf("module-c");
        assertTrue(bIndex < cIndex);
    }

    @Test
    public void testParallelBatches() throws Exception {
        // Create a diamond dependency: A -> (B, C) -> D
        ModuleInfo moduleA = ModuleInfo.builder()
                .name("module-a")
                .projectPath(Paths.get("module-a"))
                .outputDirectory(Paths.get("module-a/build/classes"))
                .build();

        ModuleInfo moduleB = ModuleInfo.builder()
                .name("module-b")
                .projectPath(Paths.get("module-b"))
                .outputDirectory(Paths.get("module-b/build/classes"))
                .addDependency("module-a")
                .build();

        ModuleInfo moduleC = ModuleInfo.builder()
                .name("module-c")
                .projectPath(Paths.get("module-c"))
                .outputDirectory(Paths.get("module-c/build/classes"))
                .addDependency("module-a")
                .build();

        ModuleInfo moduleD = ModuleInfo.builder()
                .name("module-d")
                .projectPath(Paths.get("module-d"))
                .outputDirectory(Paths.get("module-d/build/classes"))
                .addDependency("module-b")
                .addDependency("module-c")
                .build();

        ModuleGraph graph = ModuleGraph.builder()
                .addModule(moduleA)
                .addModule(moduleB)
                .addModule(moduleC)
                .addModule(moduleD)
                .build();

        List<List<String>> batches = graph.getParallelBatches();
        assertEquals(3, batches.size());
        
        // First batch: module-a (no dependencies)
        assertEquals(Set.of("module-a"), new HashSet<>(batches.get(0)));
        
        // Second batch: module-b and module-c (both depend only on a)
        assertEquals(Set.of("module-b", "module-c"), new HashSet<>(batches.get(1)));
        
        // Third batch: module-d (depends on b and c)
        assertEquals(Set.of("module-d"), new HashSet<>(batches.get(2)));
    }

    @Test(expected = CycleDetectedException.class)
    public void testCycleDetection() throws Exception {
        ModuleInfo moduleA = ModuleInfo.builder()
                .name("module-a")
                .projectPath(Paths.get("module-a"))
                .outputDirectory(Paths.get("module-a/build/classes"))
                .addDependency("module-b")
                .build();

        ModuleInfo moduleB = ModuleInfo.builder()
                .name("module-b")
                .projectPath(Paths.get("module-b"))
                .outputDirectory(Paths.get("module-b/build/classes"))
                .addDependency("module-a")
                .build();

        ModuleGraph graph = ModuleGraph.builder()
                .addModule(moduleA)
                .addModule(moduleB)
                .build();

        // This should throw CycleDetectedException
        graph.getTopologicalOrder();
    }

    @Test
    public void testAnnotationProcessorFiltering() throws Exception {
        ModuleInfo moduleA = ModuleInfo.builder()
                .name("module-a")
                .projectPath(Paths.get("module-a"))
                .outputDirectory(Paths.get("module-a/build/classes"))
                .build();

        ModuleInfo moduleB = ModuleInfo.builder()
                .name("module-b")
                .projectPath(Paths.get("module-b"))
                .outputDirectory(Paths.get("module-b/build/classes"))
                .addDependency("module-a")
                .hasAnnotationProcessors(true)
                .build();

        ModuleInfo moduleC = ModuleInfo.builder()
                .name("module-c")
                .projectPath(Paths.get("module-c"))
                .outputDirectory(Paths.get("module-c/build/classes"))
                .addDependency("module-b")
                .build();

        ModuleGraph graph = ModuleGraph.builder()
                .addModule(moduleA)
                .addModule(moduleB)
                .addModule(moduleC)
                .build();

        List<String> distributableModules = graph.getModulesInCompilationOrder();
        
        // Should only include modules without annotation processors
        assertEquals(2, distributableModules.size());
        assertTrue(distributableModules.contains("module-a"));
        assertFalse(distributableModules.contains("module-b")); // Has annotation processors
        assertTrue(distributableModules.contains("module-c"));
    }
}
