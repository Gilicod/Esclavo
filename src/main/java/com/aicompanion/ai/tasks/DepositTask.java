package com.aicompanion.ai.tasks;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

public class DepositTask extends CompanionTask {
    private BlockPos chestPos;
    private int repathCooldown = 0;

    public DepositTask(CompanionEntity companion, BlockPos chestPos) {
        super(companion);
        this.chestPos = chestPos != null ? chestPos : companion.getHomePos();
    }

    public DepositTask(CompanionEntity companion) {
        this(companion, companion.getHomePos());
    }

    @Override
    public void start() {
        companion.setCurrentState(CompanionState.DEPOSITING);
    }

    @Override
    public void tick() {
        if (paused) return;

        if (chestPos == null) {
            var nearest = companion.getNearestAuthorizedChest(companion.blockPosition(), companion.level(), 48.0);
            if (nearest.isPresent()) {
                chestPos = nearest.get();
            } else {
                companion.tellOwner("No tengo ningún cofre autorizado en esta dimensión para depositar. Usa el Silbato en un cofre para autorizarlo.");
                complete = true;
                return;
            }
        }

        if (!companion.isChestAuthorized(chestPos, companion.level())) {
            companion.tellOwner("El cofre en " + chestPos.toShortString() + " no está autorizado.");
            complete = true;
            return;
        }

        double distSq = companion.blockPosition().distSqr(chestPos);
        if (distSq > 9.0) {
            companion.setCurrentState(CompanionState.NAVIGATING);
            if (--repathCooldown <= 0) {
                repathCooldown = 15;
                companion.getNavManager().moveTo(chestPos, 1.2);
            }
        } else {
            companion.getNavManager().stop();
            companion.setCurrentState(CompanionState.DEPOSITING);
            BlockEntity be = companion.level().getBlockEntity(chestPos);
            if (be != null) {
                be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(targetHandler -> {
                    for (int i = 0; i < 36; i++) {
                        ItemStack companionStack = companion.getInventoryHandler().getStackInSlot(i);
                        if (companionStack.isEmpty()) continue;
                        // Protect tools, weapons, armor, shields, active mission items, and a basic food reserve (<= 16)
                        if (com.aicompanion.inventory.ItemHeuristicValue.isMissionProtected(companionStack, companion)) continue;
                        var item = companionStack.getItem();
                        if (item instanceof net.minecraft.world.item.TieredItem
                                || item instanceof net.minecraft.world.item.ArmorItem
                                || item instanceof net.minecraft.world.item.ShieldItem
                                || item instanceof net.minecraft.world.item.BowItem
                                || item instanceof net.minecraft.world.item.CrossbowItem
                                || item instanceof net.minecraft.world.item.ShearsItem) {
                            continue;
                        }
                        if (companionStack.isEdible() && companion.getInventoryHandler().countItem(item) <= 16) {
                            continue;
                        }
                        ItemStack simExtract = companion.getInventoryHandler().extractItem(i, companionStack.getCount(), true);
                        if (simExtract.isEmpty()) continue;
                        ItemStack simRemainder = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(targetHandler, simExtract, true);
                        int accepted = simExtract.getCount() - simRemainder.getCount();
                        if (accepted > 0) {
                            ItemStack extracted = companion.getInventoryHandler().extractItem(i, accepted, false);
                            if (!extracted.isEmpty()) {
                                ItemStack actualRemainder = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(targetHandler, extracted, false);
                                if (!actualRemainder.isEmpty()) {
                                    companion.getInventoryHandler().insertItem(i, actualRemainder, false);
                                }
                            }
                        }
                    }
                });
                companion.tellOwner("He terminado de depositar los objetos.");
            } else {
                companion.tellOwner("El contenedor en " + chestPos.toShortString() + " ya no se encuentra en el mundo.");
            }
            complete = true;
        }
    }

    @Override
    public String getDescription() { return "Depositar objetos en cofre"; }
    @Override
    public String getProgress() { return "Depositando"; }
    @Override
    public String getTaskType() { return "DEPOSIT"; }
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (chestPos != null) tag.put("ChestPos", NbtUtils.writeBlockPos(chestPos));
        return tag;
    }
    @Override
    public void load(CompoundTag tag) {
        if (tag.contains("ChestPos")) this.chestPos = NbtUtils.readBlockPos(tag.getCompound("ChestPos"));
    }
}
