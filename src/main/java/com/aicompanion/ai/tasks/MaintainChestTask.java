package com.aicompanion.ai.tasks;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.ai.planning.Goal;
import com.aicompanion.ai.planning.PrerequisitePlanner;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import com.aicompanion.inventory.CompanionInventoryHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Enterprise Autonomous Logistics & Chest Supply Daemon for Esclavo.
 * Ensures zero item loss/duplication using Forge simulated item transfer,
 * supports multi-item quota maintenance (e.g. keeping 64 bread stocked),
 * respects player chest authorization, automatically triggers prerequisite
 * gathering/crafting/farming when supplies run low, and monitors chests as a daemon.
 */
public class MaintainChestTask extends CompanionTask {
    private BlockPos chestPos;
    private final Map<ResourceLocation, Integer> targetSupplies = new LinkedHashMap<>();
    private int checkCooldown = 0;
    private int searchCooldown = 0;
    private int failedChecks = 0;
    private int operationsMade = 0;
    private boolean acquiringSubtasksActive = false;

    public MaintainChestTask(CompanionEntity companion) {
        this(companion, null, null, 0);
    }

    public MaintainChestTask(CompanionEntity companion, BlockPos chestPos) {
        this(companion, chestPos, null, 0);
    }

    public MaintainChestTask(CompanionEntity companion, BlockPos chestPos, ResourceLocation targetSupplyItem, int targetSupplyCount) {
        super(companion);
        this.chestPos = chestPos;
        if (targetSupplyItem != null && targetSupplyCount > 0) {
            this.targetSupplies.put(targetSupplyItem, targetSupplyCount);
        }
        if (chestPos != null && companion != null) {
            companion.authorizeChest(chestPos);
        }
    }

    public void addTargetSupply(ResourceLocation item, int count) {
        if (item != null && count > 0) {
            targetSupplies.put(item, count);
        }
    }

    @Override
    public void start() {
        if (chestPos == null) {
            chestPos = findNearestChest();
        }
        companion.setCurrentState(CompanionState.DEPOSITING);
        this.stage = "INITIALIZING";
        this.substate = "Localizando almacenamiento autorizado...";
        markProgress();
    }

    @Override
    public void tick() {
        if (paused || complete) return;
        if (!(companion.level() instanceof ServerLevel sl)) return;

        // Reset acquisition flag when resuming or when no subtasks are executing
        if (acquiringSubtasksActive) {
            acquiringSubtasksActive = false;
        }

        // 1. Locate chest if not set or invalid
        if (chestPos == null || !isValidChest(sl, chestPos)) {
            if (--searchCooldown <= 0) {
                chestPos = findNearestChest();
                searchCooldown = 60; // 3 seconds interval
                if (chestPos == null) {
                    failedChecks++;
                    if (failedChecks >= 4) { // 12 seconds without finding any chest
                        fail("No tengo ningún almacenamiento autorizado en esta dimensión. Selecciona un cofre con el Silbato o míralo y ordéname 'mantén este cofre'.");
                        companion.getNavManager().stop();
                        return;
                    }
                } else {
                    failedChecks = 0;
                }
            }
            if (chestPos == null) {
                companion.setCurrentState(CompanionState.WAITING);
                this.stage = "WAITING";
                this.substate = "Esperando cofre autorizado...";
                return;
            }
        }

        // 2. Approach chest
        double distSq = companion.blockPosition().distSqr(chestPos);
        if (distSq > 9.0) {
            companion.setCurrentState(CompanionState.NAVIGATING);
            this.stage = "NAVIGATING";
            this.substate = "Acercándose a almacenamiento en " + chestPos.toShortString();
            companion.getNavManager().moveTo(chestPos, 1.0);
            return;
        }

        // 3. At chest: deposit & autonomous supply routine every 60 ticks (~3 seconds)
        companion.setCurrentState(CompanionState.DEPOSITING);
        companion.getNavManager().stop();

        if (--checkCooldown <= 0) {
            checkCooldown = 60;
            IItemHandler chestHandler = getItemHandler(sl, chestPos);
            if (chestHandler == null) {
                return;
            }

            CompanionInventoryHandler inv = companion.getInventoryHandler();
            boolean operatedAny = false;

            // 3a. Autonomous supply maintenance for all configured supply quotas
            for (Map.Entry<ResourceLocation, Integer> entry : targetSupplies.entrySet()) {
                ResourceLocation supplyId = entry.getKey();
                int quota = entry.getValue();

                int inChest = countItemInHandler(chestHandler, supplyId);
                int neededInChest = quota - inChest;

                if (neededInChest > 0) {
                    // Transfer matching items from companion inventory to chest safely
                    for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS && neededInChest > 0; i++) {
                        ItemStack compStack = inv.getStackInSlot(i);
                        if (!compStack.isEmpty() && BuiltInRegistries.ITEM.getKey(compStack.getItem()).equals(supplyId)) {
                            int countBefore = compStack.getCount();
                            int toTransfer = Math.min(neededInChest, countBefore);
                            if (safeTransfer(inv, i, toTransfer, chestHandler)) {
                                operatedAny = true;
                                int transferred = countBefore - inv.getStackInSlot(i).getCount();
                                neededInChest -= Math.max(1, transferred);
                            }
                        }
                    }

                    // Re-check deficit
                    int stillNeeded = quota - countItemInHandler(chestHandler, supplyId);
                    if (stillNeeded > 0) {
                        int ownedByNpc = countItemInHandler(inv, supplyId);
                        int missingToAcquire = stillNeeded - ownedByNpc;

                        if (missingToAcquire > 0) {
                            // Autonomous supply triggering: calculate and push acquisition subtasks!
                            if (companion.getCompanionBrain() != null && companion.getCompanionBrain().getTaskManager() != null) {
                                this.stage = "SUPPLY_GATHERING";
                                this.substate = "Consiguiendo " + missingToAcquire + "x " + supplyId.getPath() + " para el cofre...";
                                Goal obtainGoal = Goal.obtain(supplyId, missingToAcquire);
                                List<CompanionTask> subtasks = PrerequisitePlanner.resolvePlanWithPrerequisites(companion, obtainGoal);
                                if (!subtasks.isEmpty()) {
                                    acquiringSubtasksActive = true;
                                    companion.getCompanionBrain().getTaskManager().pushSubtasks(subtasks);
                                    markProgress();
                                    return;
                                }
                            }
                        }
                    }
                }
            }

            // 3b. Deposit companion general loot & resources (protecting tools, armor, and essential food)
            for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
                ItemStack compStack = inv.getStackInSlot(i);
                if (compStack.isEmpty()) continue;

                // Protect tools, weapons, armor
                if (compStack.is(net.minecraft.tags.ItemTags.TOOLS)
                        || compStack.is(net.minecraft.tags.ItemTags.SWORDS)
                        || compStack.getItem() instanceof net.minecraft.world.item.ArmorItem) {
                    continue;
                }

                // Protect target supplies in companion's pocket
                ResourceLocation stackId = BuiltInRegistries.ITEM.getKey(compStack.getItem());
                if (targetSupplies.containsKey(stackId)) {
                    continue;
                }

                int toDeposit = compStack.getCount();
                if (safeTransfer(inv, i, toDeposit, chestHandler)) {
                    operatedAny = true;
                }
            }

            if (operatedAny) {
                operationsMade++;
                sl.playSound(null, chestPos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.8F, 1.0F);
                markProgress();
            }

            this.stage = "MAINTAINING";
            this.substate = targetSupplies.isEmpty()
                    ? "Cofre organizado (" + operationsMade + " operaciones)"
                    : "Cofre abastecido en " + chestPos.toShortString();
        }
    }

    /**
     * Forge-compliant zero-loss item transfer.
     * Simulates extraction and stacked insertion, checks exact capacity,
     * extracts only what fits, and executes a rollback in case of an unexpected destination full event.
     */
    public static boolean safeTransfer(IItemHandler source, int sourceSlot, int maxAmount, IItemHandler destination) {
        if (source == null || destination == null || maxAmount <= 0) return false;

        // 1. Simulate extraction from source
        ItemStack simulatedExtract = source.extractItem(sourceSlot, maxAmount, true);
        if (simulatedExtract.isEmpty()) return false;

        // 2. Simulate insertion into destination
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(destination, simulatedExtract, true);
        int actualToMove = simulatedExtract.getCount() - remainder.getCount();
        if (actualToMove <= 0) return false;

        // 3. Actually extract from source
        ItemStack extracted = source.extractItem(sourceSlot, actualToMove, false);
        if (extracted.isEmpty()) return false;

        // 4. Actually insert into destination
        ItemStack failed = ItemHandlerHelper.insertItemStacked(destination, extracted, false);
        if (!failed.isEmpty()) {
            // Safety rollback: return uninserted items back to source safely
            ItemHandlerHelper.insertItemStacked(source, failed, false);
        }
        return true;
    }

    public static int countItemInHandler(IItemHandler handler, ResourceLocation itemId) {
        if (handler == null || itemId == null) return 0;
        int count = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).equals(itemId)) {
                count += s.getCount();
            }
        }
        return count;
    }

    private IItemHandler getItemHandler(ServerLevel sl, BlockPos pos) {
        BlockEntity be = sl.getBlockEntity(pos);
        if (be == null) return null;
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        if (handler == null && be instanceof Container container) {
            handler = new InvWrapper(container);
        }
        return handler;
    }

    private boolean isValidChest(ServerLevel sl, BlockPos p) {
        if (p == null) return false;
        boolean isBlockStorage = sl.getBlockState(p).is(Blocks.CHEST)
                || sl.getBlockState(p).is(Blocks.BARREL)
                || sl.getBlockState(p).is(Blocks.TRAPPED_CHEST);
        if (!isBlockStorage) return false;
        return companion.isChestAuthorized(p);
    }

    private BlockPos findNearestChest() {
        if (!(companion.level() instanceof ServerLevel sl)) return null;
        BlockPos origin = companion.blockPosition();

        // ONLY search among explicitly authorized chests in this dimension!
        var nearest = companion.getNearestAuthorizedChest(origin, sl, 64.0);
        return nearest.orElse(null);
    }

    @Override
    public String getDescription() {
        if (!targetSupplies.isEmpty()) {
            StringBuilder sb = new StringBuilder("Mantener stock (");
            int idx = 0;
            for (Map.Entry<ResourceLocation, Integer> e : targetSupplies.entrySet()) {
                if (idx++ > 0) sb.append(", ");
                sb.append(e.getValue()).append("x ").append(e.getKey().getPath());
            }
            sb.append(")");
            return sb.toString();
        }
        return "Mantener y organizar cofre" + (chestPos != null ? " en " + chestPos.toShortString() : "");
    }

    @Override
    public String getProgress() {
        return "Operaciones realizadas: " + operationsMade;
    }

    @Override
    public String getTaskType() {
        return "MAINTAIN_CHEST";
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (chestPos != null) tag.put("ChestPos", NbtUtils.writeBlockPos(chestPos));
        tag.putInt("OperationsMade", operationsMade);

        ListTag supplyList = new ListTag();
        for (Map.Entry<ResourceLocation, Integer> entry : targetSupplies.entrySet()) {
            CompoundTag sTag = new CompoundTag();
            sTag.putString("Item", entry.getKey().toString());
            sTag.putInt("Count", entry.getValue());
            supplyList.add(sTag);
        }
        tag.put("TargetSupplies", supplyList);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        if (tag.contains("ChestPos")) this.chestPos = NbtUtils.readBlockPos(tag.getCompound("ChestPos"));
        if (tag.contains("OperationsMade")) this.operationsMade = tag.getInt("OperationsMade");

        targetSupplies.clear();
        if (tag.contains("TargetSupplies", Tag.TAG_LIST)) {
            ListTag list = tag.getList("TargetSupplies", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag sTag = list.getCompound(i);
                targetSupplies.put(new ResourceLocation(sTag.getString("Item")), sTag.getInt("Count"));
            }
        }
    }
}
