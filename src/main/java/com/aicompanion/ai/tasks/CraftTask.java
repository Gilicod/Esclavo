package com.aicompanion.ai.tasks;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import com.aicompanion.inventory.CompanionInventoryHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeHooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.aicompanion.ai.mining.MiningTarget;
import com.aicompanion.ai.planning.RecipeDependencyGraph;
import com.aicompanion.ai.planning.PrerequisitePlanner;
import com.aicompanion.inventory.InventoryRequirementTracker;

/**
 * Enterprise crafting engine for Esclavo.
 * Dynamically queries Minecraft Server RecipeManager, checks crafting table requirements (3x3 vs 2x2),
 * resolves ingredients against inventory tags (e.g. any planks, any cobblestone, modded recipes),
 * and respects crafting remainders.
 */
public class CraftTask extends CompanionTask {
    private ResourceLocation resultItemId;
    private int amount;
    private int craftedCount = 0;
    private int craftTicks = 0;

    public CraftTask(CompanionEntity companion, ResourceLocation resultItemId, int amount) {
        super(companion);
        this.resultItemId = resultItemId;
        this.amount = Math.max(1, amount);
    }

    @Override
    public List<CompanionTask> checkPreconditions() {
        Item targetItem = BuiltInRegistries.ITEM.get(resultItemId);
        if (targetItem == Items.AIR) return Collections.emptyList();

        List<CompanionTask> prereqs = new ArrayList<>();
        CompanionInventoryHandler inv = companion.getInventoryHandler();

        // Rule #1 (Pilar 5): Spatial POI Episodic Memory Query
        // Before attempting to craft or gather a new crafting table, check if one is remembered nearby
        boolean requiresCraftingTable = (targetItem instanceof net.minecraft.world.item.TieredItem) ||
                                        (targetItem instanceof net.minecraft.world.item.ArmorItem) ||
                                        targetItem == Items.CHEST || targetItem == Items.BARREL ||
                                        targetItem == Items.FURNACE;

        if (requiresCraftingTable && companion.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            BlockPos currentPos = companion.blockPosition();
            boolean nearTable = false;
            for (BlockPos p : BlockPos.betweenClosed(currentPos.offset(-4, -2, -4), currentPos.offset(4, 2, 4))) {
                if (sl.getBlockState(p).is(Blocks.CRAFTING_TABLE)) {
                    nearTable = true;
                    if (companion.getMemory() != null) {
                        companion.getMemory().rememberPOI(p, Blocks.CRAFTING_TABLE, sl.getGameTime());
                    }
                    break;
                }
            }

            if (!nearTable && companion.getMemory() != null) {
                var closestTable = companion.getMemory().findClosest(sl, currentPos, Blocks.CRAFTING_TABLE, 64.0);
                if (closestTable.isPresent()) {
                    com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-MEMORY] CraftTask: Reusing remembered crafting table at {} instead of chopping trees for a new one!", closestTable.get());
                    prereqs.add(new GotoTask(companion, closestTable.get()));
                }
            }
        }

        // 1. Planks
        if (targetItem.getDescriptionId().contains("planks")) {
            int logs = InventoryRequirementTracker.countWood(companion, "any");
            if (logs == 0) {
                prereqs.add(new ChopTask(companion, "any", 1, true));
            }
            return prereqs;
        }

        // 2. Sticks
        if (targetItem == Items.STICK) {
            int planks = InventoryRequirementTracker.countMatching(companion, s -> s.is(net.minecraft.tags.ItemTags.PLANKS));
            if (planks < 2) {
                int logs = InventoryRequirementTracker.countWood(companion, "any");
                if (logs == 0) {
                    prereqs.add(new ChopTask(companion, "any", 1, true));
                }
                prereqs.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 4));
            }
            return prereqs;
        }

        // 3. Wooden Tools
        if (targetItem == Items.WOODEN_AXE || targetItem == Items.WOODEN_PICKAXE || targetItem == Items.WOODEN_SHOVEL || targetItem == Items.WOODEN_SWORD) {
            int planks = InventoryRequirementTracker.countMatching(companion, s -> s.is(net.minecraft.tags.ItemTags.PLANKS));
            int sticks = inv.countItem(Items.STICK);
            int logs = InventoryRequirementTracker.countWood(companion, "any");

            int neededPlanks = (targetItem == Items.WOODEN_SHOVEL) ? 1 : (targetItem == Items.WOODEN_SWORD) ? 2 : 3;
            int totalPlanksAvailable = planks + (logs * 4);

            if (totalPlanksAvailable < neededPlanks + (sticks < 2 ? 2 : 0)) {
                int logsToGather = (int) Math.ceil((neededPlanks + (sticks < 2 ? 2 : 0) - planks - (logs * 4)) / 4.0);
                if (logsToGather <= 0) logsToGather = 1;
                prereqs.add(new ChopTask(companion, "any", logsToGather, true));
            }

            if (planks < neededPlanks + (sticks < 2 ? 2 : 0)) {
                prereqs.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 4));
            }

            if (sticks < 2) {
                prereqs.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.STICK), 4));
            }
            return prereqs;
        }

        // 4. Stone Tools
        if (targetItem == Items.STONE_AXE || targetItem == Items.STONE_PICKAXE || targetItem == Items.STONE_SHOVEL || targetItem == Items.STONE_SWORD) {
            int cobble = inv.countItem(Items.COBBLESTONE);
            int sticks = inv.countItem(Items.STICK);
            int neededCobble = (targetItem == Items.STONE_SHOVEL) ? 1 : (targetItem == Items.STONE_SWORD) ? 2 : 3;

            if (cobble < neededCobble) {
                int ownedPick = RecipeDependencyGraph.getHighestOwnedPickaxeTier(companion);
                if (ownedPick < RecipeDependencyGraph.TIER_WOOD) {
                    prereqs.addAll(PrerequisitePlanner.resolvePickaxePrerequisites(companion, ownedPick, RecipeDependencyGraph.TIER_WOOD));
                }
                prereqs.add(new MineTask(companion, MiningTarget.of("cobblestone"), neededCobble - cobble));
            }

            if (sticks < 2) {
                prereqs.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.STICK), 4));
            }
            return prereqs;
        }

        // 5. Iron Tools
        if (targetItem == Items.IRON_SWORD || targetItem == Items.IRON_AXE || targetItem == Items.IRON_PICKAXE || targetItem == Items.IRON_SHOVEL) {
            int iron = inv.countItem(Items.IRON_INGOT);
            int sticks = inv.countItem(Items.STICK);
            int neededIron = (targetItem == Items.IRON_SHOVEL) ? 1 : (targetItem == Items.IRON_SWORD) ? 2 : 3;
            if (iron < neededIron) {
                int ownedPick = RecipeDependencyGraph.getHighestOwnedPickaxeTier(companion);
                if (ownedPick < RecipeDependencyGraph.TIER_STONE) {
                    prereqs.addAll(PrerequisitePlanner.resolvePickaxePrerequisites(companion, ownedPick, RecipeDependencyGraph.TIER_STONE));
                }
                prereqs.add(new SmeltTask(companion, new ResourceLocation("minecraft", "raw_iron"), neededIron - iron));
            }
            if (sticks < 2) {
                prereqs.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.STICK), 4));
            }
            return prereqs;
        }

        // 6. Diamond Tools
        if (targetItem == Items.DIAMOND_SWORD || targetItem == Items.DIAMOND_AXE || targetItem == Items.DIAMOND_PICKAXE || targetItem == Items.DIAMOND_SHOVEL) {
            int diamonds = inv.countItem(Items.DIAMOND);
            int sticks = inv.countItem(Items.STICK);
            int neededDiamonds = (targetItem == Items.DIAMOND_SHOVEL) ? 1 : (targetItem == Items.DIAMOND_SWORD) ? 2 : 3;
            if (diamonds < neededDiamonds) {
                int ownedPick = RecipeDependencyGraph.getHighestOwnedPickaxeTier(companion);
                if (ownedPick < RecipeDependencyGraph.TIER_IRON) {
                    prereqs.addAll(PrerequisitePlanner.resolvePickaxePrerequisites(companion, ownedPick, RecipeDependencyGraph.TIER_IRON));
                }
                prereqs.add(new MineTask(companion, MiningTarget.of("diamond_ore"), neededDiamonds - diamonds));
            }
            if (sticks < 2) {
                prereqs.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.STICK), 4));
            }
            return prereqs;
        }

        // 7. Flint and Steel
        if (targetItem == Items.FLINT_AND_STEEL) {
            int iron = inv.countItem(Items.IRON_INGOT);
            int flint = inv.countItem(Items.FLINT);
            if (iron < 1) {
                int ownedPick = RecipeDependencyGraph.getHighestOwnedPickaxeTier(companion);
                if (ownedPick < RecipeDependencyGraph.TIER_STONE) {
                    prereqs.addAll(PrerequisitePlanner.resolvePickaxePrerequisites(companion, ownedPick, RecipeDependencyGraph.TIER_STONE));
                }
                prereqs.add(new SmeltTask(companion, new ResourceLocation("minecraft", "raw_iron"), 1));
            }
            if (flint < 1) {
                prereqs.add(new MineTask(companion, MiningTarget.of("gravel"), 3));
            }
            return prereqs;
        }

        return prereqs;
    }

    @Override
    public void start() {
        companion.setCurrentState(CompanionState.CRAFTING);
        this.craftedCount = 0;
        this.craftTicks = 0;
    }

    @Override
    public void tick() {
        if (paused || complete) return;

        if (craftedCount >= amount) {
            complete = true;
            companion.setCurrentState(CompanionState.IDLE);
            return;
        }

        craftTicks++;
        if (craftTicks < 5) return; // Small realistic cadence
        craftTicks = 0;

        Item targetItem = BuiltInRegistries.ITEM.get(resultItemId);
        if (targetItem == Items.AIR) {
            fail("Objeto desconocido para fabricar: " + resultItemId);
            return;
        }

        RecipeManager rm = companion.level().getRecipeManager();
        List<CraftingRecipe> candidates = new ArrayList<>();
        for (CraftingRecipe r : rm.getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack res = r.getResultItem(companion.level().registryAccess());
            if (!res.isEmpty() && res.is(targetItem)) {
                candidates.add(r);
            }
        }

        // Try candidate recipes from RecipeManager (Internal 3x3 crafting grid capability)
        boolean craftedAny = false;
        for (CraftingRecipe recipe : candidates) {
            if (canCraft(recipe)) {
                executeCraft(recipe);
                craftedAny = true;
                break;
            }
        }

        // Fallback for essential survival shortcuts if dynamic recipe matching was incomplete
        if (!craftedAny) {
            craftedAny = executeSurvivalFallback(targetItem);
        }

        if (craftedAny) {
            markProgress();
            if (companion.level() != null) {
                companion.level().playSound(null, companion.blockPosition(), SoundEvents.VILLAGER_WORK_TOOLSMITH, SoundSource.NEUTRAL, 0.4f, 1.2f);
            }
            if (craftedCount >= amount) {
                complete = true;
                companion.setCurrentState(CompanionState.IDLE);
            }
        } else {
            // Dynamic Prerequisite Resolution: Before aborting, check if companion can gather/craft missing ingredients
            List<CompanionTask> dynamicPrereqs = checkPreconditions();
            if (dynamicPrereqs != null && !dynamicPrereqs.isEmpty()) {
                com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-CRAFT] Missing ingredients for {}, pushing {} dynamic prerequisite subtasks",
                        resultItemId, dynamicPrereqs.size());
                logEvent("Ingredientes insuficientes para " + resultItemId.getPath() + ". Resolviendo prerequisitos dinámicos...");
                companion.getCompanionBrain().getTaskManager().pushSubtasks(dynamicPrereqs);
                return;
            }
            fail("No tengo los ingredientes necesarios para fabricar " + resultItemId.getPath() + " (" + craftedCount + "/" + amount + ")");
        }
    }

    private boolean canCraft(CraftingRecipe recipe) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        boolean[] usedSlot = new boolean[CompanionInventoryHandler.TOTAL_SLOTS];

        for (Ingredient ing : ingredients) {
            if (ing.isEmpty()) continue;
            boolean matched = false;
            for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS; i++) {
                if (usedSlot[i]) continue;
                ItemStack stack = inv.getStackInSlot(i);
                if (!stack.isEmpty() && ing.test(stack)) {
                    usedSlot[i] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        return true;
    }

    private void executeCraft(CraftingRecipe recipe) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        NonNullList<Ingredient> ingredients = recipe.getIngredients();

        for (Ingredient ing : ingredients) {
            if (ing.isEmpty()) continue;
            for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS; i++) {
                ItemStack stack = inv.getStackInSlot(i);
                if (!stack.isEmpty() && ing.test(stack)) {
                    // Handle craft remaining item (e.g. buckets)
                    ItemStack remainder = ForgeHooks.getCraftingRemainingItem(stack);
                    stack.shrink(1);
                    if (!remainder.isEmpty()) {
                        inv.addItem(remainder);
                    }
                    break;
                }
            }
        }

        ItemStack output = recipe.getResultItem(companion.level().registryAccess()).copy();
        inv.addItem(output);
        craftedCount += output.getCount();
    }

    private boolean executeSurvivalFallback(Item target) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();

        // 1. Planks from any wood
        if (target.getDescriptionId().contains("planks")) {
            for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS; i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (s.is(net.minecraft.tags.ItemTags.LOGS) && s.getCount() > 0) {
                    s.shrink(1);
                    inv.addItem(new ItemStack(target, 4));
                    craftedCount += 4;
                    return true;
                }
            }
        }

        // 2. Sticks
        if (target == Items.STICK) {
            if (ensureSticksAvailable(amount)) {
                craftedCount = amount;
                return true;
            }
        }

        // 3. Crafting Table
        if (target == Items.CRAFTING_TABLE) {
            if (!hasPlanks(4) && hasLogs(1)) {
                consumeLogs(1);
                inv.addItem(new ItemStack(Items.OAK_PLANKS, 4));
            }
            if (hasPlanks(4)) {
                consumePlanks(4);
                inv.addItem(new ItemStack(Items.CRAFTING_TABLE, 1));
                craftedCount += 1;
                return true;
            }
        }

        // 4. Tools (Pickaxes / Axes)
        if (target == Items.WOODEN_PICKAXE || target == Items.STONE_PICKAXE || target == Items.IRON_PICKAXE || target == Items.DIAMOND_PICKAXE
                || target == Items.WOODEN_AXE || target == Items.STONE_AXE || target == Items.IRON_AXE || target == Items.DIAMOND_AXE) {
            Item headMat = (target == Items.WOODEN_PICKAXE || target == Items.WOODEN_AXE) ? Items.OAK_PLANKS
                    : (target == Items.STONE_PICKAXE || target == Items.STONE_AXE) ? Items.COBBLESTONE
                    : (target == Items.DIAMOND_PICKAXE || target == Items.DIAMOND_AXE) ? Items.DIAMOND : Items.IRON_INGOT;
            int headReq = 3;

            boolean hasHead = (headMat == Items.OAK_PLANKS)
                    ? hasPlanks(3)
                    : inv.countItem(headMat) >= headReq;

            if (hasHead) {
                ensureSticksAvailable(2);
                boolean hasSticks = inv.countItem(Items.STICK) >= 2;

                if (hasSticks) {
                    if (headMat == Items.OAK_PLANKS) consumePlanks(3);
                    else inv.removeItem(headMat, headReq);
                    inv.removeItem(Items.STICK, 2);
                    inv.addItem(new ItemStack(target, 1));
                    craftedCount += 1;
                    return true;
                }
            }
        }

        // 5. Swords (Wooden / Stone / Iron / Diamond)
        if (target == Items.WOODEN_SWORD || target == Items.STONE_SWORD || target == Items.IRON_SWORD || target == Items.DIAMOND_SWORD) {
            Item bladeMat = (target == Items.WOODEN_SWORD) ? Items.OAK_PLANKS
                    : (target == Items.STONE_SWORD) ? Items.COBBLESTONE
                    : (target == Items.DIAMOND_SWORD) ? Items.DIAMOND : Items.IRON_INGOT;
            int bladeReq = 2;

            boolean hasBlade = (bladeMat == Items.OAK_PLANKS)
                    ? hasPlanks(2)
                    : inv.countItem(bladeMat) >= bladeReq;

            if (hasBlade) {
                ensureSticksAvailable(1);
                boolean hasSticks = inv.countItem(Items.STICK) >= 1;

                if (hasSticks) {
                    if (bladeMat == Items.OAK_PLANKS) consumePlanks(2);
                    else inv.removeItem(bladeMat, bladeReq);
                    inv.removeItem(Items.STICK, 1);
                    inv.addItem(new ItemStack(target, 1));
                    craftedCount += 1;
                    return true;
                }
            }
        }

        // 6. Flint and Steel
        if (target == Items.FLINT_AND_STEEL) {
            if (inv.countItem(Items.IRON_INGOT) >= 1 && inv.countItem(Items.FLINT) >= 1) {
                inv.removeItem(Items.IRON_INGOT, 1);
                inv.removeItem(Items.FLINT, 1);
                inv.addItem(new ItemStack(Items.FLINT_AND_STEEL, 1));
                craftedCount += 1;
                return true;
            }
        }

        // 7. Furnace
        if (target == Items.FURNACE) {
            if (inv.countItem(Items.COBBLESTONE) >= 8) {
                inv.removeItem(Items.COBBLESTONE, 8);
                inv.addItem(new ItemStack(Items.FURNACE, 1));
                craftedCount += 1;
                return true;
            }
        }

        return false;
    }

    private boolean ensureSticksAvailable(int needed) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        int currentSticks = inv.countItem(Items.STICK);
        if (currentSticks >= needed) return true;

        int sticksDeficit = needed - currentSticks;
        int batchesNeeded = (sticksDeficit + 3) / 4;
        int planksNeeded = batchesNeeded * 2;

        int currentPlanks = countPlanks();
        if (currentPlanks < planksNeeded) {
            int planksDeficit = planksNeeded - currentPlanks;
            int logsNeeded = (planksDeficit + 3) / 4;
            if (hasLogs(logsNeeded)) {
                consumeLogs(logsNeeded);
                inv.addItem(new ItemStack(Items.OAK_PLANKS, logsNeeded * 4));
            }
        }

        if (hasPlanks(planksNeeded)) {
            consumePlanks(planksNeeded);
            inv.addItem(new ItemStack(Items.STICK, batchesNeeded * 4));
            return true;
        }

        return inv.countItem(Items.STICK) >= needed;
    }

    private int countPlanks() {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        int total = 0;
        for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.is(net.minecraft.tags.ItemTags.PLANKS)) total += s.getCount();
        }
        return total;
    }

    private boolean hasLogs(int count) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        int total = 0;
        for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.is(net.minecraft.tags.ItemTags.LOGS)) total += s.getCount();
        }
        return total >= count;
    }

    private void consumeLogs(int count) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        int remaining = count;
        for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS && remaining > 0; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.is(net.minecraft.tags.ItemTags.LOGS)) {
                int take = Math.min(remaining, s.getCount());
                s.shrink(take);
                remaining -= take;
            }
        }
    }

    private boolean hasPlanks(int count) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        int total = 0;
        for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.is(net.minecraft.tags.ItemTags.PLANKS)) total += s.getCount();
        }
        return total >= count;
    }

    private void consumePlanks(int count) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        int remaining = count;
        for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS && remaining > 0; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.is(net.minecraft.tags.ItemTags.PLANKS)) {
                int take = Math.min(remaining, s.getCount());
                s.shrink(take);
                remaining -= take;
            }
        }
    }

    @Override
    public String getDescription() { return "Fabricar " + resultItemId.getPath() + " (" + craftedCount + "/" + amount + ")"; }
    @Override
    public String getProgress() { return complete ? "Terminado" : (craftedCount + "/" + amount); }
    @Override
    public String getTaskType() { return "CRAFT"; }
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("ResultItem", resultItemId.toString());
        tag.putInt("Amount", amount);
        tag.putInt("CraftedCount", craftedCount);
        return tag;
    }
    @Override
    public void load(CompoundTag tag) {
        this.resultItemId = new ResourceLocation(tag.getString("ResultItem"));
        this.amount = tag.getInt("Amount");
        this.craftedCount = tag.getInt("CraftedCount");
    }
}
