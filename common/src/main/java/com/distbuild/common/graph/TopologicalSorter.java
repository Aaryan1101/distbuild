package com.distbuild.common.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Implements topological sorting for dependency graphs
 */
public class TopologicalSorter {
    private static final Logger logger = LoggerFactory.getLogger(TopologicalSorter.class);

    public List<String> sort(ModuleGraph graph) throws CycleDetectedException {
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        List<String> result = new ArrayList<>();
        Set<String> allModules = graph.getAllModules();

        logger.debug("Starting topological sort for {} modules", allModules.size());

        for (String module : allModules) {
            if (!visited.contains(module)) {
                dfs(module, graph, visited, visiting, result);
            }
        }

        logger.debug("Topological sort completed: {}", result);
        return result;
    }

    private void dfs(String module, ModuleGraph graph, Set<String> visited, 
                    Set<String> visiting, List<String> result) throws CycleDetectedException {
        
        if (visiting.contains(module)) {
            // Cycle detected
            List<String> cycle = extractCycle(module, graph, visiting, new ArrayList<>());
            throw new CycleDetectedException("Cycle detected in dependency graph: " + cycle);
        }

        if (visited.contains(module)) {
            return;
        }

        visiting.add(module);
        logger.trace("Visiting module: {}", module);

        Set<String> dependencies = graph.getDependencies(module);
        for (String dep : dependencies) {
            dfs(dep, graph, visited, visiting, result);
        }

        visiting.remove(module);
        visited.add(module);
        result.add(module);
        logger.trace("Added module to result: {}", module);
    }

    private List<String> extractCycle(String startModule, ModuleGraph graph, 
                                     Set<String> visiting, List<String> cyclePath) {
        cyclePath.add(startModule);
        
        if (cyclePath.size() > graph.size()) {
            // We've looped back to the start
            return new ArrayList<>(cyclePath);
        }

        Set<String> dependencies = graph.getDependencies(startModule);
        for (String dep : dependencies) {
            if (visiting.contains(dep)) {
                return extractCycle(dep, graph, visiting, cyclePath);
            }
        }
        
        return cyclePath;
    }

    /**
     * Alternative implementation using Kahn's algorithm (BFS)
     */
    public List<String> sortKahn(ModuleGraph graph) throws CycleDetectedException {
        Map<String, Integer> inDegree = new HashMap<>();
        Queue<String> queue = new LinkedList<>();
        List<String> result = new ArrayList<>();

        // Calculate in-degrees
        Set<String> allModules = graph.getAllModules();
        for (String module : allModules) {
            inDegree.put(module, 0);
        }

        for (String module : allModules) {
            Set<String> deps = graph.getDependencies(module);
            for (String dep : deps) {
                inDegree.put(module, inDegree.get(module) + 1);
            }
        }

        // Find modules with no dependencies
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        // Process modules
        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(current);

            Set<String> dependents = graph.getDependents(current);
            for (String dependent : dependents) {
                inDegree.put(dependent, inDegree.get(dependent) - 1);
                if (inDegree.get(dependent) == 0) {
                    queue.add(dependent);
                }
            }
        }

        // Check if all modules were processed
        if (result.size() != allModules.size()) {
            throw new CycleDetectedException("Cycle detected in dependency graph");
        }

        return result;
    }
}
