package com.distbuild.common.graph;

import com.distbuild.common.model.ModuleInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents the dependency graph of modules in a Gradle project
 */
public class ModuleGraph {
    private static final Logger logger = LoggerFactory.getLogger(ModuleGraph.class);
    
    private final Map<String, ModuleInfo> modules;
    private final Map<String, Set<String>> dependencyGraph;
    private final Map<String, Set<String>> reverseDependencyGraph;

    public ModuleGraph() {
        this.modules = new HashMap<>();
        this.dependencyGraph = new HashMap<>();
        this.reverseDependencyGraph = new HashMap<>();
    }

    public ModuleGraph(Map<String, ModuleInfo> modules) {
        this.modules = new HashMap<>(modules);
        this.dependencyGraph = new HashMap<>();
        this.reverseDependencyGraph = new HashMap<>();
        buildGraphs();
    }

    public void addModule(ModuleInfo module) {
        modules.put(module.getName(), module);
        buildGraphs();
    }

    public Optional<ModuleInfo> getModule(String name) {
        return Optional.ofNullable(modules.get(name));
    }

    public Set<String> getAllModules() {
        return new HashSet<>(modules.keySet());
    }

    public Set<String> getDependencies(String moduleName) {
        return dependencyGraph.getOrDefault(moduleName, Collections.emptySet());
    }

    public Set<String> getDependents(String moduleName) {
        return reverseDependencyGraph.getOrDefault(moduleName, Collections.emptySet());
    }

    public boolean hasCycle() {
        try {
            new TopologicalSorter().sort(this);
            return false;
        } catch (CycleDetectedException e) {
            return true;
        }
    }

    public List<String> getTopologicalOrder() throws CycleDetectedException {
        return new TopologicalSorter().sort(this);
    }

    public List<String> getModulesInCompilationOrder() throws CycleDetectedException {
        List<String> order = getTopologicalOrder();
        
        // Filter out modules with annotation processors for now (Phase 1 limitation)
        return order.stream()
                .filter(moduleName -> {
                    ModuleInfo module = modules.get(moduleName);
                    return module != null && !module.hasAnnotationProcessors();
                })
                .collect(Collectors.toList());
    }

    public List<List<String>> getParallelBatches() throws CycleDetectedException {
        List<String> sortedModules = getModulesInCompilationOrder();
        List<List<String>> batches = new ArrayList<>();
        
        if (sortedModules.isEmpty()) {
            return batches;
        }

        Set<String> processed = new HashSet<>();
        Set<String> currentBatch = new HashSet<>();

        for (String module : sortedModules) {
            Set<String> deps = getDependencies(module);
            
            // Check if all dependencies have been processed
            if (processed.containsAll(deps)) {
                currentBatch.add(module);
            } else {
                // Start new batch
                if (!currentBatch.isEmpty()) {
                    batches.add(new ArrayList<>(currentBatch));
                    processed.addAll(currentBatch);
                    currentBatch.clear();
                }
                currentBatch.add(module);
            }
        }

        // Add the last batch
        if (!currentBatch.isEmpty()) {
            batches.add(new ArrayList<>(currentBatch));
        }

        return batches;
    }

    public int size() {
        return modules.size();
    }

    public boolean isEmpty() {
        return modules.isEmpty();
    }

    private void buildGraphs() {
        dependencyGraph.clear();
        reverseDependencyGraph.clear();

        for (ModuleInfo module : modules.values()) {
            String moduleName = module.getName();
            
            // Initialize dependency graph entry
            dependencyGraph.putIfAbsent(moduleName, new HashSet<>());
            
            // Add dependencies
            for (String dep : module.getDependencies()) {
                if (modules.containsKey(dep)) {
                    dependencyGraph.get(moduleName).add(dep);
                    
                    // Build reverse dependency graph
                    reverseDependencyGraph.putIfAbsent(dep, new HashSet<>());
                    reverseDependencyGraph.get(dep).add(moduleName);
                } else {
                    logger.warn("Module {} depends on {} which is not found in the graph", 
                               moduleName, dep);
                }
            }
            
            // Ensure reverse graph entry exists for modules with no dependents
            reverseDependencyGraph.putIfAbsent(moduleName, new HashSet<>());
        }
    }

    @Override
    public String toString() {
        return "ModuleGraph{" +
                "modules=" + modules.keySet() +
                ", hasCycle=" + hasCycle() +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, ModuleInfo> modules = new HashMap<>();

        public Builder addModule(ModuleInfo module) {
            modules.put(module.getName(), module);
            return this;
        }

        public ModuleGraph build() {
            return new ModuleGraph(modules);
        }
    }
}
