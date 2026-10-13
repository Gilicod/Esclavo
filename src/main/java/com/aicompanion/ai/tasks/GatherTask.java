package com.aicompanion.ai.tasks;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.ai.gathering.GatherStrategyRegistry;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import com.aicompanion.entity.DefaultBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class GatherTask extends CompanionTask {
    private ResourceLocation itemId;
    private int amountNeeded;
    private int amountGathered = 0;
    private CompanionTask subTask = null;
    private int repathCooldown = 0;

    public GatherTask(CompanionEntity companion, ResourceLocation itemId, int amountNeeded) {
        super(companion);
        this.itemId = itemId;
        this.amountNeeded = amountNeeded;
    }

    @Override
    public void start() {
        companion.setCurrentState(CompanionState.GATHERING);
        this.repathCooldown = 0;
        GatherStrategyRegistry.StrategyType strategy = GatherStrategyRegistry.determineStrategy(itemId);

        if (strategy == GatherStrategyRegistry.StrategyType.TREE) {
            String wood = itemId.getPath().replace("_log", "").replace("_wood", "");
            subTask = new ChopTask(companion, wood, amountNeeded);
            subTask.start();
        } else if (strategy == GatherStrategyRegistry.StrategyType.BLOCK_MINE) {
            ResourceLocation blockId = new ResourceLocation(itemId.getNamespace(), itemId.getPath().contains("ore") ? itemId.getPath() : itemId.getPath() + "_ore");
            if (BuiltInRegistries.BLOCK.get(blockId) == net.minecraft.world.level.block.Blocks.AIR) {
                blockId = itemId;
            }
            subTask = new MineTask(companion, blockId, amountNeeded);
            subTask.start();
        }
    }

    @Override
    public void tick() {
        if (paused || complete) return;

        Item target = BuiltInRegistries.ITEM.get(itemId);
        amountGathered = companion.getInventoryHandler().countItem(target);

        if (amountGathered >= amountNeeded) {
            complete = true;
            companion.addGatheringXP(amountNeeded * 3);
            companion.incrementTasksCompleted();
            companion.setCurrentState(CompanionState.IDLE);
            companion.getNavManager().stop();
            return;
        }

        if (subTask != null) {
            if (!subTask.isComplete()) {
                subTask.tick();
                return;
            } else if (subTask.isFailed()) {
                fail(subTask.getFailureReason());
                return;
            }
        }

        // Default Ground Item collection
        AABB area = companion.getBoundingBox().inflate(24.0);
        List<ItemEntity> items = companion.level().getEntitiesOfClass(ItemEntity.class, area,
                e -> e.isAlive() && e.getItem().getItem() == target
                        && (companion.getCompanionBrain() == null || !companion.getCompanionBrain().isDiscardedItem(e)));
        items.sort(java.util.Comparator.comparingDouble(companion::distanceToSqr));

        if (!items.isEmpty()) {
            ItemEntity closest = items.get(0);
            companion.setCurrentState(CompanionState.NAVIGATING);
            if (--repathCooldown <= 0 || companion.getNavManager().isDone()) {
                repathCooldown = 12;
                companion.getNavManager().moveTo(closest, 1.25);
            }
            if (companion.distanceToSqr(closest) < 4.0) {
                ItemStack rem = companion.getInventoryHandler().addItem(closest.getItem());
                if (!rem.isEmpty()) {
                    companion.getInventoryHandler().freeSpaceForMission(companion, s -> s.is(target));
                    rem = companion.getInventoryHandler().addItem(rem);
                }
                if (rem.isEmpty()) closest.discard();
                else closest.setItem(rem);
                companion.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.5f, 1.2f);
                markProgress();
            }
        } else {
            companion.setCurrentState(CompanionState.SEARCHING);
            if (companion.getNavManager().isDone()) {
                companion.getNavManager().navigateTo(new com.aicompanion.internal.baritone.goals.GoalExploreRegion(companion.blockPosition(), 24), com.aicompanion.ai.nav.MovementPolicy.sprint());
            }
        }
    }

    @Override
    public void stop() {
        if (companion != null) {
            companion.setSprinting(false);
            companion.getNavManager().stop();
        }
    }

    @Override
    public String getDescription() { return "Recolectar " + itemId.getPath() + " (" + amountNeeded + ")"; }
    @Override
    public String getProgress() { return amountGathered + " / " + amountNeeded; }
    @Override
    public String getTaskType() { return "GATHER"; }
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("ItemId", itemId.toString());
        tag.putInt("AmountNeeded", amountNeeded);
        tag.putInt("AmountGathered", amountGathered);
        return tag;
    }
    @Override
    public void load(CompoundTag tag) {
        this.itemId = new ResourceLocation(tag.getString("ItemId"));
        this.amountNeeded = tag.getInt("AmountNeeded");
        this.amountGathered = tag.getInt("AmountGathered");
    }
}
