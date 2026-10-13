package com.aicompanion.internal.baritone.goals;

import net.minecraft.core.BlockPos;

/**
 * Goal satisfied when the entity is standing in a position suitable to
 * interact with, mine, or place against a target block.
 * Adapted from Baritone (LGPL-3.0) GoalGetToBlock.
 */
public class GoalInteractBlock implements Goal {
    private final BlockPos targetBlock;
    private final double maxReachSq;
    private final double maxReach;

    public GoalInteractBlock(BlockPos targetBlock, double maxReach) {
        this.targetBlock = targetBlock;
        this.maxReach = Math.max(1.0, maxReach);
        this.maxReachSq = this.maxReach * this.maxReach;
    }

    public GoalInteractBlock(BlockPos targetBlock) {
        this(targetBlock, 3.8);
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        // Can't be inside the block itself
        if (targetBlock.getX() == x && targetBlock.getY() == y && targetBlock.getZ() == z) {
            return false;
        }
        double dx = (targetBlock.getX() + 0.5) - (x + 0.5);
        double dy = (targetBlock.getY() + 0.5) - (y + 1.0); // Eye height approximation
        double dz = (targetBlock.getZ() + 0.5) - (z + 0.5);
        return (dx * dx + dy * dy + dz * dz) <= maxReachSq;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        double dx = (targetBlock.getX() + 0.5) - (x + 0.5);
        double dy = (targetBlock.getY() + 0.5) - (y + 0.5);
        double dz = (targetBlock.getZ() + 0.5) - (z + 0.5);
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return Math.max(0.0, dist - maxReach);
    }

    public BlockPos getTargetBlock() {
        return targetBlock;
    }

    public double getMaxReach() {
        return maxReach;
    }

    @Override
    public String getDescription() {
        return "Interactuar con bloque " + targetBlock.toShortString();
    }
}
