package com.aicompanion.ai.memory;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class ShortTermMemory {
    private final Deque<String> recentFailures = new ArrayDeque<>();
    private final Deque<String> recentOrders = new ArrayDeque<>();

    // Target Lock (Pilar 2: Conciencia Espacial y Permanencia de Objetivos)
    private final List<BlockPos> lockedCluster = new ArrayList<>();
    private BlockPos activeClusterOrigin = null;
    private String activeClusterType = "";

    public void addFailure(String failure) {
        if (recentFailures.size() >= 5) recentFailures.pollFirst();
        recentFailures.addLast(failure);
    }

    public void addOrder(String order) {
        if (recentOrders.size() >= 5) recentOrders.pollFirst();
        recentOrders.addLast(order);
    }

    public Deque<String> getRecentFailures() { return recentFailures; }
    public Deque<String> getRecentOrders() { return recentOrders; }

    public void lockCluster(String type, BlockPos origin, List<BlockPos> cluster) {
        this.lockedCluster.clear();
        this.activeClusterType = type != null ? type : "";
        this.activeClusterOrigin = origin != null ? origin.immutable() : null;
        if (cluster != null) {
            for (BlockPos p : cluster) {
                if (p != null) this.lockedCluster.add(p.immutable());
            }
        }
        com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-SPATIAL] ShortTermMemory: Target Lock engaged on '{}' at {} with {} blocks.",
                activeClusterType, activeClusterOrigin, lockedCluster.size());
    }

    public boolean hasLockedCluster() {
        return !lockedCluster.isEmpty();
    }

    public List<BlockPos> getLockedCluster() {
        return lockedCluster;
    }

    public BlockPos peekNextClusterBlock() {
        if (lockedCluster.isEmpty()) return null;
        return lockedCluster.get(0);
    }

    public BlockPos popNextClusterBlock() {
        if (lockedCluster.isEmpty()) return null;
        return lockedCluster.remove(0);
    }

    public void removeClusterBlock(BlockPos pos) {
        if (pos != null) {
            lockedCluster.remove(pos);
        }
    }

    public void clearLockedCluster() {
        if (!lockedCluster.isEmpty() || activeClusterOrigin != null) {
            com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-SPATIAL] ShortTermMemory: Target Lock released for '{}'.", activeClusterType);
        }
        this.lockedCluster.clear();
        this.activeClusterOrigin = null;
        this.activeClusterType = "";
    }

    public BlockPos getActiveClusterOrigin() {
        return activeClusterOrigin;
    }

    public String getActiveClusterType() {
        return activeClusterType;
    }

    public CompoundTag save() { return new CompoundTag(); }
    public void load(CompoundTag tag) {}
}
