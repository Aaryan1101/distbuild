package com.distbuild.common.graph;

import java.util.List;

/**
 * Exception thrown when a cycle is detected in the dependency graph
 */
public class CycleDetectedException extends Exception {
    
    private final List<String> cycle;

    public CycleDetectedException(String message) {
        super(message);
        this.cycle = null;
    }

    public CycleDetectedException(String message, List<String> cycle) {
        super(message);
        this.cycle = cycle;
    }

    public List<String> getCycle() {
        return cycle;
    }

    @Override
    public String toString() {
        if (cycle != null && !cycle.isEmpty()) {
            return super.toString() + ". Cycle: " + String.join(" -> ", cycle) + " -> " + cycle.get(0);
        }
        return super.toString();
    }
}
