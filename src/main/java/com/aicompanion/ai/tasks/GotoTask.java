package com.aicompanion.ai.tasks;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import com.aicompanion.internal.baritone.goals.GoalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.levelgen.Heightmap;

public class GotoTask extends CompanionTask {
    private BlockPos targetPos;
    private int ticksWithoutMovement = 0;
    private BlockPos lastPos;

    public GotoTask(CompanionEntity companion, BlockPos targetPos) {
        super(companion);
        this.targetPos = targetPos;
        this.stage = "NAVIGATING";
        this.substate = "Desplazándose a " + targetPos.toShortString();
        this.lastPos = companion != null ? companion.blockPosition() : BlockPos.ZERO;
    }

    @Override
    public void start() {
        if (targetPos.getY() <= 0) {
            int surfaceY = companion.level().getHeight(Heightmap.Types.MOTION_BLOCKING, targetPos.getX(), targetPos.getZ());
            targetPos = new BlockPos(targetPos.getX(), surfaceY, targetPos.getZ());
        }
        companion.setCurrentState(CompanionState.NAVIGATING);
        companion.getNavManager().navigateTo(new GoalBlock(targetPos), 1.25);
        markProgress();
    }

    @Override
    public void tick() {
        if (paused) return;
        companion.setCurrentState(CompanionState.NAVIGATING);
        double distSq = companion.blockPosition().distSqr(targetPos);

        if (distSq <= 4.0 || companion.getNavManager().isDone()) {
            complete = true;
            stage = "COMPLETE";
            substate = "Llegada al destino";
            companion.getNavManager().stop();
            companion.setCurrentState(CompanionState.IDLE);
            return;
        }

        // Check if stuck without moving
        if (companion.blockPosition().distSqr(lastPos) < 1.0) {
            ticksWithoutMovement++;
            if (ticksWithoutMovement > 200) { // 10s without progress
                fail("No se pudo alcanzar la posición " + targetPos.toShortString() + " (destino bloqueado o inaccesible).");
                companion.getNavManager().stop();
                return;
            }
        } else {
            ticksWithoutMovement = 0;
            lastPos = companion.blockPosition();
            markProgress();
        }
    }

    @Override
    public void stop() {
        companion.getNavManager().stop();
    }

    @Override
    public String getDescription() { return "Ir a " + targetPos.toShortString(); }
    @Override
    public String getProgress() { return (int) Math.sqrt(companion.blockPosition().distSqr(targetPos)) + "m restantes (" + stage + ")"; }
    @Override
    public String getTaskType() { return "GOTO"; }
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
