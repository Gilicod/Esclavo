package com.aicompanion.ai.tasks;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionSettings;
import com.aicompanion.entity.CompanionState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DepositTask extends CompanionTask {
    private BlockPos chestPos;
    private int explicitChestId = -1;
    private int repathCooldown = 0;
    private final Set<BlockPos> visitedChests = new HashSet<>();
    private int totalItemsDeposited = 0;
    private String lastTargetLabel = "Baúl autorizado";

    public DepositTask(CompanionEntity companion, BlockPos chestPos) {
        super(companion);
        // Only lock onto chestPos if it is actually an authorized chest; never lock onto floor homePos!
        if (chestPos != null && companion != null && companion.isChestAuthorized(chestPos, companion.level())) {
            this.chestPos = chestPos;
            var entry = companion.getAuthorizedChestAt(chestPos, companion.level());
            if (entry != null) {
                this.lastTargetLabel = entry.getFullLabel();
            }
        } else {
            this.chestPos = null;
        }
    }

    public DepositTask(CompanionEntity companion, int explicitChestId) {
        super(companion);
        this.explicitChestId = explicitChestId;
        if (companion != null && explicitChestId > 0) {
            var entry = companion.getAuthorizedChestById(explicitChestId);
            if (entry != null) {
                this.chestPos = entry.getPos();
                this.lastTargetLabel = entry.getFullLabel();
            }
        }
    }

    public DepositTask(CompanionEntity companion) {
        this(companion, (BlockPos) null);
    }

    public static boolean isDepositableSlot(CompanionEntity companion, int slot, int foodReserveRemaining) {
        if (companion == null) return false;
        ItemStack stack = companion.getInventoryHandler().getStackInSlot(slot);
        if (stack.isEmpty()) return false;
        if (com.aicompanion.inventory.ItemHeuristicValue.isMissionProtected(stack, companion)) return false;
        var item = stack.getItem();
        if (item instanceof net.minecraft.world.item.TieredItem
                || item instanceof net.minecraft.world.item.ArmorItem
                || item instanceof net.minecraft.world.item.ShieldItem
                || item instanceof net.minecraft.world.item.BowItem
                || item instanceof net.minecraft.world.item.CrossbowItem
                || item instanceof net.minecraft.world.item.ShearsItem) {
            return false;
        }
        if (stack.is(net.minecraft.world.item.Items.TORCH) && companion.getInventoryHandler().countItem(net.minecraft.world.item.Items.TORCH) <= 16) {
            return false;
        }
        if (CompanionEntity.ChestCategory.isHealthyFood(stack)) {
            return foodReserveRemaining <= 0;
        }
        return true;
    }

    public String getTargetChestLabel() {
        if (companion != null && chestPos != null) {
            var entry = companion.getAuthorizedChestAt(chestPos, companion.level());
            if (entry != null) {
                return "[#" + entry.getChestId() + "] " + entry.getCustomName() + " (" + entry.getCategory().getDisplayName() + ")";
            }
            return "Baúl (" + chestPos.toShortString() + ")";
        }
        return lastTargetLabel;
    }

    public String getItemsToDepositSummary() {
        if (companion == null) return "Nada pendiente";
        int foodReserve = companion.getSettings() != null
                ? companion.getSettings().getFoodStoragePolicy().getReserveLimit()
                : 16;
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack s = companion.getInventoryHandler().getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (CompanionEntity.ChestCategory.isHealthyFood(s)) {
                if (foodReserve >= s.getCount()) {
                    foodReserve -= s.getCount();
                    continue;
                }
                int excess = s.getCount() - Math.max(0, foodReserve);
                foodReserve = 0;
                if (excess > 0 && parts.size() < 4) {
                    parts.add(excess + "x " + s.getHoverName().getString());
                }
                continue;
            }
            if (isDepositableSlot(companion, i, 0)) {
                if (parts.size() < 4) {
                    parts.add(s.getCount() + "x " + s.getHoverName().getString());
                }
            }
        }
        return parts.isEmpty() ? "Excedentes de mochila" : String.join(", ", parts);
    }

    private BlockPos resolveNextTargetChest() {
        if (companion == null || companion.level() == null) return null;
        if (explicitChestId > 0) {
            var explicit = companion.getAuthorizedChestById(explicitChestId);
            if (explicit != null && explicit.getDimension().equals(companion.level().dimension().location())) {
                this.lastTargetLabel = explicit.getFullLabel();
                return explicit.getPos();
            }
        }

        int foodReserve = companion.getSettings() != null
                ? companion.getSettings().getFoodStoragePolicy().getReserveLimit()
                : 16;

        // Find the first depositable stack in inventory and pick the best matching chest not yet visited
        for (int i = 0; i < 36; i++) {
            ItemStack s = companion.getInventoryHandler().getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (CompanionEntity.ChestCategory.isHealthyFood(s)) {
                if (foodReserve >= s.getCount()) {
                    foodReserve -= s.getCount();
                    continue;
                }
                foodReserve = 0;
            } else if (!isDepositableSlot(companion, i, 0)) {
                continue;
            }

            var bestOpt = companion.getBestAuthorizedChestForItem(companion.blockPosition(), companion.level(), s, 64.0);
            if (bestOpt.isPresent() && !visitedChests.contains(bestOpt.get().getPos())) {
                this.lastTargetLabel = bestOpt.get().getFullLabel();
                return bestOpt.get().getPos();
            }
        }

        // Fallback to any nearest unvisited authorized chest
        var nearest = companion.getNearestAuthorizedChest(companion.blockPosition(), companion.level(), 64.0);
        if (nearest.isPresent() && !visitedChests.contains(nearest.get())) {
            var entry = companion.getAuthorizedChestAt(nearest.get(), companion.level());
            if (entry != null) this.lastTargetLabel = entry.getFullLabel();
            return nearest.get();
        }
        return null;
    }

    @Override
    public void start() {
        companion.setCurrentState(CompanionState.DEPOSITING);
        if (chestPos == null || !companion.isChestAuthorized(chestPos, companion.level())) {
            chestPos = resolveNextTargetChest();
        }
    }

    @Override
    public void tick() {
        if (paused) return;

        if (chestPos == null || !companion.isChestAuthorized(chestPos, companion.level())) {
            chestPos = resolveNextTargetChest();
            if (chestPos == null) {
                if (totalItemsDeposited > 0) {
                    companion.tellOwner("He guardado " + totalItemsDeposited + " objetos en los baúles autorizados.");
                } else if (companion.getAuthorizedChestEntries().isEmpty()) {
                    companion.tellOwner("No tengo ningún baúl autorizado. Usa el Silbato sobre un cofre o la pestaña BAÚLES para autorizarlo.");
                } else {
                    companion.tellOwner("No hay objetos excedentes para guardar o los baúles cercanos están llenos.");
                }
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
            companion.setCurrentState(CompanionState.DEPOSITING);
            visitedChests.add(chestPos);
            var targetEntry = companion.getAuthorizedChestAt(chestPos, companion.level());
            CompanionEntity.ChestCategory chestCat = targetEntry != null ? targetEntry.getCategory() : CompanionEntity.ChestCategory.ALL;

            // Check if there are other authorized chests with different categories so we respect category specialization
            boolean hasMultipleCategorizedChests = companion.getAuthorizedChestEntries().size() > 1 && explicitChestId <= 0;

            BlockEntity be = companion.level().getBlockEntity(chestPos);
            if (be != null) {
                int foodReserve = companion.getSettings() != null
                        ? companion.getSettings().getFoodStoragePolicy().getReserveLimit()
                        : 16;
                final int[] foodLeft = new int[]{foodReserve};
                final int[] depositedHere = new int[]{0};

                be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(targetHandler -> {
                    for (int i = 0; i < 36; i++) {
                        ItemStack companionStack = companion.getInventoryHandler().getStackInSlot(i);
                        if (companionStack.isEmpty()) continue;

                        int maxToExtract = companionStack.getCount();
                        if (CompanionEntity.ChestCategory.isHealthyFood(companionStack)) {
                            if (foodLeft[0] >= companionStack.getCount()) {
                                foodLeft[0] -= companionStack.getCount();
                                continue;
                            }
                            maxToExtract = companionStack.getCount() - Math.max(0, foodLeft[0]);
                            foodLeft[0] = 0;
                        } else if (!isDepositableSlot(companion, i, 0)) {
                            continue;
                        }

                        if (maxToExtract <= 0) continue;

                        // If this chest has a specific category (e.g. FOOD or ORES) and the player didn't force this specific chest, only deposit matching items here
                        if (hasMultipleCategorizedChests && chestCat != CompanionEntity.ChestCategory.ALL && !chestCat.matchesItem(companionStack)) {
                            continue;
                        }
                        // Conversely, if this chest is ALL (General), but there is another unvisited chest specifically dedicated to this item's category, save it for that specialized chest!
                        if (hasMultipleCategorizedChests && chestCat == CompanionEntity.ChestCategory.ALL) {
                            var betterOpt = companion.getBestAuthorizedChestForItem(companion.blockPosition(), companion.level(), companionStack, 48.0);
                            if (betterOpt.isPresent() && !betterOpt.get().getPos().equals(chestPos)
                                    && betterOpt.get().getCategory() != CompanionEntity.ChestCategory.ALL
                                    && !visitedChests.contains(betterOpt.get().getPos())) {
                                continue;
                            }
                        }

                        ItemStack simExtract = companion.getInventoryHandler().extractItem(i, maxToExtract, true);
                        if (simExtract.isEmpty()) continue;
                        ItemStack simRemainder = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(targetHandler, simExtract, true);
                        int accepted = simExtract.getCount() - simRemainder.getCount();
                        if (accepted > 0) {
                            ItemStack extracted = companion.getInventoryHandler().extractItem(i, accepted, false);
                            if (!extracted.isEmpty()) {
                                ItemStack actualRemainder = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(targetHandler, extracted, false);
                                int storedCount = extracted.getCount() - actualRemainder.getCount();
                                depositedHere[0] += storedCount;
                                totalItemsDeposited += storedCount;
                                if (!actualRemainder.isEmpty()) {
                                    companion.getInventoryHandler().insertItem(i, actualRemainder, false);
                                }
                            }
                        }
                    }
                });

                String label = getTargetChestLabel();
                if (depositedHere[0] > 0) {
                    logEvent("Guardados " + depositedHere[0] + " objetos en " + label);
                }

                // If not locked to a single explicit chest, check if remaining items should go to another categorized chest
                if (explicitChestId <= 0 && visitedChests.size() < 4) {
                    BlockPos nextChest = resolveNextTargetChest();
                    if (nextChest != null && !visitedChests.contains(nextChest)) {
                        chestPos = nextChest;
                        repathCooldown = 0;
                        return;
                    }
                }

                companion.tellOwner("He guardado " + totalItemsDeposited + " objetos en " + label + ".");
                companion.syncToOwner();
            } else {
                companion.tellOwner("El contenedor en " + chestPos.toShortString() + " ya no se encuentra en el mundo.");
            }
            complete = true;
        }
    }

    @Override
    public String getDescription() {
        return "Guardar en " + getTargetChestLabel();
    }

    @Override
    public String getProgress() {
        if (chestPos != null && companion != null) {
            int dist = (int) Math.sqrt(companion.blockPosition().distSqr(chestPos));
            if (dist > 3) {
                return "Yendo a " + getTargetChestLabel() + " (" + dist + "m) | " + getItemsToDepositSummary();
            }
        }
        return "Depositando: " + getItemsToDepositSummary();
    }

    @Override
    public String getTaskType() { return "DEPOSIT"; }

    @Override
    public String getStage() { return getTargetChestLabel(); }

    @Override
    public String getSubstate() { return getItemsToDepositSummary(); }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (chestPos != null) tag.put("ChestPos", NbtUtils.writeBlockPos(chestPos));
        tag.putInt("ExplicitChestId", explicitChestId);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        if (tag.contains("ChestPos")) this.chestPos = NbtUtils.readBlockPos(tag.getCompound("ChestPos"));
        if (tag.contains("ExplicitChestId")) this.explicitChestId = tag.getInt("ExplicitChestId");
    }
}
