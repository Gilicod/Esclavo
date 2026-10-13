package com.aicompanion.internal.baritone.path;

/**
 * Result of a path calculation.
 * Adapted from Baritone (LGPL-3.0) PathCalculationResult.
 */
public class PathResult {
    public enum Status {
        FOUND,
        PARTIAL,
        TIMED_OUT,
        IMPOSSIBLE
    }

    private final Status status;
    private final Path path;
    private final int nodesExplored;
    private final long executionTimeMs;

    public PathResult(Status status, Path path, int nodesExplored, long executionTimeMs) {
        this.status = status;
        this.path = path;
        this.nodesExplored = nodesExplored;
        this.executionTimeMs = executionTimeMs;
    }

    public static PathResult found(Path path, int nodes, long timeMs) {
        return new PathResult(Status.FOUND, path, nodes, timeMs);
    }

    public static PathResult partial(Path path, int nodes, long timeMs) {
        return new PathResult(Status.PARTIAL, path, nodes, timeMs);
    }

    public static PathResult impossible(int nodes, long timeMs) {
        return new PathResult(Status.IMPOSSIBLE, null, nodes, timeMs);
    }

    public static PathResult timedOut(Path partialPath, int nodes, long timeMs) {
        return new PathResult(Status.TIMED_OUT, partialPath, nodes, timeMs);
    }

    public boolean hasPath() {
        return path != null && !path.isEmpty();
    }

    public Status getStatus() {
        return status;
    }

    public Path getPath() {
        return path;
    }

    public int getNodesExplored() {
        return nodesExplored;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    @Override
    public String toString() {
        return "PathResult[" + status + ", length=" + (path != null ? path.length() : 0) +
                ", nodes=" + nodesExplored + ", time=" + executionTimeMs + "ms]";
    }
}
