package com.aicompanion.internal.baritone.goals;

import net.minecraft.core.BlockPos;

/**
 * Goal satisfied only when standing exactly at (x, y, z).
 * Adapted from Baritone (LGPL-3.0).
 */
public class GoalBlock implements Goal {
    private final int x;
    private final int y;
    private final int z;

    public GoalBlock(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public GoalBlock(BlockPos pos) {
        this(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        return this.x == x && this.y == y && this.z == z;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        double dx = this.x - x;
        double dy = this.y - y;
        double dz = this.z - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public BlockPos getPos() {
        return new BlockPos(x, y, z);
    }

    public BlockPos getTarget() {
        return getPos();
    }

    @Override
    public String getDescription() {
        return "Bloque (" + x + ", " + y + ", " + z + ")";
    }
}
