/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 */
package com.aicompanion.internal.baritone.goals;

import com.aicompanion.internal.baritone.goals.Goal;
import net.minecraft.core.BlockPos;

public class GoalExploreRegion
implements Goal {
    private final int centerX;
    private final int centerZ;
    private final double radius;
    private final double radiusSq;

    public GoalExploreRegion(int centerX, int centerZ, double radius) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = Math.max(8.0, radius);
        this.radiusSq = this.radius * this.radius;
    }

    public GoalExploreRegion(BlockPos center, double radius) {
        this(center.getX(), center.getZ(), radius);
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        double dx = this.centerX - x;
        double dz = this.centerZ - z;
        return dx * dx + dz * dz >= this.radiusSq;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        double dx = this.centerX - x;
        double dz = this.centerZ - z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        return Math.max(0.0, this.radius - dist);
    }

    @Override
    public String getDescription() {
        return "Explorar sector (" + this.centerX + ", " + this.centerZ + ") r=" + (int)this.radius;
    }
}

