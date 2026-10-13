package com.aicompanion.ai.tasks;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.player.Player;

public class CarryPlayerTask extends CompanionTask {
    private BlockPos targetPos;

    public CarryPlayerTask(CompanionEntity companion, BlockPos targetPos) {
        super(companion);
        this.targetPos = targetPos;
    }

    @Override
    public void start() {
        Player owner = companion.getOwnerPlayer();
        if (owner != null) {
            owner.startRiding(companion, true);
        }
        companion.setCurrentState(CompanionState.NAVIGATING);
    }

    @Override
    public void tick() {
        if (paused) return;

        Player owner = companion.getOwnerPlayer();
        if (owner == null || !owner.isPassenger()) {
            complete = true;
            companion.setCurrentState(CompanionState.IDLE);
            return;
        }

        double distSq = companion.blockPosition().distSqr(targetPos);
        if (distSq <= 4.0 || companion.getNavManager().isDone()) {
            complete = true;
            owner.stopRiding();
            companion.getNavManager().stop();
            companion.setCurrentState(CompanionState.IDLE);
        } else {
            companion.getNavManager().moveTo(targetPos, 1.25);
        }
    }

    @Override
    public String getDescription() { return "Transportar a " + targetPos.toShortString(); }
    @Override
    public String getProgress() { return (int)Math.sqrt(companion.blockPosition().distSqr(targetPos)) + "m"; }
    @Override
    public String getTaskType() { return "CARRY_PLAYER"; }
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (targetPos != null) tag.put("TargetPos", NbtUtils.writeBlockPos(targetPos));
        return tag;
    }
    @Override
    public void load(CompoundTag tag) {
        if (tag.contains("TargetPos")) this.targetPos = NbtUtils.readBlockPos(tag.getCompound("TargetPos"));
    }
}
