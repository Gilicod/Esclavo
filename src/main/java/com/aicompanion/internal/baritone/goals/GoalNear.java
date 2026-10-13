package com.aicompanion.internal.baritone.goals;

import net.minecraft.core.BlockPos;

/**
 * Goal satisfied when the entity is within a given 3D distance of target.
 * Adapted from Baritone (LGPL-3.0).
 */
public class GoalNear implements Goal {
    private final int x;
    private final int y;
    private final int z;
    private final double rangeSq;
    private final double range;

    public GoalNear(int x, int y, int z, double range) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.range = Math.max(0.5, range);
        this.rangeSq = this.range * this.range;
    }

    public GoalNear(BlockPos pos, double range) {
        this(pos.getX(), pos.getY(), pos.getZ(), range);
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        double dx = this.x - x;
        double dy = this.y - y;
        double dz = this.z - z;
        return (dx * dx + dy * dy + dz * dz) <= rangeSq;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        double dx = this.x - x;
        double dy = this.y - y;
        double dz = this.z - z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return Math.max(0.0, dist - range);
    }

    public BlockPos getTarget() {
        return new BlockPos(x, y, z);
    }

    public BlockPos getCenter() {
        return getTarget();
    }

    public double getRange() {
        return range;
    }

    @Override
    public String getDescription() {
        return "Cerca de (" + x + ", " + y + ", " + z + ") r=" + String.format("%.1f", range);
    }
}
