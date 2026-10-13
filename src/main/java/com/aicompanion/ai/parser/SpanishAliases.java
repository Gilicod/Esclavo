package com.aicompanion.ai.parser;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * Spanish NLP translation dictionary and Tag resolver for Esclavo (Pilar 8).
 * Maps colloquial Spanish names, typos, and phrases to authoritative Minecraft ResourceLocations
 * and Forge/Minecraft TagKeys for universal mod compatibility.
 */
public class SpanishAliases {
    private static final Map<String, ResourceLocation> ORE_ALIASES = new HashMap<>();
    private static final Map<String, ResourceLocation> ORE_TAG_LOCATIONS = new HashMap<>();
    private static final Map<String, ResourceLocation> MOB_ALIASES = new HashMap<>();
    private static final Map<String, String> WOOD_ALIASES = new HashMap<>();

    static {
        // Ores / Resources - Direct ResourceLocations & Forge Tags
        registerOre("oro", "gold_ore", "ores/gold");
        registerOre("gold", "gold_ore", "ores/gold");
        registerOre("mena de oro", "gold_ore", "ores/gold");
        registerOre("mineral de oro", "gold_ore", "ores/gold");
        registerOre("pepita de oro", "gold_nugget", "nuggets/gold");
        registerOre("lingote de oro", "gold_ingot", "ingots/gold");

        registerOre("diamante", "diamond_ore", "ores/diamond");
        registerOre("diamantes", "diamond_ore", "ores/diamond");
        registerOre("diamond", "diamond_ore", "ores/diamond");
        registerOre("diamonds", "diamond_ore", "ores/diamond");
        registerOre("mena de diamante", "diamond_ore", "ores/diamond");
        registerOre("mineral de diamante", "diamond_ore", "ores/diamond");

        registerOre("hierro", "iron_ore", "ores/iron");
        registerOre("iron", "iron_ore", "ores/iron");
        registerOre("mena de hierro", "iron_ore", "ores/iron");
        registerOre("mineral de hierro", "iron_ore", "ores/iron");
        registerOre("hierro crudo", "raw_iron", "raw_materials/iron");
        registerOre("lingote de hierro", "iron_ingot", "ingots/iron");

        registerOre("carbon", "coal_ore", "ores/coal");
        registerOre("carbón", "coal_ore", "ores/coal");
        registerOre("coal", "coal_ore", "ores/coal");
        registerOre("mena de carbon", "coal_ore", "ores/coal");
        registerOre("mineral de carbon", "coal_ore", "ores/coal");

        registerOre("cobre", "copper_ore", "ores/copper");
        registerOre("copper", "copper_ore", "ores/copper");
        registerOre("mena de cobre", "copper_ore", "ores/copper");
        registerOre("cobre crudo", "raw_copper", "raw_materials/copper");

        registerOre("redstone", "redstone_ore", "ores/redstone");
        registerOre("polvo de redstone", "redstone", "dusts/redstone");
        registerOre("piedra rojiza", "redstone_ore", "ores/redstone");

        registerOre("lapislazuli", "lapis_ore", "ores/lapis");
        registerOre("lapislázuli", "lapis_ore", "ores/lapis");
        registerOre("lapis", "lapis_ore", "ores/lapis");

        registerOre("esmeralda", "emerald_ore", "ores/emerald");
        registerOre("esmeraldas", "emerald_ore", "ores/emerald");
        registerOre("emerald", "emerald_ore", "ores/emerald");

        registerOre("netherita", "ancient_debris", "ores/netherite_scrap");
        registerOre("netherite", "ancient_debris", "ores/netherite_scrap");
        registerOre("escombros ancestrales", "ancient_debris", "ores/netherite_scrap");

        registerOre("cuarzo", "nether_quartz_ore", "ores/quartz");
        registerOre("quartz", "nether_quartz_ore", "ores/quartz");
        registerOre("cuarzo del nether", "nether_quartz_ore", "ores/quartz");
        registerOre("nether quartz", "nether_quartz_ore", "ores/quartz");

        registerOre("piedra", "stone", "stone");
        registerOre("stone", "stone", "stone");
        registerOre("adoquin", "cobblestone", "cobblestone");
        registerOre("adoquín", "cobblestone", "cobblestone");
        registerOre("cobblestone", "cobblestone", "cobblestone");
        registerOre("roca", "stone", "stone");
        registerOre("pizarra profunda", "deepslate", "deepslate");
        registerOre("deepslate", "deepslate", "deepslate");
        registerOre("arena", "sand", "sand");
        registerOre("sand", "sand", "sand");

        // Nether blocks
        registerOre("piedra luminosa", "glowstone", "glowstone");
        registerOre("glowstone", "glowstone", "glowstone");
        registerOre("polvo de piedra luminosa", "glowstone_dust", "dusts/glowstone");
        registerOre("polvo luminoso", "glowstone_dust", "dusts/glowstone");
        registerOre("polvo de glowstone", "glowstone_dust", "dusts/glowstone");
        registerOre("polvo piedra luminosa", "glowstone_dust", "dusts/glowstone");
        registerOre("arena de almas", "soul_sand", "soul_sand");
        registerOre("arena_de_almas", "soul_sand", "soul_sand");
        registerOre("soul sand", "soul_sand", "soul_sand");
        registerOre("soul_sand", "soul_sand", "soul_sand");
        registerOre("tierra de almas", "soul_soil", "soul_soil");
        registerOre("soul soil", "soul_soil", "soul_soil");
        registerOre("piedra negra", "blackstone", "blackstone");
        registerOre("piedra_negra", "blackstone", "blackstone");
        registerOre("blackstone", "blackstone", "blackstone");
        registerOre("basalto", "basalt", "basalt");
        registerOre("basalt", "basalt", "basalt");
        registerOre("obsidiana", "obsidian", "obsidian");
        registerOre("obsidian", "obsidian", "obsidian");
        registerOre("netherrack", "netherrack", "netherrack");
        registerOre("piedra del nether", "netherrack", "netherrack");
        registerOre("piedra_del_nether", "netherrack", "netherrack");

        // Mobs
        MOB_ALIASES.put("creeper", new ResourceLocation("minecraft", "creeper"));
        MOB_ALIASES.put("creepers", new ResourceLocation("minecraft", "creeper"));
        MOB_ALIASES.put("zombie", new ResourceLocation("minecraft", "zombie"));
        MOB_ALIASES.put("zombies", new ResourceLocation("minecraft", "zombie"));
        MOB_ALIASES.put("zombi", new ResourceLocation("minecraft", "zombie"));
        MOB_ALIASES.put("zombis", new ResourceLocation("minecraft", "zombie"));
        MOB_ALIASES.put("esqueleto", new ResourceLocation("minecraft", "skeleton"));
        MOB_ALIASES.put("esqueletos", new ResourceLocation("minecraft", "skeleton"));
        MOB_ALIASES.put("skeleton", new ResourceLocation("minecraft", "skeleton"));
        MOB_ALIASES.put("skeletons", new ResourceLocation("minecraft", "skeleton"));
        MOB_ALIASES.put("araña", new ResourceLocation("minecraft", "spider"));
        MOB_ALIASES.put("arañas", new ResourceLocation("minecraft", "spider"));
        MOB_ALIASES.put("arana", new ResourceLocation("minecraft", "spider"));
        MOB_ALIASES.put("spider", new ResourceLocation("minecraft", "spider"));
        MOB_ALIASES.put("enderman", new ResourceLocation("minecraft", "enderman"));
        MOB_ALIASES.put("blaze", new ResourceLocation("minecraft", "blaze"));
        MOB_ALIASES.put("bruja", new ResourceLocation("minecraft", "witch"));
        MOB_ALIASES.put("witch", new ResourceLocation("minecraft", "witch"));
        MOB_ALIASES.put("vaca", new ResourceLocation("minecraft", "cow"));
        MOB_ALIASES.put("vacas", new ResourceLocation("minecraft", "cow"));
        MOB_ALIASES.put("cow", new ResourceLocation("minecraft", "cow"));
        MOB_ALIASES.put("cerdo", new ResourceLocation("minecraft", "pig"));
        MOB_ALIASES.put("cerdos", new ResourceLocation("minecraft", "pig"));
        MOB_ALIASES.put("cochino", new ResourceLocation("minecraft", "pig"));
        MOB_ALIASES.put("cochinos", new ResourceLocation("minecraft", "pig"));
        MOB_ALIASES.put("puerco", new ResourceLocation("minecraft", "pig"));
        MOB_ALIASES.put("puercos", new ResourceLocation("minecraft", "pig"));
        MOB_ALIASES.put("chancho", new ResourceLocation("minecraft", "pig"));
        MOB_ALIASES.put("chanchos", new ResourceLocation("minecraft", "pig"));
        MOB_ALIASES.put("marrano", new ResourceLocation("minecraft", "pig"));
        MOB_ALIASES.put("marranos", new ResourceLocation("minecraft", "pig"));
        MOB_ALIASES.put("pig", new ResourceLocation("minecraft", "pig"));
        MOB_ALIASES.put("pigs", new ResourceLocation("minecraft", "pig"));
        MOB_ALIASES.put("oveja", new ResourceLocation("minecraft", "sheep"));
        MOB_ALIASES.put("ovejas", new ResourceLocation("minecraft", "sheep"));
        MOB_ALIASES.put("sheep", new ResourceLocation("minecraft", "sheep"));
        MOB_ALIASES.put("pollo", new ResourceLocation("minecraft", "chicken"));
        MOB_ALIASES.put("pollos", new ResourceLocation("minecraft", "chicken"));
        MOB_ALIASES.put("gallina", new ResourceLocation("minecraft", "chicken"));
        MOB_ALIASES.put("gallinas", new ResourceLocation("minecraft", "chicken"));
        MOB_ALIASES.put("chicken", new ResourceLocation("minecraft", "chicken"));
        MOB_ALIASES.put("aldeano", new ResourceLocation("minecraft", "villager"));
        MOB_ALIASES.put("aldeanos", new ResourceLocation("minecraft", "villager"));
        MOB_ALIASES.put("villager", new ResourceLocation("minecraft", "villager"));

        // Woods (generic terms map to "any")
        WOOD_ALIASES.put("madera", "any");
        WOOD_ALIASES.put("wood", "any");
        WOOD_ALIASES.put("arbol", "any");
        WOOD_ALIASES.put("arboles", "any");
        WOOD_ALIASES.put("árbol", "any");
        WOOD_ALIASES.put("árboles", "any");
        WOOD_ALIASES.put("tronco", "any");
        WOOD_ALIASES.put("troncos", "any");
        WOOD_ALIASES.put("leña", "any");
        WOOD_ALIASES.put("cualquiera", "any");
        WOOD_ALIASES.put("any", "any");
        WOOD_ALIASES.put("all", "any");

        WOOD_ALIASES.put("roble", "oak");
        WOOD_ALIASES.put("oak", "oak");
        WOOD_ALIASES.put("madera de roble", "oak");
        WOOD_ALIASES.put("abedul", "birch");
        WOOD_ALIASES.put("birch", "birch");
        WOOD_ALIASES.put("madera de abedul", "birch");
        WOOD_ALIASES.put("abeto", "spruce");
        WOOD_ALIASES.put("spruce", "spruce");
        WOOD_ALIASES.put("pino", "spruce");
        WOOD_ALIASES.put("madera de pino", "spruce");
        WOOD_ALIASES.put("jungla", "jungle");
        WOOD_ALIASES.put("jungle", "jungle");
        WOOD_ALIASES.put("acacia", "acacia");
        WOOD_ALIASES.put("roble oscuro", "dark_oak");
        WOOD_ALIASES.put("roble_oscuro", "dark_oak");
        WOOD_ALIASES.put("dark oak", "dark_oak");
        WOOD_ALIASES.put("cerezo", "cherry");
        WOOD_ALIASES.put("cherry", "cherry");
        WOOD_ALIASES.put("manglar", "mangrove");
        WOOD_ALIASES.put("mangle", "mangrove");
        WOOD_ALIASES.put("mangrove", "mangrove");
        WOOD_ALIASES.put("bambu", "bamboo");
        WOOD_ALIASES.put("bambú", "bamboo");
        WOOD_ALIASES.put("bamboo", "bamboo");
        WOOD_ALIASES.put("carmesi", "crimson");
        WOOD_ALIASES.put("carmesí", "crimson");
        WOOD_ALIASES.put("crimson", "crimson");
        WOOD_ALIASES.put("distorsionado", "warped");
        WOOD_ALIASES.put("warped", "warped");
    }

    private static void registerOre(String alias, String vanillaId, String forgeTag) {
        ORE_ALIASES.put(alias.toLowerCase(), new ResourceLocation("minecraft", vanillaId));
        ORE_TAG_LOCATIONS.put(alias.toLowerCase(), new ResourceLocation("forge", forgeTag));
    }

    public static ResourceLocation resolveOre(String input) {
        if (input == null) return null;
        String clean = input.toLowerCase().trim();
        return ORE_ALIASES.getOrDefault(clean, clean.contains(":") ? ResourceLocation.tryParse(clean) : null);
    }

    public static TagKey<Block> resolveOreTag(String input) {
        if (input == null) return null;
        ResourceLocation tagLoc = ORE_TAG_LOCATIONS.get(input.toLowerCase().trim());
        return tagLoc != null ? BlockTags.create(tagLoc) : null;
    }

    public static ResourceLocation resolveMob(String input) {
        if (input == null) return null;
        String clean = input.toLowerCase().trim();
        return MOB_ALIASES.getOrDefault(clean, clean.contains(":") ? ResourceLocation.tryParse(clean) : null);
    }

    public static String resolveWood(String input) {
        if (input == null || input.trim().isEmpty()) return "any";
        String clean = input.toLowerCase().trim();
        return WOOD_ALIASES.getOrDefault(clean, "any");
    }
}
