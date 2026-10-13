/*
 * Server-side adaptation of Baritone v1.20.0 AStarPathFinder / AbstractNodeCostSearch.
 * Copyright Baritone contributors. LGPL-3.0-or-later. See bundled licenses.
 * Adaptation: isolated graph interface, bounded synchronous budget, 1.20.1 coordinates,
 * and movement metadata for CompanionEntity. No client, player controls or commands.
 */
package com.aicompanion.internal.baritone.v120;

import com.aicompanion.internal.baritone.goals.Goal;
import com.aicompanion.internal.baritone.v120.openset.BinaryHeapOpenSet;
import java.util.*;

public final class NpcPathSearch {
    private static final double[] COEFFICIENTS = {1.5, 2, 2.5, 3, 4, 5, 10};
    private static final double MIN_IMPROVEMENT = 0.01;
    @FunctionalInterface public interface Sink {
        void offer(int x, int y, int z, double cost, Object movement);
    }
    @FunctionalInterface public interface Graph {
        void expand(int x, int y, int z, Sink sink);
    }
    public record Step(int x, int y, int z, double cost, Object movement) {}
    public record Result(List<Step> steps, boolean reached, int nodes, long milliseconds) {}
    public static long key(int x, int y, int z) {
        return ((long)x & 0x3ffffffL) << 38 | ((long)z & 0x3ffffffL) << 12 | ((long)y & 0xfffL);
    }

    public static Result search(int x, int y, int z, Goal goal, Graph graph,
                                int maxNodes, long milliseconds) {
        long startTime = System.nanoTime();
        long budget = Math.max(1, milliseconds) * 1_000_000L;
        Map<Long, PathNode> nodes = new HashMap<>();
        Map<PathNode, Object> incoming = new IdentityHashMap<>();
        BinaryHeapOpenSet open = new BinaryHeapOpenSet();
        PathNode start = new PathNode(x, y, z, goal);
        start.cost = 0;
        start.combinedCost = start.estimatedCostToGoal;
        nodes.put(key(x,y,z), start);
        open.insert(start);
        PathNode[] best = new PathNode[COEFFICIENTS.length];
        double[] bestMetric = new double[COEFFICIENTS.length];
        Arrays.fill(best, start);
        Arrays.fill(bestMetric, start.estimatedCostToGoal);
        int explored = 0;
        while (!open.isEmpty() && explored < Math.max(1,maxNodes)) {
            if ((explored & 15) == 0 && System.nanoTime() - startTime >= budget) break;
            PathNode current = open.removeLowest();
            explored++;
            if (goal.isInGoal(current.x,current.y,current.z))
                return result(current, incoming, true, explored, startTime);
            graph.expand(current.x, current.y, current.z, (nx,ny,nz,cost,data) -> {
                if (!Double.isFinite(cost) || cost <= 0 || cost >= 1_000_000) return;
                if (nx < -30_000_000 || nx > 30_000_000 || nz < -30_000_000 || nz > 30_000_000 || ny < -2048 || ny > 2047) return;
                PathNode neighbor = nodes.computeIfAbsent(key(nx,ny,nz), k -> new PathNode(nx,ny,nz,goal));
                if (!Double.isFinite(neighbor.estimatedCostToGoal)) return;
                double tentative = current.cost + cost;
                if (neighbor.cost - tentative <= MIN_IMPROVEMENT) return;
                neighbor.previous = current;
                neighbor.cost = tentative;
                neighbor.combinedCost = tentative + neighbor.estimatedCostToGoal;
                incoming.put(neighbor,data);
                if (neighbor.isOpen()) open.update(neighbor); else open.insert(neighbor);
                for (int i=0;i<COEFFICIENTS.length;i++) {
                    double metric=neighbor.estimatedCostToGoal+neighbor.cost/COEFFICIENTS[i];
                    if (bestMetric[i]-metric>MIN_IMPROVEMENT) {bestMetric[i]=metric;best[i]=neighbor;}
                }
            });
        }
        // Return the best partial path that makes progress towards the goal or displaces the entity meaningfully.
        for (PathNode candidate : best) {
            if (candidate == null || candidate == start) continue;
            long dx = (long) candidate.x - x, dy = (long) candidate.y - y, dz = (long) candidate.z - z;
            long distSq = dx * dx + dy * dy + dz * dz;
            if (distSq >= 1 && candidate.estimatedCostToGoal < start.estimatedCostToGoal) {
                return result(candidate, incoming, false, explored, startTime);
            }
        }
        for (PathNode candidate : best) {
            if (candidate == null || candidate == start) continue;
            long dx = (long) candidate.x - x, dy = (long) candidate.y - y, dz = (long) candidate.z - z;
            long distSq = dx * dx + dy * dy + dz * dz;
            if (distSq >= 4) {
                return result(candidate, incoming, false, explored, startTime);
            }
        }
        return new Result(List.of(), false, explored, (System.nanoTime() - startTime) / 1_000_000);
    }
    private static Result result(PathNode end, Map<PathNode,Object> incoming, boolean reached,int count,long started) {
        List<Step> path=new ArrayList<>();
        for (PathNode n=end;n!=null;n=n.previous) path.add(new Step(n.x,n.y,n.z,n.cost,incoming.get(n)));
        Collections.reverse(path);
        return new Result(List.copyOf(path),reached,count,(System.nanoTime()-started)/1_000_000);
    }
}
