package com.aicompanion.ai.tasks;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public class TaskSerializerRegistry {

    public static CompoundTag serialize(CompanionTask task) {
        if (task == null) return new CompoundTag();
        CompoundTag tag = task.save();
        tag.putString("TaskTypeIdentifier", task.getTaskType());
        tag.putString("MissionId", task.getMissionId());
        tag.putBoolean("TaskPaused", task.isPaused());
        tag.putString("TaskStage", task.getStage() != null ? task.getStage() : "");
        tag.putString("TaskSubstate", task.getSubstate() != null ? task.getSubstate() : "");
        return tag;
    }

    public static CompanionTask deserialize(CompanionEntity companion, CompoundTag tag) {
        if (tag == null || !tag.contains("TaskTypeIdentifier")) return null;
        String type = tag.getString("TaskTypeIdentifier");

        CompanionTask task = null;
        switch (type) {
            case "MINE":
                task = new MineTask(companion, new ResourceLocation("minecraft", "iron_ore"), 1);
                break;
            case "CHOP":
                task = new ChopTask(companion, "oak", 1);
                break;
            case "KILL":
                task = new KillTask(companion, new ResourceLocation("minecraft", "zombie"), 1);
                break;
            case "BUILD":
                task = new BuildTask(companion, "small_house", companion.blockPosition());
                break;
            case "GATHER":
                task = new GatherTask(companion, new ResourceLocation("minecraft", "oak_log"), 1);
                break;
            case "FOLLOW":
                task = new FollowTask(companion);
                break;
            case "STAY":
                task = new StayTask(companion);
                break;
            case "GOTO":
                task = new GotoTask(companion, companion.blockPosition());
                break;
            case "RETURN":
                task = new ReturnTask(companion, false);
                break;
            case "DEPOSIT":
                task = new DepositTask(companion);
                break;
            case "WITHDRAW":
                task = new WithdrawTask(companion, new ResourceLocation("minecraft", "cobblestone"), 1);
                break;
            case "CRAFT":
                task = new CraftTask(companion, new ResourceLocation("minecraft", "iron_pickaxe"), 1);
                break;
            case "SMELT":
                task = new SmeltTask(companion, new ResourceLocation("minecraft", "iron_ingot"), 1);
                break;
            case "FIGHT":
                task = new FightTask(companion, null);
                break;
            case "FLEE":
                task = new FleeTask(companion, null, 16.0);
                break;
            case "HEAL":
                task = new HealTask(companion);
                break;
            case "FARM":
                task = new FarmTask(companion);
                break;
            case "GUARD":
                task = new GuardTask(companion);
                break;
            case "MAINTAIN_CHEST":
                task = new MaintainChestTask(companion);
                break;
            case "DIMENSION_TRAVEL":
                task = new DimensionTravelTask(companion, net.minecraft.world.level.Level.NETHER);
                break;
            case "PORTAL_BUILD":
            case "BUILD_NETHER_PORTAL":
                task = new BuildNetherPortalTask(companion);
                break;
        }

        if (task != null) {
            task.load(tag);
            if (tag.contains("MissionId")) {
                String mId = tag.getString("MissionId");
                if (!mId.isEmpty()) {
                    task.setMissionId(mId);
                }
            }
            if (tag.contains("TaskPaused") && tag.getBoolean("TaskPaused")) {
                task.pause();
            }
            if (tag.contains("TaskStage")) {
                String stg = tag.getString("TaskStage");
                if (!stg.isEmpty()) {
                    task.setStage(stg);
                }
            }
            if (tag.contains("TaskSubstate")) {
                String sub = tag.getString("TaskSubstate");
                if (!sub.isEmpty()) {
                    task.setSubstate(sub);
                }
            }
            // Ensure task has a valid non-empty missionId
            if (task.getMissionId() == null || task.getMissionId().isEmpty()) {
                task.setMissionId("MIS-" + Integer.toHexString(task.hashCode()));
            }
        }
        return task;
    }
}
