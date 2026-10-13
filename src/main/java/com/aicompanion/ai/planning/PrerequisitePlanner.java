package com.aicompanion.ai.planning;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.ai.mining.MiningTarget;
import com.aicompanion.ai.tasks.*;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.inventory.EquipmentManager;
import com.aicompanion.inventory.InventoryRequirementTracker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hierarchical task planner that resolves prerequisites for complex player requests.
 * Evaluates companion inventory state and dynamically generates preliminary tasks
 * (e.g. gather wood -> craft pickaxe -> mine stone -> craft stone pick -> etc.)
 * before executing the requested goal.
 */
public class PrerequisitePlanner {

    private static final ThreadLocal<Set<String>> ACTIVE_PLAN_GOALS = ThreadLocal.withInitial(HashSet::new);
    private static final int MAX_PLANNING_DEPTH = 10;
    private static final ThreadLocal<Integer> PLANNING_DEPTH = ThreadLocal.withInitial(() -> 0);

    /**
     * Resolves all prerequisite tasks needed to fulfill the given goal.
     */
    public static List<CompanionTask> resolvePlanWithPrerequisites(CompanionEntity companion, Goal goal) {
        List<CompanionTask> plan = new ArrayList<>();
        if (companion == null || goal == null) return plan;

        String goalKey = goal.getType().name() + ":" + (goal.getTargetResource() != null ? goal.getTargetResource() : goal.getStringParam());
        Set<String> active = ACTIVE_PLAN_GOALS.get();
        int depth = PLANNING_DEPTH.get();

        if (active.contains(goalKey) || depth >= MAX_PLANNING_DEPTH) {
            com.aicompanion.AICompanionMod.LOGGER.warn("[ESCLAVO-PLANNER] Circular dependency or max depth ({}) detected for goal: {}. Falling back to basic plan.", depth, goalKey);
            return GoalPlanner.plan(companion, goal);
        }

        active.add(goalKey);
        PLANNING_DEPTH.set(depth + 1);

        try {
            switch (goal.getType()) {
            case OBTAIN -> {
                ResourceLocation res = goal.getTargetResource();
                com.aicompanion.ai.gathering.GatherStrategyRegistry.StrategyType strat =
                        com.aicompanion.ai.gathering.GatherStrategyRegistry.determineStrategy(res);

                if (strat == com.aicompanion.ai.gathering.GatherStrategyRegistry.StrategyType.TREE) {
                    int requestedAmount = goal.getAmount() > 0 ? goal.getAmount() : 1;
                    int ownedAxeTier = RecipeDependencyGraph.getHighestOwnedAxeTier(companion);

                    // User tool progression rule:
                    // If requested <= 10 logs: craft Wooden Axe from 2 logs (no pickaxe, no stone).
                    // If requested > 10 logs: craft Stone Axe (craft wooden pickaxe, mine 3 cobblestone, craft stone axe).
                    if (requestedAmount > 1) {
                        if (requestedAmount <= 10) {
                            if (ownedAxeTier < RecipeDependencyGraph.TIER_WOOD) {
                                addAxePrerequisites(companion, plan, requestedAmount);
                            }
                        } else {
                            if (ownedAxeTier < RecipeDependencyGraph.TIER_STONE) {
                                addAxePrerequisites(companion, plan, requestedAmount);
                            }
                        }
                    }
                    plan.addAll(GoalPlanner.plan(companion, goal));
                } else if (strat == com.aicompanion.ai.gathering.GatherStrategyRegistry.StrategyType.CRAFTING) {
                    addCraftingPrerequisites(companion, plan, goal);
                    plan.addAll(GoalPlanner.plan(companion, goal));
                } else if (strat == com.aicompanion.ai.gathering.GatherStrategyRegistry.StrategyType.SMELTING) {
                    addSmeltingPrerequisites(companion, plan, goal);
                } else if (strat == com.aicompanion.ai.gathering.GatherStrategyRegistry.StrategyType.MOB_DROP) {
                    addWeaponPrerequisites(companion, plan);
                    plan.addAll(GoalPlanner.plan(companion, goal));
                } else {
                    MiningTarget miningTarget = MiningTarget.of(res != null ? res.toString() : "stone");
                    int reqTier = RecipeDependencyGraph.getRequiredPickaxeTier(miningTarget);
                    int ownedTier = RecipeDependencyGraph.getHighestOwnedPickaxeTier(companion);

                    // Add tool progression prerequisites if companion lacks required pickaxe tier
                    if (reqTier > RecipeDependencyGraph.TIER_HAND && ownedTier < reqTier) {
                        addPickaxePrerequisites(companion, plan, ownedTier, reqTier);
                    }

                    // Dimensional prerequisites
                    String targetName = res != null ? res.toString() : (goal.getStringParam() != null ? goal.getStringParam() : "");
                    if (com.aicompanion.ai.dimension.DimensionalResourcePolicy.requiresDimensionTravel(companion.level(), targetName)) {
                        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> targetDim =
                                com.aicompanion.ai.dimension.DimensionalResourcePolicy.getRequiredDimension(targetName);
                        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> originDim = companion.level().dimension();
                        addDimensionalPrerequisites(companion, plan, targetDim);

                        List<CompanionTask> mainTasks = GoalPlanner.plan(companion, goal);
                        boolean hasReturn = !mainTasks.isEmpty() && mainTasks.get(mainTasks.size() - 1) instanceof ReturnTask;
                        ReturnTask ret = hasReturn ? (ReturnTask) mainTasks.remove(mainTasks.size() - 1) : null;
                        plan.addAll(mainTasks);
                        // Autonomous return trip to origin dimension
                        plan.add(new DimensionTravelTask(companion, originDim));
                        if (ret != null) {
                            plan.add(ret);
                        }
                    } else {
                        // Add main obtain task
                        plan.addAll(GoalPlanner.plan(companion, goal));
                    }
                }
            }
            case KILL -> {
                addWeaponPrerequisites(companion, plan);
                net.minecraft.resources.ResourceLocation targetMob = goal.getTargetResource();
                String mobName = targetMob != null ? targetMob.toString() : (goal.getStringParam() != null ? goal.getStringParam() : "");
                if (com.aicompanion.ai.dimension.DimensionalResourcePolicy.requiresDimensionTravel(companion.level(), mobName)) {
                    net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> targetDim =
                            com.aicompanion.ai.dimension.DimensionalResourcePolicy.getRequiredDimension(mobName);
                    net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> originDim = companion.level().dimension();
                    addDimensionalPrerequisites(companion, plan, targetDim);

                    List<CompanionTask> mainTasks = GoalPlanner.plan(companion, goal);
                    boolean hasReturn = !mainTasks.isEmpty() && mainTasks.get(mainTasks.size() - 1) instanceof ReturnTask;
                    ReturnTask ret = hasReturn ? (ReturnTask) mainTasks.remove(mainTasks.size() - 1) : null;
                    plan.addAll(mainTasks);
                    plan.add(new DimensionTravelTask(companion, originDim));
                    if (ret != null) {
                        plan.add(ret);
                    }
                } else {
                    plan.addAll(GoalPlanner.plan(companion, goal));
                }
            }
            case BUILD -> {
                String bpPayload = goal.getStringParam() != null ? goal.getStringParam() : "small_house";
                if (com.aicompanion.ai.building.HouseSpec.isEncoded(bpPayload)) {
                    // Confirmed interactive house: exact spec + location chosen by the player
                    net.minecraft.core.BlockPos fixed = goal.getTargetPos();
                    plan.add(new BuildTask(companion, bpPayload, fixed != null ? fixed : companion.blockPosition(), fixed != null));
                    break;
                }
                com.aicompanion.ai.building.BuildingConfig bConfig = com.aicompanion.ai.building.BuildingConfig.deserialize(bpPayload);
                String bpName = bConfig.getBlueprintId();

                com.aicompanion.ai.building.Blueprint sourceBp = com.aicompanion.ai.building.BlueprintRegistry.get(bpName);
                if (sourceBp == null || com.aicompanion.ai.building.BlueprintRegistry.isProcedural(bpName)) {
                    String woodMat = "oak";
                    String stoneMat = "cobblestone";
                    if (!bpPayload.startsWith("cfg:")) {
                        String[] parts = bpPayload.replace(":auto_gather", "").replace(":gather", "").replaceAll(":rot=\\d+", "").split(":");
                        if (parts.length > 1) woodMat = parts[1];
                        if (parts.length > 2) stoneMat = parts[2];
                    }
                    sourceBp = com.aicompanion.ai.building.BlueprintRegistry.getCustom(bpName, woodMat, stoneMat);
                }

                com.aicompanion.ai.building.Blueprint bp = com.aicompanion.ai.building.BlueprintTransformer.transform(sourceBp, bConfig);
                boolean autoGather = bConfig.isAutoGather();

                if (autoGather && bp != null) {
                    java.util.Map<net.minecraft.world.item.Item, Integer> missing =
                            com.aicompanion.ai.building.MaterialAcquisitionPlanner.calculateMissingItems(companion, bp);
                    java.util.List<com.aicompanion.ai.building.MaterialAcquisitionPlanner.MaterialRequirement> reqs =
                            com.aicompanion.ai.building.MaterialAcquisitionPlanner.planRawAcquisitions(missing);

                    for (var req : reqs) {
                        ResourceLocation itemLoc = BuiltInRegistries.ITEM.getKey(req.rawItem());
                        Goal subGoal = Goal.obtain(itemLoc, req.count());
                        plan.addAll(resolvePlanWithPrerequisites(companion, subGoal));
                    }
                }

                plan.add(new BuildTask(companion, bp != null ? bp : com.aicompanion.ai.building.BlueprintRegistry.get(bpName), companion.blockPosition()));
            }
            default -> {
                plan.addAll(GoalPlanner.plan(companion, goal));
            }
            }
        } finally {
            active.remove(goalKey);
            PLANNING_DEPTH.set(depth);
        }

        return plan;
    }

    public static List<CompanionTask> resolveAxePrerequisites(CompanionEntity companion, int requestedAmount) {
        List<CompanionTask> plan = new ArrayList<>();
        if (companion == null) return plan;

        var inv = companion.getInventoryHandler();
        int ownedIron = inv.countItem(Items.IRON_INGOT);
        int ownedCobble = inv.countItem(Items.COBBLESTONE);
        int ownedSticks = inv.countItem(Items.STICK);

        // 1. If companion already has 3 iron ingots in inventory, craft Iron Axe directly
        if (ownedIron >= 3) {
            if (ownedSticks < 2) {
                int planks = InventoryRequirementTracker.countMatching(companion, s -> s.is(net.minecraft.tags.ItemTags.PLANKS));
                if (planks < 2) {
                    plan.add(new ChopTask(companion, "any", 1, true));
                    plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 4));
                }
                plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.STICK), 4));
            }
            plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.IRON_AXE), 1));
            return plan;
        }

        // 2. If companion already has 3 cobblestone in inventory, craft Stone Axe directly
        if (ownedCobble >= 3) {
            if (ownedSticks < 2) {
                int planks = InventoryRequirementTracker.countMatching(companion, s -> s.is(net.minecraft.tags.ItemTags.PLANKS));
                if (planks < 2) {
                    plan.add(new ChopTask(companion, "any", 1, true));
                    plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 4));
                }
                plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.STICK), 4));
            }
            plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.STONE_AXE), 1));
            return plan;
        }

        int totalPlanks = InventoryRequirementTracker.countMatching(companion, s -> s.is(net.minecraft.tags.ItemTags.PLANKS));
        int totalLogs = InventoryRequirementTracker.countWood(companion, "any");

        // 3. Tool progression based on user mandate:
        if (requestedAmount > 10) {
            // For > 10 logs: Craft Stone Axe. To get cobblestone, we need a pickaxe.
            int ownedPickTier = RecipeDependencyGraph.getHighestOwnedPickaxeTier(companion);
            if (ownedPickTier < RecipeDependencyGraph.TIER_WOOD) {
                if (totalPlanks < 5 && totalLogs == 0) {
                    plan.add(new ChopTask(companion, "any", 2, true));
                    plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 8));
                } else if (totalPlanks < 5 && totalLogs > 0) {
                    plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 4));
                }
                if (ownedSticks < 2) {
                    plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.STICK), 4));
                }
                plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.WOODEN_PICKAXE), 1));
            }
            // Mine 3 cobblestone
            plan.add(new MineTask(companion, MiningTarget.of("cobblestone"), 3));
            // Ensure sticks for axe
            if (ownedSticks < 2) {
                plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.STICK), 4));
            }
            // Craft Stone Axe!
            plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.STONE_AXE), 1));
        } else {
            // For <= 10 logs: Craft Wooden Axe quickly from 2 logs (no pickaxe, no stone).
            if (totalPlanks < 5 && totalLogs == 0) {
                plan.add(new ChopTask(companion, "any", 2, true));
                plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 8));
            } else if (totalPlanks < 5 && totalLogs > 0) {
                plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 4));
            }
            if (ownedSticks < 2) {
                plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.STICK), 4));
            }
            plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.WOODEN_AXE), 1));
        }

        return plan;
    }

    public static List<CompanionTask> resolvePickaxePrerequisites(CompanionEntity companion, int ownedTier, int reqTier) {
        List<CompanionTask> plan = new ArrayList<>();
        if (companion == null) return plan;
        var inv = companion.getInventoryHandler();

        // Stage 1: Hand to Wooden Pickaxe (internal crafting)
        if (ownedTier < RecipeDependencyGraph.TIER_WOOD) {
            int planks = InventoryRequirementTracker.countMatching(companion, s -> s.is(net.minecraft.tags.ItemTags.PLANKS));
            int logs = InventoryRequirementTracker.countWood(companion, "any");
            if (planks < 5 && logs == 0) {
                plan.add(new ChopTask(companion, "any", 2, true));
                plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 8));
            } else if (planks < 5 && logs > 0) {
                plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 4));
            }
            if (inv.countItem(Items.STICK) < 2) {
                plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.STICK), 4));
            }
            plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.WOODEN_PICKAXE), 1));
            ownedTier = RecipeDependencyGraph.TIER_WOOD;
        }

        // Stage 2: Wooden Pickaxe to Stone Pickaxe
        if (reqTier >= RecipeDependencyGraph.TIER_STONE && ownedTier < RecipeDependencyGraph.TIER_STONE) {
            int neededCobble = Math.max(0, 3 - inv.countItem(Items.COBBLESTONE));
            if (neededCobble > 0) {
                plan.add(new MineTask(companion, MiningTarget.of("cobblestone"), neededCobble));
            }
            if (inv.countItem(Items.STICK) < 2) {
                plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.STICK), 4));
            }
            plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.STONE_PICKAXE), 1));
            ownedTier = RecipeDependencyGraph.TIER_STONE;
        }

        // Stage 3: Stone Pickaxe to Iron Pickaxe
        if (reqTier >= RecipeDependencyGraph.TIER_IRON && ownedTier < RecipeDependencyGraph.TIER_IRON) {
            int ironIngots = inv.countItem(Items.IRON_INGOT);
            if (ironIngots < 3) {
                int neededIron = 3 - ironIngots;
                int rawIron = inv.countItem(Items.RAW_IRON);
                if (rawIron < neededIron) {
                    // Need a furnace
                    if (inv.countItem(Items.FURNACE) == 0 && inv.countItem(Items.COBBLESTONE) < 8) {
                        plan.add(new MineTask(companion, MiningTarget.of("cobblestone"), 8 - inv.countItem(Items.COBBLESTONE)));
                        plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.FURNACE), 1));
                    } else if (inv.countItem(Items.FURNACE) == 0 && inv.countItem(Items.COBBLESTONE) >= 8) {
                        plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.FURNACE), 1));
                    }

                    // Need fuel
                    if (inv.countItem(Items.COAL) < 2 && inv.countItem(Items.CHARCOAL) < 2) {
                        plan.add(new MineTask(companion, MiningTarget.of("coal_ore"), 2));
                    }

                    // Mine iron ore -> drops raw_iron
                    plan.add(new MineTask(companion, MiningTarget.of("iron_ore"), neededIron - rawIron));
                }

                // Smelt raw iron -> iron ingots
                plan.add(new SmeltTask(companion, new ResourceLocation("minecraft", "raw_iron"), neededIron));
            }

            if (inv.countItem(Items.STICK) < 2) {
                int planks = InventoryRequirementTracker.countMatching(companion, s -> s.is(net.minecraft.tags.ItemTags.PLANKS));
                int logs = InventoryRequirementTracker.countWood(companion, "any");
                if (planks < 2 && logs == 0) {
                    plan.add(new ChopTask(companion, "any", 1, true));
                    plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 4));
                } else if (planks < 2 && logs > 0) {
                    plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 4));
                }
                plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.STICK), 4));
            }
            plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.IRON_PICKAXE), 1));
            ownedTier = RecipeDependencyGraph.TIER_IRON;
        }

        // Stage 4: Iron Pickaxe to Diamond Pickaxe (needed for obsidian/nether portal)
        if (reqTier >= RecipeDependencyGraph.TIER_DIAMOND && ownedTier < RecipeDependencyGraph.TIER_DIAMOND) {
            int diamonds = inv.countItem(Items.DIAMOND);
            if (diamonds < 3) {
                plan.add(new MineTask(companion, MiningTarget.of("diamond_ore"), 3 - diamonds));
            }

            if (inv.countItem(Items.STICK) < 2) {
                int planks = InventoryRequirementTracker.countMatching(companion, s -> s.is(net.minecraft.tags.ItemTags.PLANKS));
                int logs = InventoryRequirementTracker.countWood(companion, "any");
                if (planks < 2 && logs == 0) {
                    plan.add(new ChopTask(companion, "any", 1, true));
                    plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 4));
                } else if (planks < 2 && logs > 0) {
                    plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 4));
                }
                plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.STICK), 4));
            }
            plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.DIAMOND_PICKAXE), 1));
            ownedTier = RecipeDependencyGraph.TIER_DIAMOND;
        }

        return plan;
    }

    private static void addAxePrerequisites(CompanionEntity companion, List<CompanionTask> plan, int requestedAmount) {
        plan.addAll(resolveAxePrerequisites(companion, requestedAmount));
    }

    private static void addPickaxePrerequisites(CompanionEntity companion, List<CompanionTask> plan, int ownedTier, int reqTier) {
        plan.addAll(resolvePickaxePrerequisites(companion, ownedTier, reqTier));
    }

    private static void addWeaponPrerequisites(CompanionEntity companion, List<CompanionTask> plan) {
        plan.addAll(resolveWeaponPrerequisites(companion));
    }

    public static List<CompanionTask> resolveWeaponPrerequisites(CompanionEntity companion) {
        List<CompanionTask> plan = new ArrayList<>();
        if (companion == null) return plan;

        // Check if companion already has a weapon equipped or in inventory
        if (EquipmentManager.hasWeaponEquipped(companion) || EquipmentManager.findBestWeaponSlot(companion) != -1) {
            return plan;
        }

        var inv = companion.getInventoryHandler();
        int ownedIron = inv.countItem(Items.IRON_INGOT);
        int ownedCobble = inv.countItem(Items.COBBLESTONE);
        int ownedSticks = inv.countItem(Items.STICK);
        int totalPlanks = InventoryRequirementTracker.countMatching(companion, s -> s.is(net.minecraft.tags.ItemTags.PLANKS));
        int totalLogs = InventoryRequirementTracker.countWood(companion, "any");

        // 1. If companion has 2+ iron ingots: Craft Iron Sword
        if (ownedIron >= 2) {
            if (ownedSticks < 1) {
                if (totalPlanks < 2 && totalLogs == 0) {
                    plan.add(new ChopTask(companion, "any", 1, true));
                    plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 4));
                } else if (totalPlanks < 2 && totalLogs > 0) {
                    plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 4));
                }
                plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.STICK), 4));
            }
            plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.IRON_SWORD), 1));
            return plan;
        }

        // 2. If companion has 2+ cobblestone: Craft Stone Sword
        if (ownedCobble >= 2) {
            if (ownedSticks < 1) {
                if (totalPlanks < 2 && totalLogs == 0) {
                    plan.add(new ChopTask(companion, "any", 1, true));
                    plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 4));
                } else if (totalPlanks < 2 && totalLogs > 0) {
                    plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 4));
                }
                plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.STICK), 4));
            }
            plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.STONE_SWORD), 1));
            return plan;
        }

        // 3. Otherwise: Craft Wooden Sword (needs 2 planks + 1 stick = 3 planks total = 1 log)
        if (totalPlanks < 3 && totalLogs == 0) {
            plan.add(new ChopTask(companion, "any", 1, true));
            plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 4));
            totalPlanks += 4;
        } else if (totalPlanks < 3 && totalLogs > 0) {
            plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 4));
            totalPlanks += 4;
        }

        if (ownedSticks < 1) {
            plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.STICK), 4));
        }
        plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.WOODEN_SWORD), 1));
        return plan;
    }

    private static void addSmeltingPrerequisites(CompanionEntity companion, List<CompanionTask> plan, Goal goal) {
        ResourceLocation res = goal.getTargetResource();
        ResourceLocation rawSource = com.aicompanion.ai.gathering.GatherStrategyRegistry.getRawSmeltingSource(res);
        int currentSmelted = companion.getInventoryHandler().countItem(BuiltInRegistries.ITEM.get(res));
        int neededSmelted = Math.max(0, goal.getAmount() - currentSmelted);
        if (neededSmelted <= 0) {
            plan.add(new ReturnTask(companion, false));
            return;
        }

        // 1. Furnace Check & Autonomous Crafting
        var inv = companion.getInventoryHandler();
        boolean hasFurnace = inv.countItem(Items.FURNACE) > 0;
        boolean hasNearbyFurnace = false;
        if (companion.level() instanceof net.minecraft.server.level.ServerLevel sl && companion.getMemory() != null) {
            hasNearbyFurnace = companion.getMemory().findClosest(sl, companion.blockPosition(), Blocks.FURNACE, 64.0).isPresent();
        }
        if (!hasNearbyFurnace) {
            for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(
                    companion.blockPosition().offset(-8, -2, -8),
                    companion.blockPosition().offset(8, 2, 8))) {
                if (companion.level().getBlockState(p).is(Blocks.FURNACE)) {
                    hasNearbyFurnace = true;
                    break;
                }
            }
        }

        if (!hasFurnace && !hasNearbyFurnace) {
            int neededCobble = Math.max(0, 8 - inv.countItem(Items.COBBLESTONE));
            if (neededCobble > 0) {
                int ownedPickTier = RecipeDependencyGraph.getHighestOwnedPickaxeTier(companion);
                if (ownedPickTier < RecipeDependencyGraph.TIER_WOOD) {
                    addPickaxePrerequisites(companion, plan, ownedPickTier, RecipeDependencyGraph.TIER_WOOD);
                }
                plan.add(new MineTask(companion, MiningTarget.of("cobblestone"), neededCobble));
            }
            plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.FURNACE), 1));
        }

        int currentRaw = companion.getInventoryHandler().countItem(BuiltInRegistries.ITEM.get(rawSource));
        int neededRaw = Math.max(0, neededSmelted - currentRaw);

        // 1. Tool check for raw material
        if (neededRaw > 0) {
            MiningTarget rawTarget = MiningTarget.of(rawSource.toString());
            int reqTier = RecipeDependencyGraph.getRequiredPickaxeTier(rawTarget);
            int ownedTier = RecipeDependencyGraph.getHighestOwnedPickaxeTier(companion);
            if (reqTier > RecipeDependencyGraph.TIER_HAND && ownedTier < reqTier) {
                addPickaxePrerequisites(companion, plan, ownedTier, reqTier);
            }

            // 2. Gather / mine raw resources
            Goal rawGoal = Goal.obtain(rawSource, neededRaw);
            List<CompanionTask> rawPlan = GoalPlanner.plan(companion, rawGoal);
            for (CompanionTask t : rawPlan) {
                if (!(t instanceof ReturnTask)) {
                    plan.add(t);
                }
            }
        }

        // 3. Fuel check (coal / charcoal / wood)
        int currentCoal = companion.getInventoryHandler().countItem(net.minecraft.world.item.Items.COAL) + companion.getInventoryHandler().countItem(net.minecraft.world.item.Items.CHARCOAL);
        int currentLogs = InventoryRequirementTracker.countWood(companion, "any");
        int currentPlanks = InventoryRequirementTracker.countMatching(companion, s -> s.is(net.minecraft.tags.ItemTags.PLANKS));
        int fuelCapacity = (currentCoal * 8) + (int)(currentLogs * 1.5) + (int)(currentPlanks * 0.75);

        if (fuelCapacity < neededSmelted) {
            int coalNeeded = Math.max(1, (neededSmelted - fuelCapacity + 7) / 8);
            Goal fuelGoal = Goal.obtain(new ResourceLocation("minecraft", "coal"), coalNeeded);
            List<CompanionTask> fuelPlan = GoalPlanner.plan(companion, fuelGoal);
            for (CompanionTask t : fuelPlan) {
                if (!(t instanceof ReturnTask)) {
                    plan.add(t);
                }
            }
        }

        // 4. Smelt task
        plan.add(new SmeltTask(companion, rawSource, neededSmelted));
        plan.add(new ReturnTask(companion, false));
    }

    private static void addDimensionalPrerequisites(CompanionEntity companion, List<CompanionTask> plan, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> targetDim) {
        if (targetDim.equals(net.minecraft.world.level.Level.NETHER)) {
            // Check if there is already an active Nether Portal nearby or in memory
            boolean hasPortal = false;
            if (companion.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                var poi = companion.getMemory().getWorldModel().findClosestPOI(sl, companion.blockPosition(), net.minecraft.world.level.block.Blocks.NETHER_PORTAL, 96.0);
                if (poi.isPresent()) {
                    hasPortal = true;
                }
            }
            if (!hasPortal) {
                net.minecraft.core.BlockPos center = companion.blockPosition();
                for (int dx = -24; dx <= 24 && !hasPortal; dx++) {
                    for (int dy = -6; dy <= 6 && !hasPortal; dy++) {
                        for (int dz = -24; dz <= 24; dz++) {
                            net.minecraft.core.BlockPos p = center.offset(dx, dy, dz);
                            if (companion.level().getBlockState(p).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)) {
                                hasPortal = true;
                                break;
                            }
                        }
                    }
                }
            }

            if (!hasPortal) {
                // Check if companion has 10 obsidian
                int obsidianCount = 0;
                var inv = companion.getInventoryHandler();
                for (int i = 0; i < com.aicompanion.inventory.CompanionInventoryHandler.TOTAL_SLOTS; i++) {
                    var st = inv.getStackInSlot(i);
                    if (st.is(net.minecraft.world.level.block.Blocks.OBSIDIAN.asItem())) {
                        obsidianCount += st.getCount();
                    }
                }

                if (obsidianCount < 10) {
                    int ownedPickTier = RecipeDependencyGraph.getHighestOwnedPickaxeTier(companion);
                    if (ownedPickTier < RecipeDependencyGraph.TIER_DIAMOND) {
                        addPickaxePrerequisites(companion, plan, ownedPickTier, RecipeDependencyGraph.TIER_DIAMOND);
                    }
                    plan.add(new MineTask(companion, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(net.minecraft.world.level.block.Blocks.OBSIDIAN), 10 - obsidianCount));
                }

                // Check flint and steel
                boolean hasIgnition = false;
                for (int i = 0; i < com.aicompanion.inventory.CompanionInventoryHandler.TOTAL_SLOTS; i++) {
                    var st = inv.getStackInSlot(i);
                    if (st.is(net.minecraft.world.item.Items.FLINT_AND_STEEL) || st.is(net.minecraft.world.item.Items.FIRE_CHARGE)) {
                        hasIgnition = true;
                        break;
                    }
                }
                if (!hasIgnition) {
                    plan.add(new CraftTask(companion, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.FLINT_AND_STEEL), 1));
                }

                plan.add(new BuildNetherPortalTask(companion));
            }

            plan.add(new DimensionTravelTask(companion, net.minecraft.world.level.Level.NETHER));
        } else if (targetDim.equals(net.minecraft.world.level.Level.END)) {
            plan.add(new DimensionTravelTask(companion, net.minecraft.world.level.Level.END));
        }
    }

    private static void addCraftingPrerequisites(CompanionEntity companion, List<CompanionTask> plan, Goal goal) {
        if (companion == null || goal == null) return;
        ResourceLocation res = goal.getTargetResource();
        if (res == null) return;
        var inv = companion.getInventoryHandler();
        String path = res.getPath().toLowerCase();

        if (path.equals("stick") || path.equals("sticks")) {
            int neededSticks = goal.getAmount() > 0 ? goal.getAmount() : 4;
            int curSticks = inv.countItem(Items.STICK);
            if (curSticks < neededSticks) {
                int deficit = neededSticks - curSticks;
                int batches = (deficit + 3) / 4;
                int planksNeeded = batches * 2;
                int curPlanks = InventoryRequirementTracker.countMatching(companion, s -> s.is(net.minecraft.tags.ItemTags.PLANKS));
                if (curPlanks < planksNeeded) {
                    int deficitPlanks = planksNeeded - curPlanks;
                    int logsNeeded = (deficitPlanks + 3) / 4;
                    int curLogs = InventoryRequirementTracker.countWood(companion, "any");
                    if (curLogs < logsNeeded) {
                        plan.add(new ChopTask(companion, "any", logsNeeded - curLogs, true));
                    }
                    plan.add(new CraftTask(companion, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), (logsNeeded > 0 ? logsNeeded : 1) * 4));
                }
            }
        } else if (path.contains("planks")) {
            int neededPlanks = goal.getAmount() > 0 ? goal.getAmount() : 4;
            int curPlanks = InventoryRequirementTracker.countMatching(companion, s -> s.is(net.minecraft.tags.ItemTags.PLANKS));
            if (curPlanks < neededPlanks) {
                int deficit = neededPlanks - curPlanks;
                int logsNeeded = (deficit + 3) / 4;
                int curLogs = InventoryRequirementTracker.countWood(companion, "any");
                if (curLogs < logsNeeded) {
                    plan.add(new ChopTask(companion, "any", logsNeeded - curLogs, true));
                }
            }
        } else if (path.equals("netherite_ingot")) {
            int neededIngots = goal.getAmount() > 0 ? goal.getAmount() : 1;
            int curIngots = inv.countItem(Items.NETHERITE_INGOT);
            int deficit = Math.max(0, neededIngots - curIngots);
            if (deficit > 0) {
                int neededScrap = deficit * 4;
                int curScrap = inv.countItem(Items.NETHERITE_SCRAP);
                if (curScrap < neededScrap) {
                    Goal scrapGoal = Goal.obtain(new ResourceLocation("minecraft", "netherite_scrap"), neededScrap - curScrap);
                    plan.addAll(resolvePlanWithPrerequisites(companion, scrapGoal));
                }
                int neededGold = deficit * 4;
                int curGold = inv.countItem(Items.GOLD_INGOT);
                if (curGold < neededGold) {
                    Goal goldGoal = Goal.obtain(new ResourceLocation("minecraft", "gold_ingot"), neededGold - curGold);
                    plan.addAll(resolvePlanWithPrerequisites(companion, goldGoal));
                }
            }
        } else if (path.endsWith("_pickaxe")) {
            MiningTarget target = MiningTarget.of(res.toString());
            int reqTier = RecipeDependencyGraph.getRequiredPickaxeTier(target);
            int ownedTier = RecipeDependencyGraph.getHighestOwnedPickaxeTier(companion);
            if (ownedTier < reqTier) {
                plan.addAll(resolvePickaxePrerequisites(companion, ownedTier, reqTier));
            }
        }
    }
}
