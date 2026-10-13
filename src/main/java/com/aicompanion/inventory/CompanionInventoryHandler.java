package com.aicompanion.inventory;

import com.aicompanion.AICompanionMod;
import com.aicompanion.entity.CompanionEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class CompanionInventoryHandler extends ItemStackHandler {
    public static final int MAIN_SLOTS = 36;
    public static final int ARMOR_SLOTS = 4;
    public static final int SLOT_ARMOR_START = 36;
    public static final int SLOT_HELMET = 36;
    public static final int SLOT_CHEST = 37;
    public static final int SLOT_LEGS = 38;
    public static final int SLOT_BOOTS = 39;
    public static final int SLOT_MAINHAND = 40;
    public static final int SLOT_OFFHAND = 41;
    public static final int TOTAL_SLOTS = 42;

    public CompanionInventoryHandler() {
        super(TOTAL_SLOTS);
    }

    public CompanionInventoryHandler(int size) {
        super(Math.max(TOTAL_SLOTS, size));
    }

    @Override
    public void setSize(int size) {
        super.setSize(Math.max(TOTAL_SLOTS, size));
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        // Enforce that size never shrinks below TOTAL_SLOTS (42), preventing IndexOutOfBounds
        int savedSize = nbt.contains("Size", Tag.TAG_INT) ? nbt.getInt("Size") : TOTAL_SLOTS;
        setSize(Math.max(TOTAL_SLOTS, savedSize));

        ListTag tagList = nbt.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < tagList.size(); i++) {
            CompoundTag itemTags = tagList.getCompound(i);
            int slot = itemTags.getInt("Slot");
            if (slot >= 0 && slot < this.stacks.size()) {
                this.stacks.set(slot, ItemStack.of(itemTags));
            }
        }
        onLoad();
    }

    private boolean isOrganizing = false;
    private boolean autoOrganizePending = false;
    private int organizeDelayTicks = 0;

    public boolean isOrganizing() {
        return isOrganizing;
    }

    public void setOrganizing(boolean organizing) {
        this.isOrganizing = organizing;
    }

    @Override
    protected void onContentsChanged(int slot) {
        super.onContentsChanged(slot);
        if (isOrganizing) return;
        if (slot < MAIN_SLOTS) {
            this.autoOrganizePending = true;
            this.organizeDelayTicks = 6;
        }
    }

    public void tickAutoOrganize(CompanionEntity companion) {
        if (!autoOrganizePending || isOrganizing) return;
        if (--organizeDelayTicks <= 0) {
            autoOrganizePending = false;
            if (com.aicompanion.AICompanionConfig.COMMON.autoOrganizeInventory.get()) {
                Player owner = companion != null ? companion.getOwnerPlayer() : null;
                if (owner != null && !owner.containerMenu.getCarried().isEmpty()) {
                    autoOrganizePending = true;
                    organizeDelayTicks = 10;
                    return;
                }
                isOrganizing = true;
                try {
                    InventoryOrganizer.compact(this);
                    InventoryOrganizer.organize(this);
                } finally {
                    isOrganizing = false;
                }
                if (companion != null && companion.level() != null && !companion.level().isClientSide()) {
                    companion.syncToOwner();
                }
            }
        }
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= this.stacks.size()) {
            return ItemStack.EMPTY;
        }
        return super.getStackInSlot(slot);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (slot >= 0 && slot < this.stacks.size()) {
            super.setStackInSlot(slot, stack);
        }
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (slot < 0 || slot >= this.stacks.size()) {
            return stack;
        }
        return super.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= this.stacks.size()) {
            return ItemStack.EMPTY;
        }
        return super.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        if (slot < 0 || slot >= this.stacks.size()) {
            return 64;
        }
        return super.getSlotLimit(slot);
    }

    public boolean hasSpaceFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        for (int i = 0; i < MAIN_SLOTS; i++) {
            ItemStack inSlot = getStackInSlot(i);
            if (inSlot.isEmpty()) return true;
            if (ItemStack.isSameItemSameTags(inSlot, stack) && inSlot.getCount() < inSlot.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    public ItemStack addItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack remaining = stack.copy();
        for (int i = 0; i < MAIN_SLOTS; i++) {
            remaining = insertItem(i, remaining, false);
            if (remaining.isEmpty()) return ItemStack.EMPTY;
        }
        return remaining;
    }

    public int countItem(net.minecraft.world.item.Item item) {
        if (item == null) return 0;
        int count = 0;
        for (int i = 0; i < MAIN_SLOTS; i++) {
            ItemStack inSlot = getStackInSlot(i);
            if (inSlot.getItem() == item) {
                count += inSlot.getCount();
            }
        }
        return count;
    }

    public boolean removeItem(net.minecraft.world.item.Item item, int amount) {
        if (item == null || countItem(item) < amount) return false;
        int needed = amount;
        for (int i = 0; i < MAIN_SLOTS; i++) {
            ItemStack inSlot = getStackInSlot(i);
            if (inSlot.getItem() == item) {
                int take = Math.min(needed, inSlot.getCount());
                inSlot.shrink(take);
                needed -= take;
                if (needed <= 0) break;
            }
        }
        return true;
    }

    public int findItemSlot(net.minecraft.world.item.Item item) {
        if (item == null) return -1;
        for (int i = 0; i < MAIN_SLOTS; i++) {
            if (getStackInSlot(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    public enum CapacityLevel {
        NORMAL("Normal"),      // 0 - 69%
        HIGH("Alta"),          // 70 - 84%
        CRITICAL("Crítica"),   // 85 - 99%
        FULL("Llena");         // 100%

        private final String displayName;

        CapacityLevel(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public int getOccupiedSlotCount() {
        int occupied = 0;
        for (int i = 0; i < MAIN_SLOTS; i++) {
            if (!getStackInSlot(i).isEmpty()) {
                occupied++;
            }
        }
        return occupied;
    }

    public double getOccupiedPercentage() {
        return (double) getOccupiedSlotCount() / (double) MAIN_SLOTS;
    }

    public CapacityLevel getCapacityLevel() {
        int occupied = getOccupiedSlotCount();
        if (occupied >= MAIN_SLOTS) return CapacityLevel.FULL;
        double pct = (double) occupied / (double) MAIN_SLOTS;
        if (pct >= 0.85) return CapacityLevel.CRITICAL;
        if (pct >= 0.70) return CapacityLevel.HIGH;
        return CapacityLevel.NORMAL;
    }

    public int getEmptySlotCount() {
        return MAIN_SLOTS - getOccupiedSlotCount();
    }

    public boolean hasEmptySlot() {
        return getEmptySlotCount() > 0;
    }

    public boolean isFull() {
        return getEmptySlotCount() == 0;
    }

    public boolean isCapacityCritical() {
        int desiredFree = Math.min(3, Math.max(1, com.aicompanion.AICompanionConfig.COMMON.desiredFreeSlots.get()));
        if (getEmptySlotCount() >= desiredFree) return false;
        double threshold = com.aicompanion.AICompanionConfig.COMMON.criticalCapacityThreshold.get();
        return getOccupiedPercentage() >= threshold || getEmptySlotCount() < desiredFree;
    }

    public int purgeTrashItems(CompanionEntity companion) {
        return freeSpaceForMission(companion, null);
    }

    /**
     * Autonomous 7-step Capacity Resolution Sequence:
     * 1. Analysis: Diagnostic of capacity, protected items & mission targets.
     * 2. Compaction: Merge partial item stacks.
     * 3. Nearby Storage: Deposit non-essential surplus into nearest authorized chest (<32m).
     * 4. Emergency Storage: Craft & deploy emergency chest in safe location if allowed.
     * 5. Selective Discard: Under configured policy (CONSERVATIVE, BALANCED, AGGRESSIVE) discard trash / mob loot / excess blocks.
     * 6. Verification: Ensure freed slots >= desiredFreeSlots and anti-pickup loop protection.
     * 7. Mission Continuity: Preserve progress and return freed slots count.
     */
    public int freeSpaceForMission(CompanionEntity companion, Predicate<ItemStack> isMissionTarget) {
        if (companion == null || companion.level().isClientSide()) return 0;

        // Capped strictly at 3 slots max as per user specification (3 free slots is always enough)
        int desiredFree = Math.min(3, Math.max(1, com.aicompanion.AICompanionConfig.COMMON.desiredFreeSlots.get()));
        if (getEmptySlotCount() >= desiredFree) return 0;

        String mode = com.aicompanion.AICompanionConfig.COMMON.inventoryManagementMode.get().toUpperCase();
        int freedSlots = 0;
        List<String> actionsSummary = new ArrayList<>();

        // Paso 2: Compactar stacks
        if (com.aicompanion.AICompanionConfig.COMMON.autoCompactInventory.get()) {
            boolean wasOrganizing = this.isOrganizing;
            this.isOrganizing = true;
            try {
                InventoryOrganizer.compact(this);
            } finally {
                this.isOrganizing = wasOrganizing;
            }
            if (getEmptySlotCount() >= desiredFree) {
                return 0; // Space gained purely from compaction!
            }
        }

        // Paso 3: Evaluar cofre autorizado cercano
        if (com.aicompanion.AICompanionConfig.COMMON.allowDepositSurplus.get()) {
            var nearestChest = companion.getNearestAuthorizedChest(companion.blockPosition(), companion.level(), 32.0);
            if (nearestChest.isPresent()) {
                int stored = depositSurplusToChest(companion, nearestChest.get(), isMissionTarget, desiredFree);
                if (stored > 0) {
                    freedSlots += stored;
                    actionsSummary.add("Guardé " + stored + " objetos en cofre autorizado");
                    if (getEmptySlotCount() >= desiredFree) {
                        finishFreeSpace(companion, freedSlots, actionsSummary);
                        return freedSlots;
                    }
                }
            }
        }

        // Paso 4: Fabricar / colocar cofre de emergencia
        if (!"CONSERVATIVE".equals(mode) && com.aicompanion.AICompanionConfig.COMMON.allowCraftEmergencyChest.get()) {
            BlockPos placedChest = tryDeployEmergencyChest(companion, isMissionTarget);
            if (placedChest != null) {
                int stored = depositSurplusToChest(companion, placedChest, isMissionTarget, desiredFree);
                if (stored > 0) {
                    freedSlots += stored;
                    actionsSummary.add("Coloqué cofre de emergencia en " + placedChest.toShortString() + " y guardé materiales");
                    if (getEmptySlotCount() >= desiredFree) {
                        finishFreeSpace(companion, freedSlots, actionsSummary);
                        return freedSlots;
                    }
                }
            }
        }

        // Paso 5: Descarte selectivo
        if ("CONSERVATIVE".equals(mode)) {
            finishFreeSpace(companion, freedSlots, actionsSummary);
            return freedSlots;
        }

        List<String> droppedItemNames = new ArrayList<>();

        // 5a. Descartar basura descartable (DISPOSABLE_TRASH) si allowDiscardCommonBlocks está activo
        if (com.aicompanion.AICompanionConfig.COMMON.allowDiscardCommonBlocks.get()) {
            for (int i = 0; i < MAIN_SLOTS; i++) {
                if (getEmptySlotCount() >= desiredFree) break;
                ItemStack stack = getStackInSlot(i);
                if (stack.isEmpty()) continue;
                if (isMissionTarget != null && isMissionTarget.test(stack)) continue;
                if (ItemHeuristicValue.isMissionProtected(stack, companion)) continue;
                if (ItemHeuristicValue.isImmutable(stack, companion)) continue;

                if (ItemHeuristicValue.isTrash(stack, companion)) {
                    discardSlot(companion, i, stack, droppedItemNames);
                    freedSlots++;
                }
            }
        }

        // 5b. Descartar botín de mobs si está permitido y aún falta espacio
        if (getEmptySlotCount() < desiredFree) {
            for (int i = 0; i < MAIN_SLOTS; i++) {
                if (getEmptySlotCount() >= desiredFree) break;
                ItemStack stack = getStackInSlot(i);
                if (stack.isEmpty()) continue;
                if (isMissionTarget != null && isMissionTarget.test(stack)) continue;
                if (ItemHeuristicValue.isMissionProtected(stack, companion)) continue;
                if (ItemHeuristicValue.isImmutable(stack, companion)) continue;

                boolean discardableMobLoot = false;
                if (stack.is(net.minecraft.world.item.Items.ROTTEN_FLESH) && com.aicompanion.AICompanionConfig.COMMON.allowDiscardRottenFlesh.get()) discardableMobLoot = true;
                if (stack.is(net.minecraft.world.item.Items.BONE) && com.aicompanion.AICompanionConfig.COMMON.allowDiscardBones.get()) discardableMobLoot = true;
                if (stack.is(net.minecraft.world.item.Items.STRING) && com.aicompanion.AICompanionConfig.COMMON.allowDiscardString.get()) discardableMobLoot = true;

                if (discardableMobLoot) {
                    discardSlot(companion, i, stack, droppedItemNames);
                    freedSlots++;
                }
            }
        }

        // 5c. Descartar excedente de bloques de construcción comunes por encima de la reserva configurada
        if (getEmptySlotCount() < desiredFree && com.aicompanion.AICompanionConfig.COMMON.allowDiscardCommonBlocks.get()) {
            freedSlots += purgeSurplusCommonBlocks(companion, isMissionTarget, droppedItemNames, desiredFree);
        }

        // 5d. Si es modo AGGRESSIVE, descartar LOW_VALUE
        if ("AGGRESSIVE".equals(mode) && getEmptySlotCount() < desiredFree) {
            for (int i = 0; i < MAIN_SLOTS; i++) {
                if (getEmptySlotCount() >= desiredFree) break;
                ItemStack stack = getStackInSlot(i);
                if (stack.isEmpty()) continue;
                if (isMissionTarget != null && isMissionTarget.test(stack)) continue;
                if (ItemHeuristicValue.isMissionProtected(stack, companion)) continue;
                if (ItemHeuristicValue.isImmutable(stack, companion)) continue;

                if (ItemHeuristicValue.getCategory(stack, companion) == ItemHeuristicValue.PriorityCategory.LOW_VALUE) {
                    discardSlot(companion, i, stack, droppedItemNames);
                    freedSlots++;
                }
            }
        }

        // 5e. Deadlock Breaker: Si el inventario sigue con 0 huecos libres (saturación total donde el compañero no puede recoger nada)
        if (getEmptySlotCount() < 1) {
            // Descartar LOW_VALUE (plumas, pedernal, esquejes, flores, palos sobrantes)
            for (int i = 0; i < MAIN_SLOTS; i++) {
                if (getEmptySlotCount() >= 1) break;
                ItemStack stack = getStackInSlot(i);
                if (stack.isEmpty()) continue;
                if (isMissionTarget != null && isMissionTarget.test(stack)) continue;
                if (ItemHeuristicValue.isMissionProtected(stack, companion)) continue;
                if (ItemHeuristicValue.isImmutable(stack, companion)) continue;

                if (ItemHeuristicValue.getCategory(stack, companion) == ItemHeuristicValue.PriorityCategory.LOW_VALUE) {
                    discardSlot(companion, i, stack, droppedItemNames);
                    freedSlots++;
                }
            }
        }

        // Si todavía 0 huecos libres, descartar botín de mob secundario (huesos, hilo, pólvora, ojos de araña) si allowDiscardMobLoot está activo
        if (getEmptySlotCount() < 1 && com.aicompanion.AICompanionConfig.COMMON.allowDiscardMobLoot.get()) {
            for (int i = 0; i < MAIN_SLOTS; i++) {
                if (getEmptySlotCount() >= 1) break;
                ItemStack stack = getStackInSlot(i);
                if (stack.isEmpty()) continue;
                if (isMissionTarget != null && isMissionTarget.test(stack)) continue;
                if (ItemHeuristicValue.isMissionProtected(stack, companion)) continue;
                if (ItemHeuristicValue.isImmutable(stack, companion)) continue;

                if (stack.is(net.minecraft.world.item.Items.BONE) || stack.is(net.minecraft.world.item.Items.STRING) ||
                    stack.is(net.minecraft.world.item.Items.GUNPOWDER) || stack.is(net.minecraft.world.item.Items.SPIDER_EYE)) {
                    discardSlot(companion, i, stack, droppedItemNames);
                    freedSlots++;
                }
            }
        }

        if (!droppedItemNames.isEmpty()) {
            actionsSummary.add("Tiré " + String.join(", ", droppedItemNames));
        }

        finishFreeSpace(companion, freedSlots, actionsSummary);
        return freedSlots;
    }

    private void spawnDroppedItem(CompanionEntity companion, ItemStack toDrop, List<String> droppedItemNames) {
        if (toDrop.isEmpty()) return;
        droppedItemNames.add(toDrop.getCount() + "x " + toDrop.getHoverName().getString());
        net.minecraft.world.entity.item.ItemEntity ent = companion.spawnAtLocation(toDrop);
        if (ent != null) {
            ent.setPickUpDelay(600); // 30 seconds
            if (companion.getCompanionBrain() != null) {
                companion.getCompanionBrain().recordDiscardedItem(ent);
            }
        }
    }

    private void discardSlot(CompanionEntity companion, int slot, ItemStack stack, List<String> droppedItemNames) {
        spawnDroppedItem(companion, stack.copy(), droppedItemNames);
        setStackInSlot(slot, ItemStack.EMPTY);
    }

    private int purgeSurplusCommonBlocks(CompanionEntity companion, Predicate<ItemStack> isMissionTarget, List<String> droppedItemNames, int desiredFree) {
        int freed = 0;
        int reserveCount = Math.max(64, com.aicompanion.AICompanionConfig.COMMON.reserveCommonBlocksCount.get());

        // Count total common building blocks in inventory
        int totalCommonBlocks = 0;
        for (int i = 0; i < MAIN_SLOTS; i++) {
            ItemStack stack = getStackInSlot(i);
            if (!stack.isEmpty() && isCommonBuildingBlock(stack)) {
                totalCommonBlocks += stack.getCount();
            }
        }

        // Only purge full slots if total count strictly exceeds reserveCount by at least the stack's size
        for (int i = 0; i < MAIN_SLOTS; i++) {
            if (getEmptySlotCount() >= desiredFree) break;
            ItemStack stack = getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (isMissionTarget != null && isMissionTarget.test(stack)) continue;
            if (ItemHeuristicValue.isMissionProtected(stack, companion)) continue;
            if (ItemHeuristicValue.isImmutable(stack, companion)) continue;

            if (isCommonBuildingBlock(stack)) {
                if (totalCommonBlocks - stack.getCount() >= reserveCount) {
                    totalCommonBlocks -= stack.getCount();
                    discardSlot(companion, i, stack, droppedItemNames);
                    freed++;
                }
            }
        }
        return freed;
    }

    private int depositSurplusToChest(CompanionEntity companion, BlockPos chestPos, Predicate<ItemStack> isMissionTarget, int desiredFree) {
        if (companion == null || chestPos == null) return 0;
        if (getEmptySlotCount() >= desiredFree) return 0;
        BlockEntity be = companion.level().getBlockEntity(chestPos);
        if (be == null) return 0;

        var cap = be.getCapability(ForgeCapabilities.ITEM_HANDLER);
        if (!cap.isPresent()) return 0;
        IItemHandler targetHandler = cap.orElse(null);
        if (targetHandler == null) return 0;

        int reserveCount = Math.max(64, com.aicompanion.AICompanionConfig.COMMON.reserveCommonBlocksCount.get());
        int freed = 0;

        // Calculate total common building blocks in inventory
        int totalCommonBlocks = 0;
        for (int i = 0; i < MAIN_SLOTS; i++) {
            ItemStack stack = getStackInSlot(i);
            if (!stack.isEmpty() && isCommonBuildingBlock(stack)) {
                totalCommonBlocks += stack.getCount();
            }
        }

        for (int i = 0; i < MAIN_SLOTS; i++) {
            if (getEmptySlotCount() >= desiredFree) break;
            ItemStack stack = getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (isMissionTarget != null && isMissionTarget.test(stack)) continue;
            if (ItemHeuristicValue.isMissionProtected(stack, companion)) continue;
            if (ItemHeuristicValue.isImmutable(stack, companion)) continue;

            // Keep reserve of common building blocks (dirt, cobblestone, stone...)
            if (isCommonBuildingBlock(stack)) {
                if (totalCommonBlocks - stack.getCount() < reserveCount) continue;
            }

            // Protect basic survival essentials: food (<= 16), torches (<= 16), logs (<= 16)
            if (stack.isEdible() && countItem(stack.getItem()) <= 16) continue;
            if (stack.is(net.minecraft.world.item.Items.TORCH) && countItem(net.minecraft.world.item.Items.TORCH) <= 16) continue;
            if (stack.is(net.minecraft.tags.ItemTags.LOGS) && countItem(stack.getItem()) <= 16) continue;

            // Extract and insert to chest
            ItemStack toDeposit = stack.copy();
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(targetHandler, toDeposit, false);
            int deposited = stack.getCount() - remainder.getCount();
            if (deposited > 0) {
                if (isCommonBuildingBlock(stack)) {
                    totalCommonBlocks -= deposited;
                }
                if (remainder.isEmpty()) {
                    setStackInSlot(i, ItemStack.EMPTY);
                    freed++;
                } else {
                    stack.setCount(remainder.getCount());
                }
            }
        }
        return freed;
    }

    private BlockPos tryDeployEmergencyChest(CompanionEntity companion, Predicate<ItemStack> isMissionTarget) {
        if (companion == null || companion.level().isClientSide()) return null;

        // 1. Check if companion has a chest or can craft one
        int chestSlot = findItemSlot(net.minecraft.world.item.Items.CHEST);
        if (chestSlot == -1) {
            // If completely full, discard 1 slot of trash first to make room for crafting chest
            if (getEmptySlotCount() == 0) {
                for (int i = 0; i < MAIN_SLOTS; i++) {
                    ItemStack s = getStackInSlot(i);
                    if (ItemHeuristicValue.isTrash(s, companion) && !ItemHeuristicValue.isImmutable(s, companion)) {
                        discardSlot(companion, i, s, new ArrayList<>());
                        break;
                    }
                }
            }
            if (com.aicompanion.util.EmergencyCrafting.ensureChest(companion)) {
                chestSlot = findItemSlot(net.minecraft.world.item.Items.CHEST);
            }
        }

        if (chestSlot == -1) return null;

        // 2. Find a safe adjacent block position to place chest
        BlockPos feet = companion.blockPosition();
        Direction[] directions = new Direction[] {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
        };

        BlockPos targetPos = null;
        for (Direction dir : directions) {
            BlockPos p = feet.relative(dir);
            if (companion.level().getBlockState(p).canBeReplaced() &&
                companion.level().getBlockState(p.below()).isFaceSturdy(companion.level(), p.below(), Direction.UP)) {
                targetPos = p;
                break;
            }
        }

        if (targetPos == null) return null;

        // 3. Place chest
        boolean placed = companion.level().setBlock(targetPos, Blocks.CHEST.defaultBlockState(), 3);
        if (!placed) return null;

        // Consume 1 chest item
        removeItem(net.minecraft.world.item.Items.CHEST, 1);

        // Authorize chest
        companion.authorizeChest(targetPos);

        AICompanionMod.LOGGER.info("[ESCLAVO-ECONOMY] Placed emergency chest at {} and authorized it.", targetPos);
        return targetPos;
    }

    public static boolean isCommonBuildingBlock(net.minecraft.world.item.Item item) {
        if (item == null) return false;
        return item == net.minecraft.world.item.Items.COBBLESTONE ||
               item == net.minecraft.world.item.Items.COBBLED_DEEPSLATE ||
               item == net.minecraft.world.item.Items.NETHERRACK ||
               item == net.minecraft.world.item.Items.STONE ||
               item == net.minecraft.world.item.Items.DEEPSLATE ||
               item == net.minecraft.world.item.Items.BASALT ||
               item == net.minecraft.world.item.Items.SMOOTH_BASALT ||
               item == net.minecraft.world.item.Items.BLACKSTONE ||
               item == net.minecraft.world.item.Items.DIRT ||
               item == net.minecraft.world.item.Items.COARSE_DIRT ||
               item == net.minecraft.world.item.Items.ROOTED_DIRT ||
               item == net.minecraft.world.item.Items.MUD;
    }

    public static boolean isCommonBuildingBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (isCommonBuildingBlock(stack.getItem())) return true;
        if (stack.is(net.minecraft.tags.ItemTags.DIRT) ||
            stack.is(net.minecraft.tags.ItemTags.create(new ResourceLocation("forge", "dirt"))) ||
            stack.is(net.minecraft.tags.ItemTags.create(new ResourceLocation("forge", "cobblestone"))) ||
            stack.is(net.minecraft.tags.ItemTags.create(new ResourceLocation("forge", "stone")))) {
            return true;
        }
        ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = key.getPath().toLowerCase();
        return path.contains("cobblestone") || path.contains("cobbled_") || path.contains("dirt");
    }

    private void finishFreeSpace(CompanionEntity companion, int freedSlots, List<String> actionsSummary) {
        if (freedSlots > 0 && !actionsSummary.isEmpty()) {
            String summary = String.join(". ", actionsSummary);
            AICompanionMod.LOGGER.info("[ESCLAVO-ECONOMY] Capacity resolution: {} slots freed. Actions: {}", freedSlots, summary);
            if (com.aicompanion.AICompanionConfig.COMMON.showInventoryManagementAlerts.get()) {
                Player owner = companion.getOwnerPlayer();
                if (owner != null && owner.isAlive() && !owner.isDeadOrDying()) {
                    owner.sendSystemMessage(
                        Component.literal("[" + companion.getCustomCompanionName() + "] ")
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(summary + " para continuar mi misión.").withStyle(ChatFormatting.YELLOW))
                    );
                }
            }
            if (companion.level() != null && !companion.level().isClientSide()) {
                companion.syncToOwner();
            }
        } else if (getEmptySlotCount() < com.aicompanion.AICompanionConfig.COMMON.desiredFreeSlots.get()) {
            AICompanionMod.LOGGER.warn("[ESCLAVO-ECONOMY] Capacity resolution could not free desired slots. Free slots: {}/{}. Actions: {}",
                    getEmptySlotCount(), MAIN_SLOTS, actionsSummary.isEmpty() ? "None possible" : String.join(". ", actionsSummary));
        }
    }
}
