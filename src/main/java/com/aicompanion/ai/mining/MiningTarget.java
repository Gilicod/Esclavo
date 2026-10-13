package com.aicompanion.ai.mining;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class MiningTarget {
    private final String name;
    private final Set<Block> targetBlocks = new HashSet<>();
    private final List<TagKey<Block>> targetBlockTags = new ArrayList<>();
    private final List<TagKey<Item>> targetItemTags = new ArrayList<>();
    private final Set<String> matchingDropKeywords = new HashSet<>();
    private final int minRecommendedY;
    private final int maxRecommendedY;
    /** Original order text / id, persisted so reloads rebuild the exact same target (mod ids included). */
    private String query;
    /** Exact item ids observed in the real loot of the target blocks. */
    private final Set<String> matchingDropIds = new HashSet<>();
    /** Item-only targets ("raw_zinc", "flint") whose source blocks are resolved from the loot index. */
    private Item dropItem = null;
    private boolean bound = false;

    public MiningTarget(String name, int minY, int maxY, Block... blocks) {
        this.name = name;
        this.minRecommendedY = minY;
        this.maxRecommendedY = maxY;
        this.targetBlocks.addAll(Arrays.asList(blocks));
        this.matchingDropKeywords.add(name.toLowerCase());
        this.query = name;
    }

    public MiningTarget withBlockTag(TagKey<Block> tag) {
        if (tag != null) this.targetBlockTags.add(tag);
        return this;
    }

    public MiningTarget withItemTag(TagKey<Item> tag) {
        if (tag != null) this.targetItemTags.add(tag);
        return this;
    }

    public MiningTarget withDropKeyword(String kw) {
        this.matchingDropKeywords.add(kw.toLowerCase());
        return this;
    }

    public String getName() {
        return name;
    }

    public int getMinRecommendedY() {
        return minRecommendedY;
    }

    public int getMaxRecommendedY() {
        return maxRecommendedY;
    }

    public boolean matches(BlockState state) {
        if (state == null || state.isAir()) return false;
        if (targetBlocks.contains(state.getBlock())) return true;
        for (TagKey<Block> tag : targetBlockTags) {
            if (state.is(tag)) return true;
        }
        return false;
    }

    public boolean isTargetDrop(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        // Tools, weapons and armour are never mission drops (old substring match counted
        // diamond_pickaxe as "diamond" and stone_pickaxe/redstone as "stone").
        if (stack.isDamageableItem() || stack.getMaxStackSize() == 1) return false;
        for (TagKey<Item> tag : targetItemTags) {
            if (stack.is(tag)) return true;
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase();
        if (matchingDropKeywords.contains(path)) return true;
        // Silk-touch / block items of the target blocks themselves
        for (Block b : targetBlocks) {
            if (b.asItem() == stack.getItem()) return true;
        }
        return false;
    }

    /**
     * Determines whether the given item in inventory counts towards completion of this mining mission.
     * For instance, "Piedra Luminosa" (the block) requires the actual block item (Blocks.GLOWSTONE.asItem()),
     * whereas isTargetDrop permits picking up glowstone_dust from the ground so it can be crafted into blocks.
     */
    public boolean isGoalItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.isDamageableItem() || stack.getMaxStackSize() == 1) return false;
        if ("Piedra Luminosa".equalsIgnoreCase(this.name) || "glowstone".equalsIgnoreCase(this.query)) {
            return stack.is(Blocks.GLOWSTONE.asItem());
        }
        return isTargetDrop(stack);
    }

    /** Exact-match helper usable without registries (unit tests). */
    public static boolean isExactDropName(Set<String> names, String itemPath) {
        return itemPath != null && names.contains(itemPath.toLowerCase());
    }

    public Set<String> getDropNames() {
        return Collections.unmodifiableSet(matchingDropKeywords);
    }

    public Block getPrimaryBlock() {
        return targetBlocks.isEmpty() ? Blocks.COAL_ORE : targetBlocks.iterator().next();
    }

    public Set<Block> getTargetBlocks() {
        return Collections.unmodifiableSet(targetBlocks);
    }

    public String getQuery() {
        return query;
    }

    public static MiningTarget of(String query) {
        MiningTarget t = ofInternal(query);
        t.query = query == null ? t.name : query.trim();
        return t;
    }

    private static MiningTarget ofInternal(String query) {
        String clean = query.trim().toLowerCase();
        if (clean.startsWith("minecraft:")) clean = clean.substring("minecraft:".length());
        String norm = clean.replace(' ', '_').replace('-', '_');

        switch (norm) {
            case "coal", "coal_ore", "carbon", "carbón" -> {
                return new MiningTarget("Carbón", 0, 128, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE)
                        .withBlockTag(BlockTags.COAL_ORES)
                        .withBlockTag(BlockTags.create(new ResourceLocation("forge", "ores/coal")))
                        .withDropKeyword("coal");
            }
            case "iron", "iron_ore", "hierro" -> {
                return new MiningTarget("Hierro", -16, 80, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE)
                        .withBlockTag(BlockTags.IRON_ORES)
                        .withBlockTag(BlockTags.create(new ResourceLocation("forge", "ores/iron")))
                        .withItemTag(ItemTags.create(new ResourceLocation("forge", "raw_materials/iron")))
                        .withItemTag(ItemTags.create(new ResourceLocation("forge", "ingots/iron")))
                        .withDropKeyword("raw_iron").withDropKeyword("iron_ingot");
            }
            case "gold", "gold_ore", "oro" -> {
                return new MiningTarget("Oro", -64, 32, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE)
                        .withBlockTag(BlockTags.GOLD_ORES)
                        .withBlockTag(BlockTags.create(new ResourceLocation("forge", "ores/gold")))
                        .withItemTag(ItemTags.create(new ResourceLocation("forge", "raw_materials/gold")))
                        .withItemTag(ItemTags.create(new ResourceLocation("forge", "ingots/gold")))
                        .withDropKeyword("raw_gold").withDropKeyword("gold_ingot").withDropKeyword("gold_nugget");
            }
            case "copper", "copper_ore", "cobre" -> {
                return new MiningTarget("Cobre", 0, 96, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE)
                        .withBlockTag(BlockTags.COPPER_ORES)
                        .withBlockTag(BlockTags.create(new ResourceLocation("forge", "ores/copper")))
                        .withItemTag(ItemTags.create(new ResourceLocation("forge", "raw_materials/copper")))
                        .withItemTag(ItemTags.create(new ResourceLocation("forge", "ingots/copper")))
                        .withDropKeyword("raw_copper").withDropKeyword("copper_ingot");
            }
            case "diamond", "diamond_ore", "diamante" -> {
                return new MiningTarget("Diamante", -64, 16, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE)
                        .withBlockTag(BlockTags.DIAMOND_ORES)
                        .withBlockTag(BlockTags.create(new ResourceLocation("forge", "ores/diamond")))
                        .withItemTag(ItemTags.create(new ResourceLocation("forge", "gems/diamond")))
                        .withDropKeyword("diamond");
            }
            case "redstone", "redstone_ore" -> {
                return new MiningTarget("Redstone", -64, 16, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE)
                        .withBlockTag(BlockTags.REDSTONE_ORES)
                        .withBlockTag(BlockTags.create(new ResourceLocation("forge", "ores/redstone")))
                        .withItemTag(ItemTags.create(new ResourceLocation("forge", "dusts/redstone")))
                        .withDropKeyword("redstone");
            }
            case "lapis", "lapis_ore", "lapislázuli", "lapislazuli" -> {
                return new MiningTarget("Lapislázuli", -32, 32, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE)
                        .withBlockTag(BlockTags.LAPIS_ORES)
                        .withBlockTag(BlockTags.create(new ResourceLocation("forge", "ores/lapis")))
                        .withItemTag(ItemTags.create(new ResourceLocation("forge", "gems/lapis")))
                        .withDropKeyword("lapis_lazuli");
            }
            case "emerald", "emerald_ore", "esmeralda" -> {
                return new MiningTarget("Esmeralda", -16, 256, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE)
                        .withBlockTag(BlockTags.EMERALD_ORES)
                        .withBlockTag(BlockTags.create(new ResourceLocation("forge", "ores/emerald")))
                        .withItemTag(ItemTags.create(new ResourceLocation("forge", "gems/emerald")))
                        .withDropKeyword("emerald");
            }
            case "netherite", "netherita", "debris", "ancient_debris", "escombros_ancestrales" -> {
                return new MiningTarget("Netherite", 8, 24, Blocks.ANCIENT_DEBRIS)
                        .withBlockTag(BlockTags.create(new ResourceLocation("forge", "ores/netherite_scrap")))
                        .withDropKeyword("ancient_debris");
            }
            case "quartz", "nether_quartz", "nether_quartz_ore", "cuarzo", "cuarzo_del_nether" -> {
                return new MiningTarget("Cuarzo", 10, 115, Blocks.NETHER_QUARTZ_ORE)
                        .withBlockTag(BlockTags.create(new ResourceLocation("forge", "ores/quartz")))
                        .withDropKeyword("quartz");
            }
            case "glowstone", "piedra_luminosa" -> {
                return new MiningTarget("Piedra Luminosa", 60, 120, Blocks.GLOWSTONE)
                        .withDropKeyword("glowstone")
                        .withDropKeyword("glowstone_dust");
            }
            case "glowstone_dust", "polvo_de_piedra_luminosa", "polvo_luminoso", "polvo_de_glowstone", "polvo_piedra_luminosa" -> {
                return new MiningTarget("Polvo Luminoso", 60, 120, Blocks.GLOWSTONE)
                        .withDropKeyword("glowstone_dust");
            }
            case "netherrack", "piedra_del_nether" -> {
                return new MiningTarget("Netherrack", 0, 128, Blocks.NETHERRACK)
                        .withDropKeyword("netherrack");
            }
            case "soul_sand", "soul_soil", "arena_de_almas", "tierra_de_almas" -> {
                return new MiningTarget("Arena de Almas", 20, 65, Blocks.SOUL_SAND, Blocks.SOUL_SOIL)
                        .withDropKeyword("soul_sand").withDropKeyword("soul_soil");
            }
            case "blackstone", "piedra_negra" -> {
                return new MiningTarget("Piedra Negra", 10, 100, Blocks.BLACKSTONE, Blocks.GILDED_BLACKSTONE)
                        .withDropKeyword("blackstone").withDropKeyword("gilded_blackstone");
            }
            case "basalt", "basalto" -> {
                return new MiningTarget("Basalto", 20, 100, Blocks.BASALT, Blocks.SMOOTH_BASALT)
                        .withDropKeyword("basalt");
            }
            case "obsidian", "obsidiana" -> {
                return new MiningTarget("Obsidiana", -64, 30, Blocks.OBSIDIAN)
                        .withDropKeyword("obsidian");
            }
            case "stone", "piedra", "adoquin", "adoquín", "cobblestone" -> {
                // Only natural stone/deepslate are mined; cobblestone blocks are usually player builds.
                return new MiningTarget("Piedra", -64, 320, Blocks.STONE, Blocks.DEEPSLATE)
                        .withDropKeyword("cobblestone").withDropKeyword("cobbled_deepslate")
                        .withDropKeyword("stone").withDropKeyword("deepslate");
            }
            default -> {
                try {
                    // Try resolving as direct block ID
                    ResourceLocation id = ResourceLocation.tryParse(clean.contains(":") ? clean : "minecraft:" + norm);
                    if (id != null) {
                        Block block = BuiltInRegistries.BLOCK.get(id);
                        if (block != Blocks.AIR) {
                            MiningTarget t = new MiningTarget(id.getPath(), -64, 320, block).withDropKeyword(id.getPath());
                            String p = id.getPath();
                            if (p.endsWith("_ore")) {
                                String base = p.substring(0, p.length() - 4).replaceFirst("^(deepslate|nether|end|stone)_", "");
                                for (String kw : new String[]{base, "raw_" + base, base + "_gem", base + "_crystal", base + "_shard", base + "_dust", base + "_chunk", base + "_nugget"}) {
                                    t.withDropKeyword(kw);
                                }
                                ResourceLocation deepId = ResourceLocation.tryParse(id.getNamespace() + ":deepslate_" + base + "_ore");
                                if (deepId != null) {
                                    Block deep = BuiltInRegistries.BLOCK.get(deepId);
                                    if (deep != Blocks.AIR) {
                                        t.targetBlocks.add(deep);
                                    }
                                }
                            }
                            return t;
                        }
                    }
                } catch (Exception ignored) {}
                // Fallback default coal
                return new MiningTarget("Carbón", 0, 128, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE)
                        .withDropKeyword("coal");
            }
        }
    }
}
