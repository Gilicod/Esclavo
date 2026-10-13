package com.aicompanion.internal.baritone.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/**
 * Goal to track and follow a dynamic Entity within a min and max radius.
 * Adapted from Baritone (LGPL-3.0) GoalFollowEntity.
 */
public class GoalEntity implements Goal {
    private final Entity targetEntity;
    private final double minDistanceSq;
    private final double maxDistanceSq;
    private final double targetDistance;

    public GoalEntity(Entity targetEntity, double minDistance, double maxDistance) {
        this.targetEntity = targetEntity;
        this.minDistanceSq = minDistance * minDistance;
        this.maxDistanceSq = maxDistance * maxDistance;
        this.targetDistance = (minDistance + maxDistance) / 2.0;
    }

    public GoalEntity(Entity targetEntity, double desiredDistance) {
        this(targetEntity, Math.max(1.0, desiredDistance - 1.0), desiredDistance + 1.0);
    }

    @Override
    public boolean isInGoal(int x, int y, int z) {
        if (targetEntity == null || !targetEntity.isAlive()) return false;
        double dx = targetEntity.getX() - (x + 0.5);
        double dy = targetEntity.getY() - y;
        double dz = targetEntity.getZ() - (z + 0.5);
        double distSq = dx * dx + dy * dy + dz * dz;
        return distSq >= minDistanceSq && distSq <= maxDistanceSq;
    }

    @Override
    public double heuristic(int x, int y, int z) {
        if (targetEntity == null) return 0.0;
        double dx = targetEntity.getX() - (x + 0.5);
        double dy = targetEntity.getY() - y;
        double dz = targetEntity.getZ() - (z + 0.5);
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return Math.abs(dist - targetDistance);
    }

    public Entity getTargetEntity() {
        return targetEntity;
    }

    public Entity getTarget() {
        return targetEntity;
    }

    @Override
    public String getDescription() {
        return "Seguir entidad " + (targetEntity != null ? targetEntity.getName().getString() : "desconocida");
    }
}
