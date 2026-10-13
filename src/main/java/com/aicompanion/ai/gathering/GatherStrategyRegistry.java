package com.aicompanion.ai.gathering;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;

public class GatherStrategyRegistry {

    public enum StrategyType {
        GROUND_ITEM,
        TREE,
        BLOCK_MINE,
        MOB_DROP,
        CRAFTING,
        SMELTING
    }

    private static final Map<String, StrategyType> STRATEGIES = new HashMap<>();

    static {
        // Logs and woods -> TREE
        STRATEGIES.put("oak_log", StrategyType.TREE);
        STRATEGIES.put("birch_log", StrategyType.TREE);
        STRATEGIES.put("spruce_log", StrategyType.TREE);
        STRATEGIES.put("jungle_log", StrategyType.TREE);
        STRATEGIES.put("acacia_log", StrategyType.TREE);
        STRATEGIES.put("dark_oak_log", StrategyType.TREE);
        STRATEGIES.put("mangrove_log", StrategyType.TREE);
        STRATEGIES.put("cherry_log", StrategyType.TREE);

        // Ores / stone / sand -> BLOCK_MINE
        STRATEGIES.put("cobblestone", StrategyType.BLOCK_MINE);
        STRATEGIES.put("stone", StrategyType.BLOCK_MINE);
        STRATEGIES.put("deepslate", StrategyType.BLOCK_MINE);
        STRATEGIES.put("coal", StrategyType.BLOCK_MINE);
        STRATEGIES.put("raw_iron", StrategyType.BLOCK_MINE);
        STRATEGIES.put("iron_ore", StrategyType.BLOCK_MINE);
        STRATEGIES.put("raw_gold", StrategyType.BLOCK_MINE);
        STRATEGIES.put("gold_ore", StrategyType.BLOCK_MINE);
        STRATEGIES.put("diamond", StrategyType.BLOCK_MINE);
        STRATEGIES.put("diamond_ore", StrategyType.BLOCK_MINE);
        STRATEGIES.put("emerald", StrategyType.BLOCK_MINE);
        STRATEGIES.put("emerald_ore", StrategyType.BLOCK_MINE);
        STRATEGIES.put("raw_copper", StrategyType.BLOCK_MINE);
        STRATEGIES.put("copper_ore", StrategyType.BLOCK_MINE);
        STRATEGIES.put("redstone", StrategyType.BLOCK_MINE);
        STRATEGIES.put("lapis_lazuli", StrategyType.BLOCK_MINE);
        STRATEGIES.put("sand", StrategyType.BLOCK_MINE);
        STRATEGIES.put("gravel", StrategyType.BLOCK_MINE);

        // Mob drops -> MOB_DROP
        STRATEGIES.put("rotten_flesh", StrategyType.MOB_DROP);
        STRATEGIES.put("bone", StrategyType.MOB_DROP);
        STRATEGIES.put("gunpowder", StrategyType.MOB_DROP);
        STRATEGIES.put("string", StrategyType.MOB_DROP);
        STRATEGIES.put("spider_eye", StrategyType.MOB_DROP);
        STRATEGIES.put("ender_pearl", StrategyType.MOB_DROP);
        STRATEGIES.put("ink_sac", StrategyType.MOB_DROP);
        STRATEGIES.put("glow_ink_sac", StrategyType.MOB_DROP);
        STRATEGIES.put("leather", StrategyType.MOB_DROP);
        STRATEGIES.put("feather", StrategyType.MOB_DROP);
        STRATEGIES.put("beef", StrategyType.MOB_DROP);
        STRATEGIES.put("cooked_beef", StrategyType.MOB_DROP);
        STRATEGIES.put("porkchop", StrategyType.MOB_DROP);
        STRATEGIES.put("cooked_porkchop", StrategyType.MOB_DROP);
        STRATEGIES.put("mutton", StrategyType.MOB_DROP);
        STRATEGIES.put("cooked_mutton", StrategyType.MOB_DROP);
        STRATEGIES.put("chicken", StrategyType.MOB_DROP);
        STRATEGIES.put("cooked_chicken", StrategyType.MOB_DROP);
        STRATEGIES.put("blaze_rod", StrategyType.MOB_DROP);
        STRATEGIES.put("slime_ball", StrategyType.MOB_DROP);
        STRATEGIES.put("white_wool", StrategyType.MOB_DROP);
        STRATEGIES.put("wool", StrategyType.MOB_DROP);

        // Blocks & Minerals
        STRATEGIES.put("obsidian", StrategyType.BLOCK_MINE);
        STRATEGIES.put("ancient_debris", StrategyType.BLOCK_MINE);
        STRATEGIES.put("netherite", StrategyType.BLOCK_MINE);
        STRATEGIES.put("netherita", StrategyType.BLOCK_MINE);
        STRATEGIES.put("netherite_scrap", StrategyType.SMELTING);
        STRATEGIES.put("netherite_ingot", StrategyType.CRAFTING);
        STRATEGIES.put("clay", StrategyType.BLOCK_MINE);
        STRATEGIES.put("dirt", StrategyType.BLOCK_MINE);
        STRATEGIES.put("grass_block", StrategyType.BLOCK_MINE);
        STRATEGIES.put("netherrack", StrategyType.BLOCK_MINE);
        STRATEGIES.put("nether_quartz_ore", StrategyType.BLOCK_MINE);
        STRATEGIES.put("quartz", StrategyType.BLOCK_MINE);

        // Plant & Crops -> GROUND_ITEM
        STRATEGIES.put("wheat_seeds", StrategyType.GROUND_ITEM);
        STRATEGIES.put("wheat", StrategyType.GROUND_ITEM);
        STRATEGIES.put("carrot", StrategyType.GROUND_ITEM);
        STRATEGIES.put("potato", StrategyType.GROUND_ITEM);
        STRATEGIES.put("apple", StrategyType.GROUND_ITEM);
        STRATEGIES.put("dandelion", StrategyType.GROUND_ITEM);
        STRATEGIES.put("poppy", StrategyType.GROUND_ITEM);
        STRATEGIES.put("blue_orchid", StrategyType.GROUND_ITEM);
        STRATEGIES.put("allium", StrategyType.GROUND_ITEM);
        STRATEGIES.put("sunflower", StrategyType.GROUND_ITEM);
        STRATEGIES.put("sugar_cane", StrategyType.GROUND_ITEM);
        STRATEGIES.put("bamboo", StrategyType.GROUND_ITEM);

        // Smeltables -> SMELTING
        STRATEGIES.put("iron_ingot", StrategyType.SMELTING);
        STRATEGIES.put("gold_ingot", StrategyType.SMELTING);
        STRATEGIES.put("copper_ingot", StrategyType.SMELTING);
        STRATEGIES.put("smooth_stone", StrategyType.SMELTING);
        STRATEGIES.put("glass", StrategyType.SMELTING);
        STRATEGIES.put("charcoal", StrategyType.SMELTING);
        STRATEGIES.put("terracotta", StrategyType.SMELTING);
        STRATEGIES.put("cooked_beef", StrategyType.SMELTING);
        STRATEGIES.put("cooked_porkchop", StrategyType.SMELTING);
        STRATEGIES.put("cooked_mutton", StrategyType.SMELTING);
        STRATEGIES.put("cooked_chicken", StrategyType.SMELTING);
        STRATEGIES.put("cooked_cod", StrategyType.SMELTING);
        STRATEGIES.put("cooked_salmon", StrategyType.SMELTING);
    }

    public static StrategyType determineStrategy(ResourceLocation itemId) {
        if (itemId == null) return StrategyType.GROUND_ITEM;
        String path = itemId.getPath().toLowerCase();

        if (STRATEGIES.containsKey(path)) {
            return STRATEGIES.get(path);
        }
        if (path.contains("planks") || path.equals("stick") || path.equals("sticks") || path.contains("pickaxe") || path.contains("sword") || path.contains("axe") || path.contains("shovel") || path.contains("hoe")) {
            return StrategyType.CRAFTING;
        }
        if (path.contains("ingot")) {
            return StrategyType.SMELTING;
        }
        if (path.contains("log") || path.contains("wood")) {
            return StrategyType.TREE;
        }
        if (path.contains("ore") || path.contains("stone") || path.contains("dirt") || path.contains("sand") || path.contains("gravel") || path.contains("obsidian")) {
            return StrategyType.BLOCK_MINE;
        }
        if (path.contains("dye") || path.contains("sac") || path.contains("flesh") || path.contains("leather") || path.contains("wool")) {
            return StrategyType.MOB_DROP;
        }
        return StrategyType.GROUND_ITEM;
    }

    public static ResourceLocation getRawSmeltingSource(ResourceLocation smeltedId) {
        if (smeltedId == null) return new ResourceLocation("minecraft", "raw_iron");
        String path = smeltedId.getPath().toLowerCase();
        return switch (path) {
            case "iron_ingot" -> new ResourceLocation("minecraft", "raw_iron");
            case "gold_ingot" -> new ResourceLocation("minecraft", "raw_gold");
            case "copper_ingot" -> new ResourceLocation("minecraft", "raw_copper");
            case "netherite_scrap" -> new ResourceLocation("minecraft", "ancient_debris");
            case "smooth_stone" -> new ResourceLocation("minecraft", "stone");
            case "glass" -> new ResourceLocation("minecraft", "sand");
            case "charcoal" -> new ResourceLocation("minecraft", "oak_log");
            case "stone" -> new ResourceLocation("minecraft", "cobblestone");
            case "terracotta" -> new ResourceLocation("minecraft", "clay");
            case "cooked_beef" -> new ResourceLocation("minecraft", "beef");
            case "cooked_porkchop" -> new ResourceLocation("minecraft", "porkchop");
            case "cooked_mutton" -> new ResourceLocation("minecraft", "mutton");
            case "cooked_chicken" -> new ResourceLocation("minecraft", "chicken");
            case "cooked_cod" -> new ResourceLocation("minecraft", "cod");
            case "cooked_salmon" -> new ResourceLocation("minecraft", "salmon");
            default -> {
                if (path.contains("iron")) yield new ResourceLocation("minecraft", "raw_iron");
                if (path.contains("gold")) yield new ResourceLocation("minecraft", "raw_gold");
                if (path.contains("copper")) yield new ResourceLocation("minecraft", "raw_copper");
                yield new ResourceLocation("minecraft", "raw_iron");
            }
        };
    }

    public static ResourceLocation getMobForDrop(ResourceLocation dropId) {
        if (dropId == null) return null;
        String path = dropId.getPath().toLowerCase();
        return switch (path) {
            case "ink_sac" -> new ResourceLocation("minecraft", "squid");
            case "glow_ink_sac" -> new ResourceLocation("minecraft", "glow_squid");
            case "leather", "beef", "cooked_beef" -> new ResourceLocation("minecraft", "cow");
            case "porkchop", "cooked_porkchop" -> new ResourceLocation("minecraft", "pig");
            case "mutton", "cooked_mutton", "wool", "white_wool" -> new ResourceLocation("minecraft", "sheep");
            case "feather", "chicken", "cooked_chicken" -> new ResourceLocation("minecraft", "chicken");
            case "bone", "arrow" -> new ResourceLocation("minecraft", "skeleton");
            case "gunpowder" -> new ResourceLocation("minecraft", "creeper");
            case "string", "spider_eye" -> new ResourceLocation("minecraft", "spider");
            case "ender_pearl" -> new ResourceLocation("minecraft", "enderman");
            case "blaze_rod" -> new ResourceLocation("minecraft", "blaze");
            case "slime_ball" -> new ResourceLocation("minecraft", "slime");
            case "rotten_flesh" -> new ResourceLocation("minecraft", "zombie");
            default -> null;
        };
    }
}
