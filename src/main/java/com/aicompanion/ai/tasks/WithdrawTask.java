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
        this.chestPos = (chestPos != null && companion != null && companion.isChestAuthorized(chestPos, companion.level())) ? chestPos : null;
        this.itemId = itemId;
        this.amount = amount;
    }

    public WithdrawTask(CompanionEntity companion, ResourceLocation itemId, int amount) {
        this(companion, null, itemId, amount);
    }

    private BlockPos findAuthorizedChestWithItem() {
        if (companion == null || companion.level() == null) return null;
        Item targetItem = itemId != null ? BuiltInRegistries.ITEM.get(itemId) : null;
        ResourceLocation dim = companion.level().dimension().location();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (var entry : companion.getAuthorizedChestEntries()) {
            if (!entry.getDimension().equals(dim)) continue;
            BlockEntity be = companion.level().getBlockEntity(entry.getPos());
            if (be == null) continue;
            var cap = be.getCapability(ForgeCapabilities.ITEM_HANDLER);
            if (!cap.isPresent()) continue;
            var handler = cap.orElse(null);
            if (handler == null) continue;
            boolean hasItem = (targetItem == null);
            if (targetItem != null) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack s = handler.getStackInSlot(i);
                    if (!s.isEmpty() && s.is(targetItem)) {
                        hasItem = true;
                        break;
                    }
                }
            }
            if (hasItem) {
                double d = companion.blockPosition().distSqr(entry.getPos());
                if (d < bestDist) {
                    bestDist = d;
                    best = entry.getPos();
                }
            }
        }
        if (best != null) return best;
        return companion.getNearestAuthorizedChest(companion.blockPosition(), companion.level(), 64.0).orElse(null);
    }

    @Override
    public void start() {
        companion.setCurrentState(CompanionState.WITHDRAWING);
        if (chestPos == null || !companion.isChestAuthorized(chestPos, companion.level())) {
            chestPos = findAuthorizedChestWithItem();
        }
    }

    @Override
    public void tick() {
        if (paused) return;

        if (chestPos == null || !companion.isChestAuthorized(chestPos, companion.level())) {
            chestPos = findAuthorizedChestWithItem();
            if (chestPos == null) {
                companion.tellOwner("No tengo ningún baúl autorizado cercano con " + (itemId != null ? itemId.getPath() : "esos objetos") + ".");
                complete = true;
                return;
            }
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
