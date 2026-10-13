package com.aicompanion.inventory;

import com.aicompanion.entity.CompanionEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;

/**
 * Heuristic Item Valuation Engine for Esclavo (Pilar 6).
 * Classifies inventory items into strict valuation tiers to drive autonomous
 * discard economics (dropping trash like diorite, dirt, seeds, and modded junk stones)
 * while guaranteeing tools, armor, food, and quest deliverables are immutable.
 */
public class ItemHeuristicValue {

    public enum PriorityCategory {
        IMMUTABLE(100_000),      // Never discard: Tools, Weapons, Armor, Food, Precious Gems, Netherracks
        HIGH_VALUE(1_000),       // Ores, Raw metals, Torches, Workstations, Logs, Machinery
        MEDIUM_VALUE(100),       // Planks, Sticks, Cobblestone, Coal, Common building blocks
        LOW_VALUE(10),           // Saplings, Feathers, Flint, Sticks
        DISPOSABLE_TRASH(0);     // Andesite, Diorite, Granite, Tuff, Dirt, Gravel, Seeds, Rotten Flesh, Modded junk geology

        final int weight;
        PriorityCategory(int weight) {
            this.weight = weight;
        }

        public int getWeight() {
            return weight;
        }
    }

    private static final TagKey<Item> FORGE_TOOLS = ItemTags.create(new ResourceLocation("forge", "tools"));
    private static final TagKey<Item> FORGE_ARMORS = ItemTags.create(new ResourceLocation("forge", "armors"));
    private static final TagKey<Item> FORGE_SHIELDS = ItemTags.create(new ResourceLocation("forge", "shields"));
    private static final TagKey<Item> FORGE_ORES = ItemTags.create(new ResourceLocation("forge", "ores"));
    private static final TagKey<Item> FORGE_RAW_MATERIALS = ItemTags.create(new ResourceLocation("forge", "raw_materials"));
    private static final TagKey<Item> FORGE_INGOTS = ItemTags.create(new ResourceLocation("forge", "ingots"));
    private static final TagKey<Item> FORGE_GEMS = ItemTags.create(new ResourceLocation("forge", "gems"));
    private static final TagKey<Item> FORGE_COBBLESTONE = ItemTags.create(new ResourceLocation("forge", "cobblestone"));
    private static final TagKey<Item> FORGE_STONE = ItemTags.create(new ResourceLocation("forge", "stone"));
    private static final TagKey<Item> FORGE_GRAVEL = ItemTags.create(new ResourceLocation("forge", "gravel"));
    private static final TagKey<Item> FORGE_DIRT = ItemTags.create(new ResourceLocation("forge", "dirt"));
    private static final TagKey<Item> FORGE_SAND = ItemTags.create(new ResourceLocation("forge", "sand"));

    public static PriorityCategory getCategory(ItemStack stack, CompanionEntity companion) {
        if (stack == null || stack.isEmpty()) return PriorityCategory.DISPOSABLE_TRASH;
        Item item = stack.getItem();
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        String path = key.getPath().toLowerCase();

        // 0. IMMUTABLE: Custom named items, enchanted items, or mission deliverables
        if (stack.hasCustomHoverName() || stack.isEnchanted() || isMissionProtected(stack, companion)) {
            return PriorityCategory.IMMUTABLE;
        }

        // 1. IMMUTABLE: Equipment, Weapons, Armor, Tools, Shields, Shears, Fishing Rods, Flint & Steel
        if (item instanceof TieredItem || item instanceof ArmorItem || item instanceof BowItem ||
            item instanceof CrossbowItem || item instanceof ShieldItem || item instanceof ShearsItem ||
            item instanceof FishingRodItem || item instanceof FlintAndSteelItem ||
            stack.is(FORGE_TOOLS) || stack.is(FORGE_ARMORS) || stack.is(FORGE_SHIELDS)) {
            return PriorityCategory.IMMUTABLE;
        }

        // 2. IMMUTABLE: Food & Healing Items
        if (item.isEdible() || item == Items.POTION || item == Items.SPLASH_POTION ||
            item == Items.LINGERING_POTION || item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE) {
            return PriorityCategory.IMMUTABLE;
        }

        // 3. IMMUTABLE: Rare & Precious Valuables & Dimensional Travel Items
        if (item == Items.DIAMOND || item == Items.NETHERITE_INGOT || item == Items.NETHERITE_SCRAP ||
            item == Items.ANCIENT_DEBRIS || item == Items.EMERALD || item == Items.TOTEM_OF_UNDYING ||
            item == Items.ENDER_PEARL || item == Items.BLAZE_ROD || item == Items.ENCHANTED_BOOK ||
            item == Items.EXPERIENCE_BOTTLE || item == Items.NAME_TAG || item == Items.SADDLE ||
            item == Items.LEAD || item == Items.CLOCK || item == Items.COMPASS || item == Items.RECOVERY_COMPASS ||
            item == Items.OBSIDIAN || item == Items.CRYING_OBSIDIAN || item == Items.ENDER_EYE ||
            item == Items.NETHER_STAR || item == Items.BEACON || item == Items.ELYTRA) {
            return PriorityCategory.IMMUTABLE;
        }

        // 3b. Modded Curios, Backpacks, Storage, Relics, Rings, Charms
        if (path.contains("curio") || path.contains("charm") || path.contains("ring") ||
            path.contains("amulet") || path.contains("backpack") || path.contains("shulker") ||
            path.contains("pouch") || path.contains("relic") || path.contains("artifact") ||
            path.contains("tome") || path.contains("spell_book")) {
            return PriorityCategory.IMMUTABLE;
        }

        // 4. HIGH VALUE: Ores, Raw metals, Mineral storage, Workstations, Logs, Torches, Machinery
        if (stack.is(ItemTags.COALS) || stack.is(ItemTags.IRON_ORES) || stack.is(ItemTags.GOLD_ORES) ||
            stack.is(ItemTags.DIAMOND_ORES) || stack.is(ItemTags.EMERALD_ORES) || stack.is(ItemTags.COPPER_ORES) ||
            stack.is(ItemTags.REDSTONE_ORES) || stack.is(ItemTags.LAPIS_ORES) ||
            stack.is(FORGE_ORES) || stack.is(FORGE_RAW_MATERIALS) || stack.is(FORGE_INGOTS) || stack.is(FORGE_GEMS) ||
            item == Items.RAW_IRON || item == Items.RAW_GOLD || item == Items.RAW_COPPER ||
            item == Items.IRON_INGOT || item == Items.GOLD_INGOT || item == Items.COPPER_INGOT ||
            item == Items.REDSTONE || item == Items.LAPIS_LAZULI || item == Items.QUARTZ || item == Items.AMETHYST_SHARD ||
            stack.is(ItemTags.LOGS) ||
            item == Items.CRAFTING_TABLE || item == Items.FURNACE || item == Items.BLAST_FURNACE ||
            item == Items.SMOKER || item == Items.CHEST || item == Items.BARREL || item == Items.TORCH ||
            item == Items.ANVIL || item == Items.CHIPPED_ANVIL || item == Items.DAMAGED_ANVIL ||
            item == Items.SMITHING_TABLE || item == Items.STONECUTTER || item == Items.BREWING_STAND) {
            return PriorityCategory.HIGH_VALUE;
        }

        // 4b. Modded Tech / Machinery / Components (Create cogs, shafts, gears, pipes, circuits)
        if (path.contains("circuit") || path.contains("gear") || path.contains("mechanism") ||
            path.contains("engine") || path.contains("wrench") || path.contains("battery") ||
            path.contains("machine") || path.contains("motor") || path.contains("shaft") ||
            path.contains("cogwheel") || path.contains("electron_tube")) {
            return PriorityCategory.HIGH_VALUE;
        }

        // 5. DISPOSABLE TRASH: Junk geology & basic mob waste (first to be purged when full)
        // Dirt, sand, gravel and essential scaffolding are strictly excluded from trash!
        if (item == Items.ANDESITE || item == Items.DIORITE || item == Items.GRANITE || item == Items.TUFF ||
            item == Items.CALCITE || item == Items.DRIPSTONE_BLOCK || item == Items.POINTED_DRIPSTONE ||
            item == Items.WHEAT_SEEDS || item == Items.BEETROOT_SEEDS || item == Items.PUMPKIN_SEEDS ||
            item == Items.MELON_SEEDS || item == Items.TORCHFLOWER_SEEDS || item == Items.PITCHER_POD ||
            item == Items.POISONOUS_POTATO || item == Items.SPIDER_EYE) {
            return PriorityCategory.DISPOSABLE_TRASH;
        }

        // Modded geology waste (Create, Quark, Chipped stones & cut/polished variations)
        if (isModdedJunkGeology(path)) {
            return PriorityCategory.DISPOSABLE_TRASH;
        }

        // 5b. Rotten flesh (discardable if allowed)
        if (item == Items.ROTTEN_FLESH || path.contains("rotten_flesh")) {
            if (com.aicompanion.AICompanionConfig.COMMON.allowDiscardRottenFlesh.get()) {
                return PriorityCategory.DISPOSABLE_TRASH;
            }
            return PriorityCategory.LOW_VALUE;
        }

        // 6. LOW VALUE: Easily replenishable secondary items (Feathers, excess Flint, gravel, saplings, flowers)
        if (item == Items.FEATHER || item == Items.FLINT || item == Items.GRAVEL || stack.is(FORGE_GRAVEL) ||
            path.contains("sapling") || path.contains("flower") || path.contains("leaves") ||
            stack.is(ItemTags.SAPLINGS) || stack.is(ItemTags.FLOWERS) || stack.is(ItemTags.LEAVES) ||
            item == Items.BONE_MEAL || item == Items.SNOWBALL) {
            return PriorityCategory.LOW_VALUE;
        }

        // 6b. Bones and String (Configurable - NEVER trash unless explicitly enabled)
        if (item == Items.BONE || path.contains("bone")) {
            if (com.aicompanion.AICompanionConfig.COMMON.allowDiscardBones.get()) {
                return PriorityCategory.LOW_VALUE;
            }
            return PriorityCategory.MEDIUM_VALUE;
        }
        if (item == Items.STRING || path.contains("string")) {
            if (com.aicompanion.AICompanionConfig.COMMON.allowDiscardString.get()) {
                return PriorityCategory.LOW_VALUE;
            }
            return PriorityCategory.MEDIUM_VALUE;
        }

        // 7. MEDIUM VALUE: Basic craft building supplies (Dirt, Sand, Planks, Sticks, Cobblestone, Cobbled Deepslate, Netherrack, Stone, Deepslate, Basalt, Blackstone)
        if (stack.is(ItemTags.PLANKS) || item == Items.STICK || item == Items.COBBLESTONE ||
            item == Items.COBBLED_DEEPSLATE || item == Items.NETHERRACK || item == Items.STONE ||
            item == Items.DEEPSLATE || item == Items.BASALT || item == Items.SMOOTH_BASALT ||
            item == Items.BLACKSTONE ||
            item == Items.DIRT || item == Items.COARSE_DIRT || item == Items.ROOTED_DIRT || item == Items.MUD ||
            item == Items.SAND || item == Items.RED_SAND ||
            stack.is(ItemTags.DIRT) || stack.is(ItemTags.SAND) ||
            stack.is(FORGE_DIRT) || stack.is(FORGE_SAND) ||
            stack.is(FORGE_COBBLESTONE) || stack.is(FORGE_STONE) ||
            path.contains("cobblestone") || path.contains("cobbled_") || path.contains("dirt") || path.contains("sand")) {
            return PriorityCategory.MEDIUM_VALUE;
        }

        // 8. Default for unknown items: HIGH_VALUE (Safe against accidental discard, but not IMMUTABLE)
        return PriorityCategory.HIGH_VALUE;
    }

    private static boolean isModdedJunkGeology(String path) {
        if (path.contains("ore") || path.contains("raw_") || path.contains("ingot") ||
            path.contains("gem") || path.contains("dust") || path.contains("nugget")) {
            return false;
        }

        // Create stones
        if (path.contains("limestone") || path.contains("scoria") || path.contains("scorchia") ||
            path.contains("crimsite") || path.contains("asurine") || path.contains("veridium") ||
            path.contains("ochrum")) {
            return true;
        }

        // Quark stones
        if (path.contains("jasper") || path.contains("shale") || path.contains("permafrost") ||
            path.contains("corundum") || path.contains("myalite")) {
            return true;
        }

        // Common geology paths & cut/polished variants from Chipped/Rechiseled/Create
        if (path.contains("diorite") || path.contains("granite") || path.contains("andesite") ||
            path.contains("tuff") || path.contains("calcite") || path.contains("dripstone") ||
            path.contains("gabbro") || path.contains("pebble") || path.contains("rock")) {
            return true;
        }

        // Generic seeds
        if (path.endsWith("_seeds") || path.equals("seeds")) {
            return true;
        }

        return false;
    }

    public static boolean isMissionProtected(ItemStack stack, CompanionEntity companion) {
        if (stack == null || stack.isEmpty() || companion == null) return false;
        if (companion.getCompanionBrain() == null) return false;

        // Check active task description / targets
        var taskManager = companion.getCompanionBrain().getTaskManager();
        if (taskManager != null && taskManager.getCurrentTask() != null) {
            var current = taskManager.getCurrentTask();
            if (current instanceof com.aicompanion.ai.tasks.MineTask mineTask) {
                if (mineTask.getMiningTarget() != null && mineTask.getMiningTarget().isTargetDrop(stack)) {
                    return true;
                }
            }
            if (current instanceof com.aicompanion.ai.tasks.ChopTask) {
                if (stack.is(ItemTags.LOGS)) return true;
            }
            if (current instanceof com.aicompanion.ai.tasks.BuildTask buildTask) {
                if (buildTask.requiresItem(stack.getItem())) return true;
            }
        }

        // Check queued tasks (e.g. if a BuildTask is queued and child gathering tasks are running)
        if (taskManager != null && taskManager.getTaskQueue() != null) {
            for (var task : taskManager.getTaskQueue().getTasks()) {
                if (task instanceof com.aicompanion.ai.tasks.BuildTask buildTask) {
                    if (buildTask.requiresItem(stack.getItem())) return true;
                }
            }
        }

        // Check root mission goals
        var goals = companion.getCompanionBrain().getLastUserGoals();
        if (goals != null) {
            ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
            for (com.aicompanion.ai.planning.Goal g : goals) {
                if (g != null && g.getTargetResource() != null) {
                    if (g.getTargetResource().equals(itemKey) || g.getTargetResource().getPath().equalsIgnoreCase(itemKey.getPath())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static boolean isTrash(ItemStack stack, CompanionEntity companion) {
        return getCategory(stack, companion) == PriorityCategory.DISPOSABLE_TRASH;
    }

    public static boolean isImmutable(ItemStack stack, CompanionEntity companion) {
        return getCategory(stack, companion) == PriorityCategory.IMMUTABLE;
    }

    public static boolean isLowValue(ItemStack stack, CompanionEntity companion) {
        return getCategory(stack, companion) == PriorityCategory.LOW_VALUE;
    }

    public static boolean isMediumValue(ItemStack stack, CompanionEntity companion) {
        return getCategory(stack, companion) == PriorityCategory.MEDIUM_VALUE;
    }
}
