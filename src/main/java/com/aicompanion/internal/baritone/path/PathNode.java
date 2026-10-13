package com.aicompanion.internal.baritone.path;

import com.aicompanion.internal.baritone.movements.MovementType;
import net.minecraft.core.BlockPos;

/**
 * Node in the 3D voxel A* graph.
 * Adapted from Baritone (LGPL-3.0) PathNode.
 */
public class PathNode implements Comparable<PathNode> {
    public final int x;
    public final int y;
    public final int z;

    public final PathNode parent;
    public final MovementType movementType;
    public final BlockPos actionPos; // Target for BREAK_BLOCK or PLACE_BLOCK if applicable

    public double gCost;
    public double hCost;
    public double fCost;

    public PathNode(int x, int y, int z, PathNode parent, MovementType movementType, BlockPos actionPos, double gCost, double hCost) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.parent = parent;
        this.movementType = movementType;
        this.actionPos = actionPos;
        this.gCost = gCost;
        this.hCost = hCost;
        this.fCost = gCost + hCost;
    }

    public BlockPos getPos() {
        return new BlockPos(x, y, z);
    }

    @Override
    public int compareTo(PathNode other) {
        return Double.compare(this.fCost, other.fCost);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PathNode other)) return false;
        return this.x == other.x && this.y == other.y && this.z == other.z;
    }

    @Override
    public int hashCode() {
        int hash = x;
        hash = 31 * hash + y;
        hash = 31 * hash + z;
        return hash;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ") " + movementType + " f=" + String.format("%.2f", fCost);
    }
}
