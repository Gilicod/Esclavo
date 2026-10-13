package com.aicompanion.ai.tasks;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

public class WithdrawTask extends CompanionTask {
    private BlockPos chestPos;
    private ResourceLocation itemId;
    private int amount;
    private int repathCooldown = 0;

    public WithdrawTask(CompanionEntity companion, BlockPos chestPos, ResourceLocation itemId, int amount) {
        super(companion);
        this.chestPos = chestPos != null ? chestPos : companion.getHomePos();
        this.itemId = itemId;
        this.amount = amount;
    }

    public WithdrawTask(CompanionEntity companion, ResourceLocation itemId, int amount) {
        this(companion, companion.getHomePos(), itemId, amount);
    }

    @Override
    public void start() {
        companion.setCurrentState(CompanionState.WITHDRAWING);
    }

    @Override
    public void tick() {
        if (paused) return;

        if (chestPos == null) {
            var nearest = companion.getNearestAuthorizedChest(companion.blockPosition(), companion.level(), 48.0);
            if (nearest.isPresent()) {
                chestPos = nearest.get();
            } else {
                companion.tellOwner("No tengo ningún cofre autorizado en esta dimensión para retirar objetos. Usa el Silbato en un cofre para autorizarlo.");
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
            companion.setCurrentState(CompanionState.WITHDRAWING);
            BlockEntity be = companion.level().getBlockEntity(chestPos);
            if (be != null) {
                Item targetItem = BuiltInRegistries.ITEM.get(itemId);
                be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(targetHandler -> {
                    int remainingToTake = amount;
                    for (int j = 0; j < targetHandler.getSlots(); j++) {
                        ItemStack inChest = targetHandler.getStackInSlot(j);
                        if (inChest.getItem() == targetItem && !inChest.isEmpty()) {
                            int toTake = Math.min(remainingToTake, inChest.getCount());
                            // 1. Simulate extraction from chest
                            ItemStack simExtract = targetHandler.extractItem(j, toTake, true);
                            if (simExtract.isEmpty()) continue;
                            // 2. Simulate insertion into companion
                            ItemStack simRemainder = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(companion.getInventoryHandler(), simExtract, true);
                            int accepted = simExtract.getCount() - simRemainder.getCount();
                            if (accepted > 0) {
                                ItemStack extracted = targetHandler.extractItem(j, accepted, false);
                                if (!extracted.isEmpty()) {
                                    ItemStack actualRemainder = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(companion.getInventoryHandler(), extracted, false);
                                    if (!actualRemainder.isEmpty()) {
                                        targetHandler.insertItem(j, actualRemainder, false); // rollback to chest
                                    }
                                    remainingToTake -= (accepted - actualRemainder.getCount());
                                }
                            }
                            if (remainingToTake <= 0) break;
                        }
                    }
                });
                companion.tellOwner("He terminado de retirar los objetos.");
            } else {
                companion.tellOwner("El contenedor en " + chestPos.toShortString() + " ya no existe.");
            }
            complete = true;
        }
    }

    @Override
    public String getDescription() { return "Retirar " + itemId.getPath() + " (" + amount + ")"; }
    @Override
    public String getProgress() { return "Retirando"; }
    @Override
    public String getTaskType() { return "WITHDRAW"; }
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (chestPos != null) tag.put("ChestPos", NbtUtils.writeBlockPos(chestPos));
        tag.putString("ItemId", itemId.toString());
        tag.putInt("Amount", amount);
        return tag;
    }
    @Override
    public void load(CompoundTag tag) {
        if (tag.contains("ChestPos")) this.chestPos = NbtUtils.readBlockPos(tag.getCompound("ChestPos"));
        this.itemId = new ResourceLocation(tag.getString("ItemId"));
        this.amount = tag.getInt("Amount");
    }
}
