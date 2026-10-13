package com.aicompanion.ai.spatial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.function.Predicate;

/**
 * Enterprise 3D Spatial Clustering Engine for Esclavo.
 * Implements 3D Flood-Fill (BFS) across Moore neighborhoods (26 directions)
 * to detect complete object structures (entire trees, entire ore veins).
 *
 * Enforces Target Locking to eliminate spatial attention deficit:
 * once an object is locked, SearchRegionManager is globally inhibited until
 * the entire cluster is cleared.
 */
public class SpatialClusterManager {

    private static final int MAX_TREE_CLUSTER_SIZE = 128;
    private static final int MAX_ORE_CLUSTER_SIZE = 48;

    /**
     * Scans and aggregates a complete tree structure via 3D BFS Flood-Fill.
     * Traverses connected logs in 26 3D directions (faces, edges, diagonals)
     * to capture natural branching, split trunks, and diagonal canopies.
     *
     * @param sl        The ServerLevel
     * @param seedLog   The discovered initial log
     * @param isMatchingLog Predicate defining whether a block belongs to this tree family
     * @return List of all connected logs, sorted Top-Down (highest Y first)
     */
    public static List<BlockPos> scanTreeCluster(ServerLevel sl, BlockPos seedLog, Predicate<BlockState> isMatchingLog) {
        List<BlockPos> cluster = new ArrayList<>();
        if (sl == null || seedLog == null || isMatchingLog == null) return cluster;

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        // 1. Trace down to root base
        BlockPos base = seedLog;
        while (base.getY() > sl.getMinBuildHeight() && isMatchingLog.test(sl.getBlockState(base.below()))) {
            base = base.below();
        }

        queue.add(base);
        visited.add(base);

        // 2. 3D Flood-Fill (BFS) across Moore 26-neighborhood
        while (!queue.isEmpty() && cluster.size() < MAX_TREE_CLUSTER_SIZE) {
            BlockPos current = queue.poll();
            BlockState state = sl.getBlockState(current);

            if (isMatchingLog.test(state)) {
                cluster.add(current.immutable());

                // Scan 26 neighboring blocks in 3D (including diagonals)
                for (int dy = -1; dy <= 2; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos neighbor = current.offset(dx, dy, dz);
                            if (!visited.contains(neighbor)) {
                                visited.add(neighbor);
                                if (sl.isLoaded(neighbor)) {
                                    BlockState ns = sl.getBlockState(neighbor);
                                    if (isMatchingLog.test(ns)) {
                                        queue.add(neighbor);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Top-Down Destruction Ordering:
        // Mandatory Pilar 2 rule: sort strictly by Y descending (highest logs first)
        // Tie-breaker: closest to horizontal center of trunk
        final BlockPos centerBase = base;
        cluster.sort((a, b) -> {
            int yCmp = Integer.compare(b.getY(), a.getY()); // Highest Y first!
            if (yCmp != 0) return yCmp;
            int dxA = a.getX() - centerBase.getX();
            int dzA = a.getZ() - centerBase.getZ();
            int dxB = b.getX() - centerBase.getX();
            int dzB = b.getZ() - centerBase.getZ();
            return Integer.compare(dxA * dxA + dzA * dzA, dxB * dxB + dzB * dzB);
        });

        return cluster;
    }

    /**
     * Scans and aggregates a complete ore vein cluster via 3D BFS Flood-Fill.
     * Captures all connected ore blocks in 26 3D directions.
     *
     * @param sl         The ServerLevel
     * @param seedOre    The discovered initial ore block
     * @param isMatchingOre Predicate defining whether a block matches target ore
     * @param companionPos Current companion position for distance sorting
     * @return List of all connected ore blocks in the vein
     */
    public static List<BlockPos> scanOreVeinCluster(ServerLevel sl, BlockPos seedOre, Predicate<BlockState> isMatchingOre, BlockPos companionPos) {
        List<BlockPos> cluster = new ArrayList<>();
        if (sl == null || seedOre == null || isMatchingOre == null) return cluster;

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        queue.add(seedOre);
        visited.add(seedOre);

        while (!queue.isEmpty() && cluster.size() < MAX_ORE_CLUSTER_SIZE) {
            BlockPos current = queue.poll();
            BlockState state = sl.getBlockState(current);

            if (isMatchingOre.test(state)) {
                cluster.add(current.immutable());

                // Scan 26 neighboring blocks in 3D
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos neighbor = current.offset(dx, dy, dz);
                            if (!visited.contains(neighbor)) {
                                visited.add(neighbor);
                                if (sl.isLoaded(neighbor)) {
                                    BlockState ns = sl.getBlockState(neighbor);
                                    if (isMatchingOre.test(ns)) {
                                        queue.add(neighbor);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sort: mine closest to companion first, with top-down safety as tie-breaker
        cluster.sort((a, b) -> {
            int distCmp = Double.compare(a.distSqr(companionPos), b.distSqr(companionPos));
            if (distCmp != 0) return distCmp;
            return Integer.compare(b.getY(), a.getY());
        });

        return cluster;
    }

    /**
     * Checks if any leaf blocks directly obstruct line-of-sight or reach path between
     * companion and the target log, clearing them if necessary.
     */
    public static boolean clearObstructingLeaves(ServerLevel sl, BlockPos targetLog, BlockPos companionEyePos, com.aicompanion.entity.CompanionEntity companion) {
        if (sl == null || targetLog == null || companionEyePos == null) return false;

        // Check immediate adjacent blocks around target log for obstructing leaves
        boolean clearedAny = false;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            BlockPos adj = targetLog.relative(dir);
            BlockState adjState = sl.getBlockState(adj);
            if (adjState.is(BlockTags.LEAVES)) {
                // If leaf is between companion and log, break it
                if (adj.distSqr(companionEyePos) < targetLog.distSqr(companionEyePos)) {
                    sl.destroyBlock(adj, true, companion);
                    clearedAny = true;
                }
            }
        }
        return clearedAny;
    }
}
