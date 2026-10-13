package com.aicompanion.ai.dimension;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Enterprise Dimensional Resource & Entity Mapping Policy.
 * Identifies whether a requested resource, block, or entity is native to the Nether, The End,
 * or the Overworld, enabling Esclavo to plan interdimensional expeditions autonomously.
 */
public class DimensionalResourcePolicy {

    private static final Set<String> NETHER_KEYWORDS = new HashSet<>();
    private static final Set<String> END_KEYWORDS = new HashSet<>();

    static {
        // Nether Resources, Blocks & Items
        String[] netherItems = {
                "ancient_debris", "netherite", "netherita", "escombros_ancestrales", "escombros ancestrales", "netherite_scrap", "netherite_ingot", "netherite_block",
                "netherite_sword", "netherite_pickaxe", "netherite_axe", "netherite_shovel", "netherite_hoe",
                "netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots",
                "nether_quartz_ore", "quartz", "cuarzo", "cuarzos", "quartz_block", "smooth_quartz", "quartz_pillar",
                "netherrack", "glowstone", "glowstone_dust",
                "soul_sand", "soul_soil", "soul_torch", "soul_campfire", "soul_lantern",
                "basalt", "smooth_basalt", "polished_basalt",
                "blackstone", "polished_blackstone", "gilded_blackstone", "polished_blackstone_bricks",
                "crying_obsidian", "magma_block", "magma_cream",
                "nether_brick", "nether_bricks", "red_nether_bricks",
                "nether_wart", "nether_wart_block", "warped_wart_block",
                "crimson_stem", "warped_stem", "crimson_hyphae", "warped_hyphae",
                "crimson_planks", "warped_planks", "crimson_fungus", "warped_fungus",
                "crimson_nylium", "warped_nylium", "shroomlight", "weeping_vines", "twisting_vines",
                "blaze_rod", "blaze_powder", "ghast_tear", "wither_skeleton_skull",
                "blaze", "ghast", "wither_skeleton", "piglin", "piglin_brute", "zombified_piglin",
                "hoglin", "zoglin", "magma_cube", "strider"
        };
        for (String s : netherItems) NETHER_KEYWORDS.add(s.toLowerCase(Locale.ROOT));

        // End Resources, Blocks & Items
        String[] endItems = {
                "end_stone", "end_stone_bricks",
                "purpur_block", "purpur_pillar", "purpur_stairs", "purpur_slab",
                "chorus_fruit", "popped_chorus_fruit", "chorus_plant", "chorus_flower",
                "dragon_breath", "dragon_egg", "dragon_head", "elytra",
                "shulker_shell", "shulker_box",
                "shulker", "ender_dragon", "endermite"
        };
        for (String s : endItems) END_KEYWORDS.add(s.toLowerCase(Locale.ROOT));
    }

    public static ResourceLocation getRequiredDimensionId(String query) {
        if (query == null || query.isBlank()) return new ResourceLocation("overworld");
        String clean = query.trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("minecraft:")) clean = clean.substring("minecraft:".length());

        if (isNetherResource(clean)) {
            return new ResourceLocation("the_nether");
        }
        if (isEndResource(clean)) {
            return new ResourceLocation("the_end");
        }
        return new ResourceLocation("overworld");
    }

    /**
     * Determines which dimension is strictly required for the given resource or query name.
     * Returns Level.NETHER, Level.END, or Level.OVERWORLD.
     */
    public static ResourceKey<Level> getRequiredDimension(String query) {
        if (query == null || query.isBlank()) return Level.OVERWORLD;
        String clean = query.trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("minecraft:")) clean = clean.substring("minecraft:".length());

        if (isNetherResource(clean)) {
            return Level.NETHER;
        }
        if (isEndResource(clean)) {
            return Level.END;
        }
        return Level.OVERWORLD;
    }

    public static ResourceKey<Level> getRequiredDimension(ResourceLocation location) {
        if (location == null) return Level.OVERWORLD;
        return getRequiredDimension(location.getPath());
    }

    public static ResourceKey<Level> getRequiredDimension(Item item) {
        if (item == null) return Level.OVERWORLD;
        ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
        return key != null ? getRequiredDimension(key) : Level.OVERWORLD;
    }

    public static ResourceKey<Level> getRequiredDimension(Block block) {
        if (block == null) return Level.OVERWORLD;
        ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block);
        return key != null ? getRequiredDimension(key) : Level.OVERWORLD;
    }

    public static ResourceKey<Level> getRequiredDimension(EntityType<?> entityType) {
        if (entityType == null) return Level.OVERWORLD;
        ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entityType);
        return key != null ? getRequiredDimension(key) : Level.OVERWORLD;
    }

    public static boolean isNetherResource(String name) {
        if (name == null) return false;
        String clean = name.trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("minecraft:")) clean = clean.substring("minecraft:".length());

        if (NETHER_KEYWORDS.contains(clean)) return true;
        for (String kw : NETHER_KEYWORDS) {
            if (clean.equals(kw) || clean.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEndResource(String name) {
        if (name == null) return false;
        String clean = name.trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("minecraft:")) clean = clean.substring("minecraft:".length());

        if (END_KEYWORDS.contains(clean)) return true;
        for (String kw : END_KEYWORDS) {
            if (clean.equals(kw) || clean.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if executing this request requires traveling to another dimension from current level.
     */
    public static boolean requiresDimensionTravel(Level currentLevel, String query) {
        if (currentLevel == null) return false;
        ResourceKey<Level> required = getRequiredDimension(query);
        return !currentLevel.dimension().equals(required);
    }

    public static boolean requiresDimensionTravel(Level currentLevel, ResourceLocation resource) {
        if (currentLevel == null || resource == null) return false;
        return requiresDimensionTravel(currentLevel, resource.getPath());
    }
}
