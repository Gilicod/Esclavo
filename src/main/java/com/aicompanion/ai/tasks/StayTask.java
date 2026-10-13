package com.aicompanion.ai.tasks;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

public class StayTask extends CompanionTask {
    private BlockPos stayPos;

    public StayTask(CompanionEntity companion) {
        super(companion);
        this.stayPos = companion.blockPosition();
    }

    @Override
    public void start() {
        this.stayPos = companion.blockPosition();
        companion.getNavManager().stop();
        companion.setCurrentState(CompanionState.STAYING);
    }

    @Override
    public void tick() {
        if (paused) return;
        companion.setCurrentState(CompanionState.STAYING);
        if (companion.blockPosition().distSqr(stayPos) > 4.0) {
            companion.getNavManager().moveTo(stayPos, 1.0);
        }
    }

    @Override
    public String getDescription() { return "Quedarse en posición"; }
    @Override
    public String getProgress() { return "Esperando"; }
    @Override
    public String getTaskType() { return "STAY"; }
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (stayPos != null) tag.put("StayPos", NbtUtils.writeBlockPos(stayPos));
        return tag;
    }
    @Override
    public void load(CompoundTag tag) {
        if (tag.contains("StayPos")) this.stayPos = NbtUtils.readBlockPos(tag.getCompound("StayPos"));
    }
}
