package com.aicompanion.internal.baritone.goals;

import net.minecraft.core.BlockPos;

/**
 * Goal definition adapted from Baritone (LGPL-3.0) for Esclavo autonomous navigation.
 * Evaluates whether a coordinate satisfies the destination criteria,
 * and provides an admissible heuristic estimation of distance.
 */
public interface Goal {
    boolean isInGoal(int x, int y, int z);
    double heuristic(int x, int y, int z);

    default boolean isInGoal(BlockPos pos) {
        return isInGoal(pos.getX(), pos.getY(), pos.getZ());
    }

    default double heuristic(BlockPos pos) {
        return heuristic(pos.getX(), pos.getY(), pos.getZ());
    }

    String getDescription();
}
