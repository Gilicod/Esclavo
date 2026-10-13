package com.aicompanion.ai.tasks;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class BringEntityTask extends CompanionTask {
    private ResourceLocation entityTypeId;
    private PathfinderMob targetMob = null;

    public BringEntityTask(CompanionEntity companion, ResourceLocation entityTypeId) {
        super(companion);
        this.entityTypeId = entityTypeId;
    }

    @Override
    public void start() {
        companion.setCurrentState(CompanionState.SEARCHING);
    }

    @Override
    public void tick() {
        if (paused) return;

        Player owner = companion.getOwnerPlayer();
        if (owner == null) {
            complete = true;
            return;
        }

        if (targetMob == null || !targetMob.isAlive()) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityTypeId);
            AABB area = companion.getBoundingBox().inflate(32.0);
            List<PathfinderMob> list = companion.level().getEntitiesOfClass(PathfinderMob.class, area, e -> e.getType() == type && e.isAlive());
            if (!list.isEmpty()) {
                targetMob = list.get(0);
            } else {
                companion.setCurrentState(CompanionState.SEARCHING);
                return;
            }
        }

        // Guide mob towards owner
        double distToOwner = companion.distanceToSqr(owner);
        if (distToOwner < 9.0) {
            complete = true;
            companion.getNavManager().stop();
            companion.setCurrentState(CompanionState.IDLE);
        } else {
            companion.setCurrentState(CompanionState.NAVIGATING);
            companion.getNavManager().moveTo(owner, 1.1);
            targetMob.getNavigation().moveTo(companion, 1.1);
        }
    }

    @Override
    public String getDescription() { return "Traer " + entityTypeId.getPath(); }
    @Override
    public String getProgress() { return complete ? "Entregado" : "Guiando"; }
    @Override
    public String getTaskType() { return "BRING_ENTITY"; }
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("EntityType", entityTypeId.toString());
        return tag;
    }
    @Override
    public void load(CompoundTag tag) {
        this.entityTypeId = new ResourceLocation(tag.getString("EntityType"));
    }
}
