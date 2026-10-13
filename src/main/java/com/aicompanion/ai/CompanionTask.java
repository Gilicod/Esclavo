package com.aicompanion.ai;

import com.aicompanion.entity.CompanionEntity;
import net.minecraft.nbt.CompoundTag;

public abstract class CompanionTask {
    protected final CompanionEntity companion;
    protected boolean complete = false;
    protected boolean paused = false;
    protected boolean failed = false;

    protected String missionId = "";
    protected String stage = "INITIALIZING";
    protected String substate = "";
    protected String failureReason = "";
    protected long lastProgressTick = 0;
    protected int retryCount = 0;

    public CompanionTask(CompanionEntity companion) {
        this.companion = companion;
        this.lastProgressTick = companion != null ? companion.tickCount : 0;
    }

    public void setMissionId(String id) {
        this.missionId = id != null ? id : "";
    }

    public String getMissionId() {
        if (missionId == null || missionId.isEmpty()) {
            if (companion != null && companion.getCompanionBrain() != null) {
                String brainMission = companion.getCompanionBrain().getActiveMissionId();
                if (brainMission != null && !brainMission.isEmpty()) {
                    this.missionId = brainMission;
                    return this.missionId;
                }
            }
            this.missionId = "MIS-" + Integer.toHexString(this.hashCode());
        }
        return missionId;
    }

    public void setStage(String stage) {
        this.stage = stage != null ? stage : "";
    }

    public void setSubstate(String substate) {
        this.substate = substate != null ? substate : "";
    }

    public void logEvent(String event) {
        String mId = getMissionId();
        String npcName = companion != null ? companion.getCustomCompanionName() : "Unknown";
        int npcId = companion != null ? companion.getId() : -1;
        com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO] [MISION {}] [NPC {} #{}] {}", mId, npcName, npcId, event);
    }

    public abstract void start();
    public abstract void tick();
    public void stop() {}
    public void pause() { this.paused = true; }
    public void resume() { this.paused = false; }
    public void cancel() { this.complete = true; }

    /**
     * Called when this active task is interrupted by an emergency survival flee.
     * Allows civil tasks to immediately discard/blacklist dangerous targets and avoid infinite flee loops.
     */
    public void onEmergencyFlee(com.aicompanion.ai.tasks.FleeTask fleeTask) {}

    /**
     * GOAP Precondition Hook.
     * Evaluates required tools, inventory space, or materials before this task can execute.
     *
     * @return Ordered list of prerequisite tasks to push onto the LIFO execution stack,
     *         or empty list if preconditions are satisfied.
     */
    public java.util.List<CompanionTask> checkPreconditions() {
        return java.util.Collections.emptyList();
    }

    public void fail(String reason) {
        this.failed = true;
        this.complete = true;
        this.failureReason = reason;
        if (companion != null && companion.getOwnerPlayer() != null) {
            companion.getOwnerPlayer().sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("[" + companion.getCustomCompanionName() + "] ")
                            .withStyle(net.minecraft.ChatFormatting.RED)
                            .append(net.minecraft.network.chat.Component.literal("Misión interrumpida: " + reason).withStyle(net.minecraft.ChatFormatting.WHITE))
            );
        }
    }

    public void markProgress() {
        this.lastProgressTick = companion != null ? companion.tickCount : 0;
    }

    public boolean isComplete() { return complete; }
    public boolean isPaused() { return paused; }
    public boolean isFailed() { return failed; }

    public String getStage() { return stage; }
    public String getSubstate() { return substate; }
    public String getFailureReason() { return failureReason; }
    public long getLastProgressTick() { return lastProgressTick; }
    public int getRetryCount() { return retryCount; }

    public boolean isStationaryWorking() {
        if ("MINING".equalsIgnoreCase(stage) || "CHOPPING".equalsIgnoreCase(stage)
                || "BUILDING".equalsIgnoreCase(stage) || "SMELTING".equalsIgnoreCase(stage)
                || "CRAFTING".equalsIgnoreCase(stage)) {
            return true;
        }
        if (companion != null) {
            var st = companion.getCurrentState();
            if (st == com.aicompanion.entity.CompanionState.SMELTING
                    || st == com.aicompanion.entity.CompanionState.CRAFTING) {
                return true;
            }
            if (st == com.aicompanion.entity.CompanionState.BUILDING
                    && !"NAVIGATING".equalsIgnoreCase(stage)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Called by StuckDetector when movement towards the current target has stalled repeatedly.
     * Allows tasks to blacklist unreachable targets, reset pathing, or pick alternative candidates.
     */
    public void onStuckTargetUnreachable() {}

    public abstract String getDescription();
    public abstract String getProgress();
    public abstract CompoundTag save();
    public abstract void load(CompoundTag tag);
    public abstract String getTaskType();
}
