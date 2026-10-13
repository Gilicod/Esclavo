package com.aicompanion.ai.building;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.ai.tasks.BuildTask;
import com.aicompanion.ai.tasks.ChopTask;
import com.aicompanion.ai.tasks.MineTask;
import com.aicompanion.ai.tasks.SmeltTask;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.inventory.CompanionInventoryHandler;
import com.aicompanion.inventory.InventoryRequirementTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Enterprise Material Acquisition Planner for Esclavo (Pilar 7).
 * Analyzes blueprint phases before block placement, calculates exact raw material requirements
 * (converting planks, stairs, fences to raw logs, and stone bricks/stairs to cobblestone),
 * evaluates current inventory stock, and generates prerequisite gathering tasks (ChopTask, MineTask, SmeltTask)
 * to guarantee 100% material availability before construction begins.
 */
public class MaterialAcquisitionPlanner {

    public record MaterialRequirement(Item rawItem, int count, boolean needsSmelting) {}

    public record AcquisitionPlan(
            Blueprint.BuildPhase phase,
            int totalBlocksNeeded,
            int missingLogs,
            int missingStone,
            int missingSand,
            int missingGlassToSmelt,
            int missingDirt,
            Map<Item, Integer> detailedMissing
    ) {
        public boolean is100PercentStocked() {
            return missingLogs <= 0 && missingStone <= 0 && missingSand <= 0 && missingGlassToSmelt <= 0 && missingDirt <= 0 && detailedMissing.isEmpty();
        }

        public List<CompanionTask> generatePrerequisiteTasks(CompanionEntity companion) {
            List<CompanionTask> prereqs = new ArrayList<>();
            if (is100PercentStocked() || companion == null) {
                return prereqs;
            }

            // 1. Gather Wood (ChopTask) if logs are missing
            if (missingLogs > 0) {
                prereqs.add(new ChopTask(companion, "any", missingLogs));
            }

            // 2. Gather Stone / Cobblestone (MineTask) if stone is missing
            if (missingStone > 0) {
                prereqs.add(new MineTask(companion, new ResourceLocation("minecraft", "cobblestone"), missingStone));
            }

            // 3. Gather Dirt (MineTask) if dirt/earth is missing
            if (missingDirt > 0) {
                prereqs.add(new MineTask(companion, new ResourceLocation("minecraft", "dirt"), missingDirt));
            }

            // 4. Gather Sand (MineTask) if sand or glass is missing
            int totalSandNeeded = missingSand + missingGlassToSmelt;
            if (totalSandNeeded > 0) {
                prereqs.add(new MineTask(companion, new ResourceLocation("minecraft", "sand"), totalSandNeeded));
            }

            // 5. Smelt Glass (SmeltTask) if glass is required
            if (missingGlassToSmelt > 0) {
                prereqs.add(new SmeltTask(companion, new ResourceLocation("minecraft", "sand"), missingGlassToSmelt));
            }

            return prereqs;
        }

        public String getMissingReport() {
            if (is100PercentStocked()) {
                return "100% de materiales disponibles.";
            }
            List<String> items = new ArrayList<>();
            if (missingLogs > 0) items.add(missingLogs + " troncos de madera");
            if (missingStone > 0) items.add(missingStone + " piedra/adoquín");
            if (missingDirt > 0) items.add(missingDirt + " tierra");
            if (missingSand > 0) items.add(missingSand + " arena");
            if (missingGlassToSmelt > 0) items.add(missingGlassToSmelt + " cristal (fundir arena)");
            for (Map.Entry<Item, Integer> entry : detailedMissing.entrySet()) {
                if (entry.getValue() > 0) {
                    items.add(entry.getValue() + " " + new ItemStack(entry.getKey()).getHoverName().getString());
                }
            }
            return "Faltan: " + String.join(", ", items);
        }
    }

    /**
     * Evaluates materials needed for an entire blueprint or a specific phase.
     */
    public static AcquisitionPlan planMaterials(CompanionEntity companion, Blueprint blueprint, Blueprint.BuildPhase phase) {
        if (blueprint == null || companion == null) {
            return new AcquisitionPlan(phase, 0, 0, 0, 0, 0, 0, Collections.emptyMap());
        }

        List<Blueprint.BlockEntry> entries = (phase != null)
                ? BuildPlacementPlanner.getBlocksForPhase(blueprint, phase)
                : blueprint.getBlocks();

        return planMaterialsForEntries(companion, entries, phase);
    }

    /**
     * Evaluates materials needed for a specific list of remaining unplaced block entries.
     */
    public static AcquisitionPlan planMaterialsForEntries(CompanionEntity companion, List<Blueprint.BlockEntry> entries, Blueprint.BuildPhase phase) {
        if (entries == null || entries.isEmpty() || companion == null) {
            return new AcquisitionPlan(phase, 0, 0, 0, 0, 0, 0, Collections.emptyMap());
        }

        int requiredDirectLogs = 0;
        double requiredPlanks = 0.0;
        double requiredCobblestone = 0.0;
        int requiredSand = 0;
        int requiredGlass = 0;
        int requiredDirt = 0;
        Map<Item, Integer> otherNeeded = new HashMap<>();

        for (Blueprint.BlockEntry entry : entries) {
            BlockState state = entry.state();
            if (state == null || state.isAir()
                    || SchematicImporter.isTechnicalOrUnobtainable(state)
                    || SchematicImporter.isSecondaryMultiBlockPart(state)) continue;
            if (phase != null && entry.phase() != null && entry.phase() != phase) continue;

            Block block = state.getBlock();
            Item item = block.asItem();

            if (state.is(BlockTags.LOGS)) {
                requiredDirectLogs++;
            } else if (isWoodBlock(block, state)) {
                requiredPlanks += calculatePlankEquivalent(block, state);
            } else if (isStoneBlock(block, state)) {
                requiredCobblestone += calculateCobbleEquivalent(block, state);
            } else if (isDirtBlock(block, state)) {
                requiredDirt++;
            } else if (block == Blocks.GLASS || block == Blocks.GLASS_PANE) {
                requiredGlass += (block == Blocks.GLASS_PANE ? 1 : 1);
            } else if (block == Blocks.SAND || block == Blocks.SANDSTONE) {
                requiredSand += (block == Blocks.SANDSTONE ? 4 : 1);
            } else if (item != Items.AIR) {
                if (entry.phase() != Blueprint.BuildPhase.FURNISHING && !SchematicImporter.isOptionalFurnishing(state)) {
                    otherNeeded.put(item, otherNeeded.getOrDefault(item, 0) + 1);
                }
            }
        }

        int totalPlanksNeeded = (int) Math.ceil(requiredPlanks);
        int totalCobbleNeeded = (int) Math.ceil(requiredCobblestone);

        // Cross-reference against companion inventory
        CompanionInventoryHandler inv = companion.getInventoryHandler();

        // 1. Available wood stock: direct logs must come from actual logs; leftover logs * 4 + existing planks satisfy plank items
        int existingLogs = InventoryRequirementTracker.countWood(companion, "any");
        int existingPlanks = countPlanksInInventory(companion);

        int missingDirectLogs = Math.max(0, requiredDirectLogs - existingLogs);
        int leftoverLogsForPlanks = Math.max(0, existingLogs - requiredDirectLogs);
        int totalAvailablePlanks = (leftoverLogsForPlanks * 4) + existingPlanks;

        int missingPlanks = Math.max(0, totalPlanksNeeded - totalAvailablePlanks);
        int missingLogsForPlanks = (missingPlanks > 0) ? (int) Math.ceil(missingPlanks / 4.0) : 0;
        int missingLogs = missingDirectLogs + missingLogsForPlanks;

        // 2. Available stone stock (cobblestone + stone + stone bricks + cobbled deepslate)
        int existingStone = inv.countItem(Items.COBBLESTONE) + inv.countItem(Items.STONE)
                + inv.countItem(Items.STONE_BRICKS) + inv.countItem(Items.DEEPSLATE)
                + inv.countItem(Items.COBBLED_DEEPSLATE);
        int missingStone = Math.max(0, totalCobbleNeeded - existingStone);

        // 3. Available dirt stock
        int existingDirt = inv.countItem(Items.DIRT) + inv.countItem(Items.GRASS_BLOCK)
                + inv.countItem(Items.COARSE_DIRT) + inv.countItem(Items.ROOTED_DIRT)
                + inv.countItem(Items.DIRT_PATH) + inv.countItem(Items.FARMLAND);
        int missingDirt = Math.max(0, requiredDirt - existingDirt);

        // 4. Available glass and sand
        int existingGlass = inv.countItem(Items.GLASS) + (inv.countItem(Items.GLASS_PANE) / 2);
        int missingGlass = Math.max(0, requiredGlass - existingGlass);

        int existingSand = inv.countItem(Items.SAND);
        int missingSand = Math.max(0, requiredSand - existingSand);

        // Glass to smelt: if we don't have enough glass, we need sand to smelt
        int missingGlassToSmelt = missingGlass;

        // 5. Other missing items (excluding optional furnishings)
        Map<Item, Integer> detailedMissing = new HashMap<>();
        if (phase != Blueprint.BuildPhase.FURNISHING) {
            for (Map.Entry<Item, Integer> entry : otherNeeded.entrySet()) {
                int owned = inv.countItem(entry.getKey());
                if (owned < entry.getValue()) {
                    detailedMissing.put(entry.getKey(), entry.getValue() - owned);
                }
            }
        }

        return new AcquisitionPlan(
                phase,
                entries.size(),
                missingLogs,
                missingStone,
                missingSand,
                missingGlassToSmelt,
                missingDirt,
                detailedMissing
        );
    }

    /**
     * Legacy bridge: Calculates directly missing items from a blueprint without resource breakdown.
     */
    public static Map<Item, Integer> calculateMissingItems(CompanionEntity companion, Blueprint bp) {
        if (companion == null || bp == null) return Collections.emptyMap();
        Map<Item, Integer> needed = new HashMap<>();
        for (Blueprint.BlockEntry entry : bp.getBlocks()) {
            BlockState st = entry.state();
            if (st == null || st.isAir()
                    || SchematicImporter.isTechnicalOrUnobtainable(st)
                    || SchematicImporter.isSecondaryMultiBlockPart(st)) continue;
            Item item = st.getBlock().asItem();
            if (item != Items.AIR) {
                needed.put(item, needed.getOrDefault(item, 0) + 1);
            }
        }
        Map<Item, Integer> missing = new HashMap<>();
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
            int owned = inv.countItem(entry.getKey());
            if (owned < entry.getValue()) {
                missing.put(entry.getKey(), entry.getValue() - owned);
            }
        }
        return missing;
    }

    /**
     * Legacy bridge: Translates raw item missing map into planned raw material requirements.
     */
    public static List<MaterialRequirement> planRawAcquisitions(Map<Item, Integer> missing) {
        List<MaterialRequirement> reqs = new ArrayList<>();
        if (missing == null || missing.isEmpty()) return reqs;

        int logsNeeded = 0;
        int cobbleNeeded = 0;
        int sandNeeded = 0;

        for (Map.Entry<Item, Integer> entry : missing.entrySet()) {
            Item item = entry.getKey();
            int count = entry.getValue();

            Block block = Block.byItem(item);
            BlockState state = block.defaultBlockState();

            if (isWoodBlock(block, state)) {
                logsNeeded += (int) Math.ceil((count * calculatePlankEquivalent(block, state)) / 4.0);
            } else if (isStoneBlock(block, state)) {
                cobbleNeeded += count;
            } else if (item == Items.GLASS || item == Items.GLASS_PANE) {
                sandNeeded += count;
            } else {
                reqs.add(new MaterialRequirement(item, count, false));
            }
        }

        if (logsNeeded > 0) reqs.add(new MaterialRequirement(Items.OAK_LOG, logsNeeded, false));
        if (cobbleNeeded > 0) reqs.add(new MaterialRequirement(Items.COBBLESTONE, cobbleNeeded, false));
        if (sandNeeded > 0) reqs.add(new MaterialRequirement(Items.SAND, sandNeeded, true));

        return reqs;
    }

    public static void planAndEnqueueFullConstruction(CompanionEntity companion, Blueprint bp, BlockPos originPos) {
        planAndEnqueueFullConstruction(companion, bp, originPos, null);
    }

    /**
     * Plans and enqueues full construction:
     * Audits 100% of required materials; if deficient, enqueues gathering prerequisites before BuildTask.
     */
    public static void planAndEnqueueFullConstruction(CompanionEntity companion, Blueprint bp, BlockPos originPos, BuildingConfig config) {
        if (companion == null || bp == null) return;

        AcquisitionPlan plan = planMaterials(companion, bp, null);
        BuildTask buildTask = new BuildTask(companion, bp, originPos, config);

        if (!plan.is100PercentStocked()) {
            List<CompanionTask> prereqs = plan.generatePrerequisiteTasks(companion);
            if (!prereqs.isEmpty()) {
                if (companion.getOwnerPlayer() != null) {
                    companion.getOwnerPlayer().sendSystemMessage(
                            Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                    .withStyle(net.minecraft.ChatFormatting.GOLD)
                                    .append(Component.literal("Planificando construcción de " + bp.getName() + ": " + plan.getMissingReport() + ". Iniciando recolección previa...")
                                            .withStyle(net.minecraft.ChatFormatting.AQUA))
                    );
                }
                companion.getCompanionBrain().getTaskManager().setTask(buildTask); // checkPreconditions will automatically handle pushing prereqs
                return;
            }
        }

        companion.getCompanionBrain().getTaskManager().setTask(buildTask);
    }

    private static boolean isWoodBlock(Block block, BlockState state) {
        return state.is(BlockTags.PLANKS)
                || state.is(BlockTags.WOODEN_STAIRS)
                || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.WOODEN_FENCES)
                || block instanceof net.minecraft.world.level.block.FenceGateBlock
                || state.is(BlockTags.WOODEN_DOORS)
                || state.is(BlockTags.WOODEN_TRAPDOORS)
                || state.is(BlockTags.WOODEN_PRESSURE_PLATES)
                || state.is(BlockTags.WOODEN_BUTTONS)
                || state.is(BlockTags.ALL_SIGNS)
                || state.is(BlockTags.BEDS)
                || state.is(BlockTags.LOGS)
                || block == Blocks.CHEST
                || block == Blocks.BARREL
                || block == Blocks.CRAFTING_TABLE;
    }

    private static double calculatePlankEquivalent(Block block, BlockState state) {
        if (state.is(BlockTags.LOGS)) return 4.0;
        if (block == Blocks.CHEST || block == Blocks.BARREL) return 8.0;
        if (block == Blocks.CRAFTING_TABLE) return 4.0;
        if (state.is(BlockTags.WOODEN_STAIRS)) return 1.5;
        if (state.is(BlockTags.WOODEN_SLABS)) return 0.5;
        if (state.is(BlockTags.WOODEN_TRAPDOORS)) return 3.0;
        if (state.is(BlockTags.WOODEN_DOORS)) return 6.0;
        if (state.is(BlockTags.BEDS)) return 3.0;
        if (state.is(BlockTags.ALL_SIGNS)) return 2.5;
        if (state.is(BlockTags.WOODEN_FENCES)) return 1.67;
        if (block instanceof net.minecraft.world.level.block.FenceGateBlock) return 4.0;
        if (state.is(BlockTags.WOODEN_PRESSURE_PLATES)) return 2.0;
        return 1.0;
    }

    private static boolean isStoneBlock(Block block, BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.STONE_BRICKS)
                || state.is(BlockTags.STAIRS)
                || state.is(BlockTags.SLABS)
                || state.is(BlockTags.WALLS)
                || block == Blocks.COBBLESTONE
                || block == Blocks.STONE
                || block == Blocks.STONE_BRICKS
                || block == Blocks.DEEPSLATE
                || block == Blocks.COBBLED_DEEPSLATE;
    }

    private static boolean isDirtBlock(Block block, BlockState state) {
        return block == Blocks.DIRT || block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT
                || block == Blocks.GRASS_BLOCK || block == Blocks.PODZOL || block == Blocks.MYCELIUM
                || block == Blocks.MUD || block == Blocks.DIRT_PATH || block == Blocks.FARMLAND;
    }

    private static double calculateCobbleEquivalent(Block block, BlockState state) {
        if (state.is(BlockTags.STAIRS)) return 1.5;
        if (state.is(BlockTags.SLABS)) return 0.5;
        if (state.is(BlockTags.WALLS)) return 1.0;
        return 1.0;
    }

    private static int countPlanksInInventory(CompanionEntity companion) {
        if (companion == null) return 0;
        return InventoryRequirementTracker.countMatching(companion, stack -> stack.is(ItemTags.PLANKS));
    }
}
