package com.aicompanion.internal.baritone.goals;

import net.minecraft.core.BlockPos;

/**
 * Goal satisfied on horizontal column (X, Z), ignoring Y height.
 * Adapted from Baritone (LGPL-3.0).
 */
public class GoalXZ implements Goal {
    private final int x;
    private final int z;

    public GoalXZ(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public GoalXZ(BlockPos pos) {
        this(pos.getX(), pos.getZ());
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        return this.x == x && this.z == z;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        double dx = this.x - x;
        double dz = this.z - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public int getX() { return x; }
    public int getZ() { return z; }

    @Override
    public String getDescription() {
        return "Coordenadas XZ (" + x + ", " + z + ")";
    }
}
