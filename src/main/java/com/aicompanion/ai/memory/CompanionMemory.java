package com.aicompanion.ai.memory;

import net.minecraft.nbt.CompoundTag;

public class CompanionMemory {
    private final CompanionWorldModel worldModel = new CompanionWorldModel();
    private final ShortTermMemory shortTermMemory = new ShortTermMemory();

    public CompanionWorldModel getWorldModel() { return worldModel; }
    public ShortTermMemory getShortTermMemory() { return shortTermMemory; }

    public java.util.Optional<net.minecraft.core.BlockPos> findClosest(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos origin, net.minecraft.world.level.block.Block block, double maxRadius) {
        return worldModel.findClosestPOI(level, origin, block, maxRadius);
    }

    public void rememberPOI(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.Block block, long currentTick) {
        worldModel.rememberPOI(pos, block, currentTick);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("WorldModel", worldModel.save());
        tag.put("ShortTerm", shortTermMemory.save());
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag.contains("WorldModel")) worldModel.load(tag.getCompound("WorldModel"));
        if (tag.contains("ShortTerm")) shortTermMemory.load(tag.getCompound("ShortTerm"));
    }
}
