package com.aicompanion.ai.parser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.*;

public class ItemSearchRegistry {

    public record ItemSuggestion(String label, String insertText, ResourceLocation resourceId, String category) {}

    private static final Map<String, ResourceLocation> SPANISH_DICT = new LinkedHashMap<>();
    private static final Map<ResourceLocation, String> DISPLAY_NAMES = new HashMap<>();

    static {
        // --- 1. SPECIAL DROPS / ITEMS (TINTA, CUERO, ETC.) ---
        register("tinta", "minecraft:ink_sac", "Tinta (Calamar)", "DROP");
        register("tinta brillante", "minecraft:glow_ink_sac", "Tinta Brillante", "DROP");
        register("saco de tinta", "minecraft:ink_sac", "Saco de Tinta", "DROP");
        register("tinte negro", "minecraft:black_dye", "Tinte Negro", "ITEM");
        register("tinte rojo", "minecraft:red_dye", "Tinte Rojo", "ITEM");
        register("tinte azul", "minecraft:blue_dye", "Tinte Azul", "ITEM");
        register("tinte amarillo", "minecraft:yellow_dye", "Tinte Amarillo", "ITEM");
        register("tinte verde", "minecraft:green_dye", "Tinte Verde", "ITEM");
        register("tinte blanco", "minecraft:white_dye", "Tinte Blanco", "ITEM");
        register("tinte cian", "minecraft:cyan_dye", "Tinte Cian", "ITEM");

        register("cuero", "minecraft:leather", "Cuero", "DROP");
        register("pluma", "minecraft:feather", "Pluma", "DROP");
        register("plumas", "minecraft:feather", "Plumas", "DROP");
        register("lana", "minecraft:white_wool", "Lana Blanca", "DROP");
        register("lana blanca", "minecraft:white_wool", "Lana Blanca", "DROP");
        register("hueso", "minecraft:bone", "Hueso", "DROP");
        register("huesos", "minecraft:bone", "Huesos", "DROP");
        register("polvora", "minecraft:gunpowder", "Pólvora", "DROP");
        register("pólvora", "minecraft:gunpowder", "Pólvora", "DROP");
        register("hilo", "minecraft:string", "Hilo", "DROP");
        register("ojo de arana", "minecraft:spider_eye", "Ojo de Araña", "DROP");
        register("perla de ender", "minecraft:ender_pearl", "Perla de Ender", "DROP");
        register("ender pearl", "minecraft:ender_pearl", "Perla de Ender", "DROP");
        register("vara de blaze", "minecraft:blaze_rod", "Vara de Blaze", "DROP");
        register("blaze rod", "minecraft:blaze_rod", "Vara de Blaze", "DROP");
        register("bola de slime", "minecraft:slime_ball", "Bola de Slime", "DROP");
        register("carne podrida", "minecraft:rotten_flesh", "Carne Podrida", "DROP");

        // --- 2. COMIDA Y CULTIVOS ---
        register("semillas", "minecraft:wheat_seeds", "Semillas de Trigo", "GATHER");
        register("semillas de trigo", "minecraft:wheat_seeds", "Semillas de Trigo", "GATHER");
        register("trigo", "minecraft:wheat", "Trigo", "GATHER");
        register("zanahoria", "minecraft:carrot", "Zanahoria", "GATHER");
        register("zanahorias", "minecraft:carrot", "Zanahorias", "GATHER");
        register("patata", "minecraft:potato", "Patata", "GATHER");
        register("patatas", "minecraft:potato", "Patatas", "GATHER");
        register("papa", "minecraft:potato", "Papa", "GATHER");
        register("papas", "minecraft:potato", "Papas", "GATHER");
        register("manzana", "minecraft:apple", "Manzana", "GATHER");
        register("manzanas", "minecraft:apple", "Manzanas", "GATHER");
        register("sandia", "minecraft:melon_slice", "Sandía", "GATHER");
        register("sandía", "minecraft:melon_slice", "Sandía", "GATHER");
        register("calabaza", "minecraft:pumpkin", "Calabaza", "GATHER");
        register("cana de azucar", "minecraft:sugar_cane", "Caña de Azúcar", "GATHER");
        register("azucar", "minecraft:sugar", "Azúcar", "ITEM");
        register("bambu", "minecraft:bamboo", "Bambú", "GATHER");
        register("bambú", "minecraft:bamboo", "Bambú", "GATHER");
        register("pan", "minecraft:bread", "Pan", "ITEM");
        register("carne de vaca", "minecraft:beef", "Carne de Vaca", "DROP");
        register("bife", "minecraft:beef", "Bife", "DROP");
        register("carne de cerdo", "minecraft:porkchop", "Carne de Cerdo", "DROP");
        register("cerdo crudo", "minecraft:porkchop", "Carne de Cerdo", "DROP");
        register("chuleta", "minecraft:porkchop", "Chuleta de Cerdo", "DROP");
        register("chuletas", "minecraft:porkchop", "Chuletas de Cerdo", "DROP");
        register("chuleta de cerdo", "minecraft:porkchop", "Chuleta de Cerdo", "DROP");
        register("tocino", "minecraft:porkchop", "Tocino (Carne de Cerdo)", "DROP");
        register("tosino", "minecraft:porkchop", "Tocino (Carne de Cerdo)", "DROP");
        register("tocineta", "minecraft:porkchop", "Tocineta", "DROP");
        register("porkchop", "minecraft:porkchop", "Chuleta de Cerdo", "DROP");
        register("chuleta cocinada", "minecraft:cooked_porkchop", "Chuleta Cocinada", "DROP");
        register("tocino cocinado", "minecraft:cooked_porkchop", "Chuleta Cocinada", "DROP");
        register("tosino cocinado", "minecraft:cooked_porkchop", "Chuleta Cocinada", "DROP");
        register("pollo", "minecraft:chicken", "Pollo Crudo", "DROP");
        register("cordero", "minecraft:mutton", "Cordero Crudo", "DROP");
        register("pescado", "minecraft:cod", "Pescado (Bacalao)", "DROP");

        // --- 3. FLORES Y PLANTAS ---
        register("flores", "minecraft:dandelion", "Flores Variadas", "GATHER");
        register("flor", "minecraft:dandelion", "Flor", "GATHER");
        register("diente de leon", "minecraft:dandelion", "Diente de León", "GATHER");
        register("diente de león", "minecraft:dandelion", "Diente de León", "GATHER");
        register("amapola", "minecraft:poppy", "Amapola", "GATHER");
        register("orquidea", "minecraft:blue_orchid", "Orquídea Azul", "GATHER");
        register("orquídea", "minecraft:blue_orchid", "Orquídea Azul", "GATHER");
        register("tulipan", "minecraft:red_tulip", "Tulipán Rojo", "GATHER");
        register("tulipán", "minecraft:red_tulip", "Tulipán", "GATHER");
        register("girasol", "minecraft:sunflower", "Girasol", "GATHER");

        // --- 4. MADERA / ÁRBOLES ---
        register("madera", "minecraft:oak_log", "Madera (Roble)", "CHOP");
        register("madera de roble", "minecraft:oak_log", "Madera de Roble", "CHOP");
        register("arbol", "minecraft:oak_log", "Talar Árboles", "CHOP");
        register("arboles", "minecraft:oak_log", "Talar Árboles", "CHOP");
        register("árbol", "minecraft:oak_log", "Talar Árbol", "CHOP");
        register("árboles", "minecraft:oak_log", "Talar Árboles", "CHOP");
        register("roble", "minecraft:oak_log", "Tronco de Roble", "CHOP");
        register("abedul", "minecraft:birch_log", "Tronco de Abedul", "CHOP");
        register("abeto", "minecraft:spruce_log", "Tronco de Abeto", "CHOP");
        register("jungla", "minecraft:jungle_log", "Tronco de Jungla", "CHOP");
        register("acacia", "minecraft:acacia_log", "Tronco de Acacia", "CHOP");
        register("roble oscuro", "minecraft:dark_oak_log", "Roble Oscuro", "CHOP");
        register("mangle", "minecraft:mangrove_log", "Tronco de Mangle", "CHOP");
        register("cerezo", "minecraft:cherry_log", "Tronco de Cerezo", "CHOP");

        // --- 5. MINERALES Y METALES ---
        register("oro", "minecraft:gold_ore", "Oro", "MINE");
        register("mineral de oro", "minecraft:gold_ore", "Mineral de Oro", "MINE");
        register("lingote de oro", "minecraft:gold_ingot", "Lingote de Oro", "ITEM");
        register("pepita de oro", "minecraft:gold_nugget", "Pepita de Oro", "ITEM");
        register("hierro", "minecraft:iron_ore", "Hierro", "MINE");
        register("mineral de hierro", "minecraft:iron_ore", "Mineral de Hierro", "MINE");
        register("lingote de hierro", "minecraft:iron_ingot", "Lingote de Hierro", "ITEM");
        register("diamante", "minecraft:diamond_ore", "Diamante", "MINE");
        register("diamantes", "minecraft:diamond_ore", "Diamantes", "MINE");
        register("carbon", "minecraft:coal_ore", "Carbón", "MINE");
        register("carbón", "minecraft:coal_ore", "Carbón", "MINE");
        register("cobre", "minecraft:copper_ore", "Cobre", "MINE");
        register("redstone", "minecraft:redstone_ore", "Redstone", "MINE");
        register("lapislazuli", "minecraft:lapis_ore", "Lapislázuli", "MINE");
        register("lapislázuli", "minecraft:lapis_ore", "Lapislázuli", "MINE");
        register("esmeralda", "minecraft:emerald_ore", "Esmeralda", "MINE");
        register("esmeraldas", "minecraft:emerald_ore", "Esmeraldas", "MINE");
        register("netherita", "minecraft:ancient_debris", "Detritos Ancestrales", "MINE");
        register("detritos ancestrales", "minecraft:ancient_debris", "Detritos Ancestrales", "MINE");
        register("debris", "minecraft:ancient_debris", "Detritos Ancestrales", "MINE");
        register("lingote de netherita", "minecraft:netherite_ingot", "Lingote de Netherita", "ITEM");
        register("cuarzo", "minecraft:nether_quartz_ore", "Cuarzo del Nether", "MINE");
        register("cuarzo del nether", "minecraft:nether_quartz_ore", "Cuarzo del Nether", "MINE");
        register("piedra luminosa", "minecraft:glowstone", "Piedra Luminosa", "MINE");
        register("glowstone", "minecraft:glowstone", "Piedra Luminosa", "MINE");
        register("polvo de piedra luminosa", "minecraft:glowstone_dust", "Polvo de Piedra Luminosa", "ITEM");
        register("polvo luminoso", "minecraft:glowstone_dust", "Polvo de Piedra Luminosa", "ITEM");
        register("polvo de glowstone", "minecraft:glowstone_dust", "Polvo de Piedra Luminosa", "ITEM");
        register("polvo piedra luminosa", "minecraft:glowstone_dust", "Polvo de Piedra Luminosa", "ITEM");
        register("arena de almas", "minecraft:soul_sand", "Arena de Almas", "MINE");
        register("soul sand", "minecraft:soul_sand", "Arena de Almas", "MINE");
        register("tierra de almas", "minecraft:soul_soil", "Tierra de Almas", "MINE");
        register("soul soil", "minecraft:soul_soil", "Tierra de Almas", "MINE");
        register("basalto", "minecraft:basalt", "Basalto", "MINE");
        register("piedra negra", "minecraft:blackstone", "Piedra Negra", "MINE");
        register("blackstone", "minecraft:blackstone", "Piedra Negra", "MINE");
        register("netherrack", "minecraft:netherrack", "Piedra del Nether", "MINE");
        register("piedra del nether", "minecraft:netherrack", "Piedra del Nether", "MINE");

        // --- 6. BLOQUES Y MATERIALES DE CONSTRUCCIÓN ---
        register("obsidiana", "minecraft:obsidian", "Obsidiana", "MINE");
        register("piedra", "minecraft:stone", "Piedra", "MINE");
        register("adoquin", "minecraft:cobblestone", "Adoquín", "MINE");
        register("adoquín", "minecraft:cobblestone", "Adoquín", "MINE");
        register("deepslate", "minecraft:deepslate", "Pizarra Profunda", "MINE");
        register("arena", "minecraft:sand", "Arena", "MINE");
        register("arena roja", "minecraft:red_sand", "Arena Roja", "MINE");
        register("grava", "minecraft:gravel", "Grava", "MINE");
        register("arcilla", "minecraft:clay", "Arcilla", "MINE");
        register("tierra", "minecraft:dirt", "Tierra", "MINE");
        register("cesped", "minecraft:grass_block", "Césped", "MINE");
        register("cristal", "minecraft:glass", "Cristal", "ITEM");
        register("antorcha", "minecraft:torch", "Antorchas", "ITEM");
        register("antorchas", "minecraft:torch", "Antorchas", "ITEM");
        register("cofre", "minecraft:chest", "Cofre", "ITEM");
        register("baul", "minecraft:chest", "Baúl / Cofre", "ACTION");
        register("vaul", "minecraft:chest", "Baúl / Cofre", "ACTION");
        register("horno", "minecraft:furnace", "Horno", "ITEM");
        register("mesa de crafteo", "minecraft:crafting_table", "Mesa de Crafteo", "ITEM");
        register("palo", "minecraft:stick", "Palo", "CRAFT");
        register("palos", "minecraft:stick", "Palos", "CRAFT");
        register("stick", "minecraft:stick", "Palo", "CRAFT");
        register("sticks", "minecraft:stick", "Palos", "CRAFT");
        register("tablon", "minecraft:oak_planks", "Tablones de Madera", "CRAFT");
        register("tablones", "minecraft:oak_planks", "Tablones de Madera", "CRAFT");
        register("planks", "minecraft:oak_planks", "Tablones de Madera", "CRAFT");

        // --- 7. HERRAMIENTAS Y COMBATE ---
        register("espada", "minecraft:iron_sword", "Espada de Hierro", "ITEM");
        register("pico", "minecraft:iron_pickaxe", "Pico de Hierro", "ITEM");
        register("hacha", "minecraft:iron_axe", "Hacha de Hierro", "ITEM");
        register("pala", "minecraft:iron_shovel", "Pala de Hierro", "ITEM");
        register("arco", "minecraft:bow", "Arco", "ITEM");
        register("flecha", "minecraft:arrow", "Flechas", "ITEM");
        register("flechas", "minecraft:arrow", "Flechas", "ITEM");
        register("cubo", "minecraft:bucket", "Cubo / Balde", "ITEM");
        register("cubo de agua", "minecraft:water_bucket", "Cubo con Agua", "ITEM");

        // --- 8. MOBS ---
        register("zombie", "minecraft:zombie", "Zombie", "KILL");
        register("zombies", "minecraft:zombie", "Zombies", "KILL");
        register("esqueleto", "minecraft:skeleton", "Esqueleto", "KILL");
        register("esqueletos", "minecraft:skeleton", "Esqueletos", "KILL");
        register("creeper", "minecraft:creeper", "Creeper", "KILL");
        register("creepers", "minecraft:creeper", "Creepers", "KILL");
        register("arana", "minecraft:spider", "Araña", "KILL");
        register("araña", "minecraft:spider", "Araña", "KILL");
        register("aranas", "minecraft:spider", "Arañas", "KILL");
        register("arañas", "minecraft:spider", "Arañas", "KILL");
        register("enderman", "minecraft:enderman", "Enderman", "KILL");
        register("calamar", "minecraft:squid", "Calamar (Tinta)", "KILL");
        register("calamar brillante", "minecraft:glow_squid", "Calamar Brillante", "KILL");
        register("vaca", "minecraft:cow", "Vaca (Cuero/Carne)", "KILL");
        register("vacas", "minecraft:cow", "Vacas", "KILL");
        register("cerdo", "minecraft:pig", "Cerdo", "KILL");
        register("cerdos", "minecraft:pig", "Cerdos", "KILL");
        register("cochino", "minecraft:pig", "Cochino / Cerdo", "KILL");
        register("cochinos", "minecraft:pig", "Cochinos / Cerdos", "KILL");
        register("puerco", "minecraft:pig", "Puerco / Cerdo", "KILL");
        register("puercos", "minecraft:pig", "Puercos / Cerdos", "KILL");
        register("chancho", "minecraft:pig", "Chancho / Cerdo", "KILL");
        register("chanchos", "minecraft:pig", "Chanchos / Cerdos", "KILL");
        register("marrano", "minecraft:pig", "Marrano / Cerdo", "KILL");
        register("marranos", "minecraft:pig", "Marranos / Cerdos", "KILL");
        register("oveja", "minecraft:sheep", "Oveja (Lana)", "KILL");
        register("gallina", "minecraft:chicken", "Gallina (Plumas)", "KILL");
        register("blaze", "minecraft:blaze", "Blaze", "KILL");
        register("bruja", "minecraft:witch", "Bruja", "KILL");
    }

    private static void register(String keyword, String resourceId, String displayName, String category) {
        ResourceLocation id = new ResourceLocation(resourceId);
        SPANISH_DICT.put(normalize(keyword), id);
        DISPLAY_NAMES.putIfAbsent(id, displayName);
    }

    public static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().trim()
                .replace('á', 'a')
                .replace('é', 'e')
                .replace('í', 'i')
                .replace('ó', 'o')
                .replace('ú', 'u')
                .replace('ñ', 'n');
    }

    public static ResourceLocation resolve(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        String norm = normalize(input);

        // 1. Un ID completo del registro ("create:zinc_ore") se respeta tal cual
        if (norm.contains(":")) {
            try {
                ResourceLocation exact = new ResourceLocation(norm);
                if (BuiltInRegistries.ITEM.containsKey(exact) || BuiltInRegistries.BLOCK.containsKey(exact)) {
                    return exact;
                }
            } catch (Exception ignored) {}
        }

        // 2. Coincidencia exacta en el diccionario español
        if (SPANISH_DICT.containsKey(norm)) {
            return SPANISH_DICT.get(norm);
        }

        // 3. Coincidencia exacta con el nombre visible de cualquier objeto o bloque registrado (vanilla + mods)
        for (CatalogEntry entry : getAllCatalogEntries()) {
            if (normalize(entry.displayName()).equals(norm)) {
                return entry.id();
            }
        }

        // 4. Coincidencia por subcadena en el diccionario español (claves más largas primero)
        List<Map.Entry<String, ResourceLocation>> sortedDict = new ArrayList<>(SPANISH_DICT.entrySet());
        sortedDict.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        if (norm.length() >= 3) {
            for (Map.Entry<String, ResourceLocation> entry : sortedDict) {
                if (entry.getKey().length() >= 3 && norm.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
            for (Map.Entry<String, ResourceLocation> entry : sortedDict) {
                if (entry.getKey().length() >= 3 && entry.getKey().contains(norm)) {
                    return entry.getValue();
                }
            }
        }

        // 5. Coincidencia por subcadena con el nombre visible de catálogo
        if (norm.length() >= 3) {
            for (CatalogEntry entry : getAllCatalogEntries()) {
                String disp = normalize(entry.displayName());
                if (disp.contains(norm) || norm.contains(disp)) {
                    return entry.id();
                }
            }
        }

        // 6. Direct Registry lookup
        try {
            ResourceLocation direct = norm.contains(":") ? new ResourceLocation(norm) : new ResourceLocation("minecraft", norm);
            if (BuiltInRegistries.ITEM.containsKey(direct) || BuiltInRegistries.BLOCK.containsKey(direct)) {
                return direct;
            }
        } catch (Exception ignored) {}

        return null;
    }

    public static String getDisplayName(ResourceLocation id) {
        if (id == null) return "Objeto";
        if (DISPLAY_NAMES.containsKey(id)) {
            return DISPLAY_NAMES.get(id);
        }
        // Buscar el nombre localizado real en el registro de ítems
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                try {
                    String translated = item.getName(new net.minecraft.world.item.ItemStack(item)).getString();
                    if (translated != null && !translated.trim().isEmpty()) {
                        DISPLAY_NAMES.put(id, translated);
                        return translated;
                    }
                } catch (Exception ignored) {}
            }
        }
        String path = id.getPath().replace('_', ' ');
        return Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    /**
     * Returns dynamic suggestions matching the user's active typing in the GUI.
     */
    public static List<ItemSuggestion> getSuggestions(String query, int limit) {
        List<ItemSuggestion> results = new ArrayList<>();
        Set<String> seenLabels = new HashSet<>();

        String norm = normalize(query);

        // If query is empty, provide rich general starter actions
        if (norm.isEmpty()) {
            addSuggestion(results, seenLabels, "+Tinta", "4 de tinta", new ResourceLocation("minecraft", "ink_sac"), "DROP");
            addSuggestion(results, seenLabels, "+Madera", "16 de madera", new ResourceLocation("minecraft", "oak_log"), "CHOP");
            addSuggestion(results, seenLabels, "+4 Oro", "4 de oro", new ResourceLocation("minecraft", "gold_ore"), "MINE");
            addSuggestion(results, seenLabels, "+10 Hierro", "10 de hierro", new ResourceLocation("minecraft", "iron_ore"), "MINE");
            addSuggestion(results, seenLabels, "+Guardar Cofre", "guardar en cofre", new ResourceLocation("minecraft", "chest"), "ACTION");
            return results;
        }

        // Extract last token if query has spaces, commas, or conjunctions (e.g. "busca 4 de tin", "4 oro y mad")
        String token = norm;
        int lastSep = Math.max(norm.lastIndexOf(' '), Math.max(norm.lastIndexOf(','), norm.lastIndexOf('+')));
        if (lastSep >= 0 && lastSep < norm.length() - 1) {
            String candidate = norm.substring(lastSep + 1).trim();
            if (!candidate.isEmpty()) {
                token = candidate;
            }
        }

        // Clean out common prefixes like "de", numbers
        token = token.replaceAll("^\\d+", "").trim();
        if (token.startsWith("de ")) token = token.substring(3).trim();
        if (token.isEmpty()) token = norm;

        // 1. Match in Spanish dictionary by prefix or substring
        for (Map.Entry<String, ResourceLocation> entry : SPANISH_DICT.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(token) || key.contains(token) || token.contains(key)) {
                ResourceLocation id = entry.getValue();
                String disp = DISPLAY_NAMES.getOrDefault(id, key);
                String label = "+" + disp;
                if (label.length() > 16) label = label.substring(0, 14) + "..";
                addSuggestion(results, seenLabels, label, key, id, "ITEM");
                if (results.size() >= limit) return results;
            }
        }

        // 2. Match in Minecraft registry by item ID
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            String path = id.getPath();
            if (path.contains(token)) {
                String label = "+" + path.replace('_', ' ');
                if (label.length() > 16) label = label.substring(0, 14) + "..";
                addSuggestion(results, seenLabels, label, path, id, "REGISTRY");
                if (results.size() >= limit) return results;
            }
        }

        // Fallback default suggestions if nothing matched
        if (results.isEmpty()) {
            addSuggestion(results, seenLabels, "+Buscar " + token, "busca " + token, null, "CUSTOM");
            addSuggestion(results, seenLabels, "+Minar " + token, "mina " + token, null, "MINE");
            addSuggestion(results, seenLabels, "+Talar Árboles", "talar madera", new ResourceLocation("minecraft", "oak_log"), "CHOP");
            addSuggestion(results, seenLabels, "+Guardar Cofre", "guardar en cofre", null, "ACTION");
        }

        return results;
    }

    private static void addSuggestion(List<ItemSuggestion> list, Set<String> seen, String label, String insertText, ResourceLocation id, String category) {
        if (seen.add(label)) {
            list.add(new ItemSuggestion(label, insertText, id, category));
        }
    }

    public record SmartSuggestion(String label, String insertText, String canonicalId, String category) {
        public SmartSuggestion(String label, String value, String category) {
            this(label, value, value, category);
        }
        public String value() {
            return insertText;
        }
    }

    public static List<SmartSuggestion> getMaterialSuggestions(String query) {
        List<SmartSuggestion> list = new ArrayList<>();
        String norm = normalize(query);

        record MatOpt(String label, String insertText, String canonicalId, String category) {}
        List<MatOpt> all = List.of(
            // Single woods
            new MatOpt("Roble (Madera)", "Roble", "minecraft:oak_log", "SUBTYPE"),
            new MatOpt("Abedul (Madera)", "Abedul", "minecraft:birch_log", "SUBTYPE"),
            new MatOpt("Abeto (Madera)", "Abeto", "minecraft:spruce_log", "SUBTYPE"),
            new MatOpt("Cerezo (Madera)", "Cerezo", "minecraft:cherry_log", "SUBTYPE"),
            new MatOpt("Roble Oscuro (Madera)", "Roble Oscuro", "minecraft:dark_oak_log", "SUBTYPE"),
            new MatOpt("Manglar (Madera)", "Manglar", "minecraft:mangrove_log", "SUBTYPE"),
            new MatOpt("Bambú (Madera)", "Bambú", "minecraft:bamboo_block", "SUBTYPE"),
            new MatOpt("Acacia (Madera)", "Acacia", "minecraft:acacia_log", "SUBTYPE"),
            new MatOpt("Jungla (Madera)", "Jungla", "minecraft:jungle_log", "SUBTYPE"),

            // Single stones
            new MatOpt("Adoquín (Piedra)", "Adoquín", "minecraft:cobblestone", "SUBTYPE"),
            new MatOpt("Ladrillos de Piedra", "Ladrillos de Piedra", "minecraft:stone_bricks", "SUBTYPE"),
            new MatOpt("Pizarra Profunda (Deepslate)", "Pizarra Profunda", "minecraft:deepslate", "SUBTYPE"),
            new MatOpt("Arenisca (Piedra)", "Arenisca", "minecraft:sandstone", "SUBTYPE"),

            // Popular combos
            new MatOpt("Roble y Adoquín (Clásico)", "Roble y Adoquín", "minecraft:oak_log", "SUBTYPE"),
            new MatOpt("Cerezo y Ladrillos (Elegante)", "Cerezo y Ladrillos", "minecraft:cherry_log", "SUBTYPE"),
            new MatOpt("Abeto y Adoquín (Rústico)", "Abeto y Adoquín", "minecraft:spruce_log", "SUBTYPE"),
            new MatOpt("Roble Oscuro y Pizarra (Gótico)", "Roble Oscuro y Pizarra", "minecraft:dark_oak_log", "SUBTYPE"),
            new MatOpt("Abedul y Adoquín (Nórdico)", "Abedul y Adoquín", "minecraft:birch_log", "SUBTYPE"),
            new MatOpt("Bambú y Arenisca (Cálido)", "Bambú y Arenisca", "minecraft:bamboo_block", "SUBTYPE")
        );

        if (norm.isEmpty()) {
            for (MatOpt opt : all) {
                list.add(new SmartSuggestion(opt.label(), opt.insertText(), opt.canonicalId(), opt.category()));
            }
            return list;
        }

        for (MatOpt opt : all) {
            String normVal = normalize(opt.insertText());
            String normLab = normalize(opt.label());
            if (normVal.contains(norm) || normLab.contains(norm) || norm.contains(normVal)) {
                list.add(new SmartSuggestion(opt.label(), opt.insertText(), opt.canonicalId(), opt.category()));
            }
        }
        return list;
    }

    public static List<SmartSuggestion> getSmartSuggestions(String rawInput, int limit) {
        List<SmartSuggestion> results = new ArrayList<>();
        if (rawInput == null) return results;
        String trimmed = rawInput.trim();
        if (trimmed.isEmpty()) {
            return results; // Dropdown remains completely closed when input is empty
        }

        String norm = normalize(rawInput).trim();
        boolean endsWithSpace = rawInput.endsWith(" ");

        // 1. Separate clauses (after 'y', 'despues', comma, etc.)
        int lastSep = Math.max(norm.lastIndexOf(" y "), Math.max(norm.lastIndexOf(" despues "), Math.max(norm.lastIndexOf(","), norm.lastIndexOf(";"))));
        String currentClause = (lastSep >= 0 && lastSep < norm.length() - 2) ? norm.substring(lastSep + 2).trim() : norm;

        // Starter actions list
        record ActionDef(String label, String value) {}
        List<ActionDef> starterActions = List.of(
            new ActionDef("⛏ Minar", "minar"),
            new ActionDef("🌲 Talar", "talar"),
            new ActionDef("⚔ Cazar", "cazar"),
            new ActionDef("🌾 Recoger", "recoger"),
            new ActionDef("🔍 Conseguir", "conseguir"),
            new ActionDef("🧭 Ir a", "ir a"),
            new ActionDef("📦 Guardar en baúl", "guardar en baul"),
            new ActionDef("🎁 Traer a mí", "traer a mi"),
            new ActionDef("🏃 Seguirme", "sigueme"),
            new ActionDef("🛑 Esperar", "espera"),
            new ActionDef("🏠 Volver a base", "volver a base"),
            new ActionDef("🏗 Construir casa", "construir casa")
        );

        if (currentClause.isEmpty()) {
            for (ActionDef a : starterActions) {
                results.add(new SmartSuggestion(a.label(), a.value(), "ACTION"));
            }
            return results;
        }

        String[] tokens = currentClause.split("\\s+");
        String first = tokens.length > 0 ? tokens[0] : "";

        // If only 1 partial word without trailing space: filter actions
        if (tokens.length == 1 && !endsWithSpace) {
            for (ActionDef a : starterActions) {
                String normVal = normalize(a.value());
                String normLab = normalize(a.label());
                if (normVal.startsWith(first) || normLab.toLowerCase().contains(first)) {
                    results.add(new SmartSuggestion(a.label(), a.value(), "ACTION"));
                }
            }
            // Also search dictionary if user started typing an item directly (e.g. "mad")
            addDictionaryMatches(results, first, limit);
            return results;
        }

        // Identify action type
        boolean isMining = first.equals("mina") || first.equals("minar") || first.equals("picar") || first.equals("pica");
        boolean isChopping = first.equals("tala") || first.equals("talar") || first.equals("corta") || first.equals("cortar") || first.equals("madera") || first.equals("arbol") || first.equals("arboles");
        boolean isHunting = first.equals("caza") || first.equals("cazar") || first.equals("mata") || first.equals("matar");
        boolean isGathering = first.equals("recoge") || first.equals("recoger") || first.equals("cosecha") || first.equals("cosechar") || first.equals("junta") || first.equals("juntar");
        boolean isObtaining = first.equals("consigue") || first.equals("conseguir") || first.equals("busca") || first.equals("buscar") || first.equals("obtener") || first.equals("obten");
        boolean isGoTo = first.equals("ir") || first.equals("ve") || first.equals("viaja") || first.equals("camina");
        boolean isBuilding = first.equals("construye") || first.equals("construir") || first.equals("casa");

        if (isGoTo) {
            results.add(new SmartSuggestion("🏠 Base / Casa", "base", "DESTINATION"));
            results.add(new SmartSuggestion("📦 Cofre principal", "cofre", "DESTINATION"));
            results.add(new SmartSuggestion("⛏ Mina", "mina", "DESTINATION"));
            results.add(new SmartSuggestion("🌊 Río / Agua", "rio", "DESTINATION"));
            results.add(new SmartSuggestion("🌲 Bosque", "bosque", "DESTINATION"));
            results.add(new SmartSuggestion("👤 Conmigo", "conmigo", "DESTINATION"));
            return results;
        }

        if (isBuilding) {
            try {
                for (String customId : com.aicompanion.ai.building.BlueprintRegistry.getCustomSchematicIds()) {
                    String disp = com.aicompanion.ai.building.BlueprintRegistry.getFormatDisplayName(customId);
                    results.add(new SmartSuggestion("📁 " + disp, customId, "SCHEMATIC"));
                }
            } catch (Throwable ignored) {}
            results.add(new SmartSuggestion("🏠 Casa de supervivencia", "casa de supervivencia", "DESTINATION"));
            results.add(new SmartSuggestion("🏰 Casa mediana", "casa mediana", "DESTINATION"));
            results.add(new SmartSuggestion("🏢 Casa 2 pisos", "casa dos pisos", "DESTINATION"));
            results.add(new SmartSuggestion("🏕 Refugio rápido", "refugio rapido", "DESTINATION"));
            results.add(new SmartSuggestion("🗼 Torre de vigilancia", "torre", "DESTINATION"));
            results.add(new SmartSuggestion("📦 Almacén", "almacen", "DESTINATION"));
            results.add(new SmartSuggestion("🧱 Muralla defensiva", "muralla", "DESTINATION"));
            results.add(new SmartSuggestion("🌉 Puente", "puente", "DESTINATION"));
            results.add(new SmartSuggestion("🌾 Granja vallada", "granja", "DESTINATION"));
            return results;
        }

        // ================= PASO 2, 3, 4 PIPELINE =================
        boolean hasNumber = hasAnyNumber(tokens, endsWithSpace);
        boolean hasDest = hasDestination(tokens);
        boolean hasTarget = hasTargetItem(tokens, 1, endsWithSpace);

        // PASO 4: Si ya hay destino, la orden está completa
        if (hasDest) {
            return Collections.emptyList();
        }

        // PASO 4: Si ya tiene objetivo Y cantidad, sugerir destinos / qué hacer después
        if (hasTarget && hasNumber) {
            String destFilter = (!endsWithSpace && tokens.length > 0) ? tokens[tokens.length - 1].toLowerCase() : "";
            List<SmartSuggestion> dests = List.of(
                new SmartSuggestion("🎁 y traer a mí", "y traer a mi", "DESTINATION"),
                new SmartSuggestion("📦 y guardar en baúl", "y guardar en baul", "DESTINATION"),
                new SmartSuggestion("🏠 y volver a base", "y volver a base", "DESTINATION"),
                new SmartSuggestion("🏃 y seguirme", "y sigueme", "DESTINATION"),
                new SmartSuggestion("🎒 y en inventario", "y en inventario", "DESTINATION")
            );
            for (SmartSuggestion d : dests) {
                if (destFilter.isEmpty() || d.value().contains(destFilter) || d.label().toLowerCase().contains(destFilter)) {
                    results.add(d);
                }
            }
            return results;
        }

        // PASO 3: Si ya tiene objetivo pero NO cantidad, sugerir cantidades
        if (hasTarget && !hasNumber) {
            String numFilter = (!endsWithSpace && tokens.length > 0 && isNumber(tokens[tokens.length - 1])) ? tokens[tokens.length - 1] : "";
            List<SmartSuggestion> qtyList;
            if (isHunting) {
                qtyList = List.of(
                    new SmartSuggestion("5", "5", "QUANTITY"),
                    new SmartSuggestion("10", "10", "QUANTITY"),
                    new SmartSuggestion("15", "15", "QUANTITY"),
                    new SmartSuggestion("20", "20", "QUANTITY")
                );
            } else {
                qtyList = List.of(
                    new SmartSuggestion("16", "16", "QUANTITY"),
                    new SmartSuggestion("32 (Medio stack)", "32", "QUANTITY"),
                    new SmartSuggestion("64 (1 stack completo)", "64", "QUANTITY"),
                    new SmartSuggestion("128 (2 stacks)", "128", "QUANTITY"),
                    new SmartSuggestion("256 (4 stacks)", "256", "QUANTITY")
                );
            }
            for (SmartSuggestion q : qtyList) {
                if (numFilter.isEmpty() || q.value().startsWith(numFilter)) {
                    results.add(q);
                }
            }
            return results;
        }

        // PASO 2: No tiene objetivo aún -> sugerir objetos / materiales
        String filter = (tokens.length >= 2 && !endsWithSpace) ? tokens[tokens.length - 1] : "";
        Set<String> seenValues = new HashSet<>();

        if (isMining) {
            String[] ores = {
                "diamante", "hierro", "oro", "carbon", "cobre", "redstone", "esmeralda", "lapislazuli", "netherita",
                "piedra luminosa", "cuarzo", "piedra", "adoquin", "pizarra", "obsidiana",
                "madera", "roble", "abedul", "abeto", "roble_oscuro", "jungla", "acacia", "cerezo", "manglar", "bambu"
            };
            String[] insertNames = {
                "Diamante", "Hierro", "Oro", "Carbón", "Cobre", "Redstone", "Esmeralda", "Lapislázuli", "Netherita",
                "Piedra Luminosa", "Cuarzo", "Piedra", "Adoquín", "Pizarra Profunda", "Obsidiana",
                "Madera", "Roble", "Abedul", "Abeto", "Roble Oscuro", "Jungla", "Acacia", "Cerezo", "Manglar", "Bambú"
            };
            String[] labels = {
                "💎 Diamante", "⛏ Hierro", "🟡 Oro", "⚫ Carbón", "🟠 Cobre", "🔴 Redstone", "🟢 Esmeralda", "🔵 Lapislázuli", "🟣 Netherita",
                "💡 Piedra Luminosa", "⚪ Cuarzo", "🪨 Piedra", "🧱 Adoquín", "🪨 Pizarra Profunda", "🌌 Obsidiana",
                "🌲 Madera", "🌲 Roble", "🌲 Abedul", "🌲 Abeto", "🌲 Roble Oscuro", "🌲 Jungla", "🌲 Acacia", "🌸 Cerezo", "🌿 Manglar", "🎋 Bambú"
            };
            String[] canonical = {
                "minecraft:diamond_ore", "minecraft:iron_ore", "minecraft:gold_ore", "minecraft:coal_ore", "minecraft:copper_ore", "minecraft:redstone_ore", "minecraft:emerald_ore", "minecraft:lapis_ore", "minecraft:ancient_debris",
                "minecraft:glowstone", "minecraft:nether_quartz_ore", "minecraft:stone", "minecraft:cobblestone", "minecraft:deepslate", "minecraft:obsidian",
                "minecraft:oak_log", "minecraft:oak_log", "minecraft:birch_log", "minecraft:spruce_log", "minecraft:dark_oak_log", "minecraft:jungle_log", "minecraft:acacia_log", "minecraft:cherry_log", "minecraft:mangrove_log", "minecraft:bamboo"
            };
            for (int i = 0; i < ores.length; i++) {
                if (filter.isEmpty() || ores[i].contains(filter) || labels[i].toLowerCase().contains(filter) || insertNames[i].toLowerCase().contains(filter)) {
                    if (seenValues.add(ores[i])) {
                        results.add(new SmartSuggestion(labels[i], insertNames[i], canonical[i], "SUBTYPE"));
                    }
                }
            }
            if (!filter.isEmpty()) {
                addDictionaryMatches(results, seenValues, filter, limit);
            }
            // Bloques registrados (vanilla + mods del modpack)
            addRegistryMatches(results, seenValues, filter, e -> e.item() instanceof net.minecraft.world.item.BlockItem);
            return results;
        }

        if (isChopping) {
            String[] woods = {"madera", "roble", "abedul", "abeto", "roble_oscuro", "jungla", "acacia", "cerezo", "manglar", "bambu"};
            String[] insertNames = {"Madera", "Roble", "Abedul", "Abeto", "Roble Oscuro", "Jungla", "Acacia", "Cerezo", "Manglar", "Bambú"};
            String[] labels = {"🌲 Madera", "🌲 Roble", "🌲 Abedul", "🌲 Abeto", "🌲 Roble Oscuro", "🌲 Jungla", "🌲 Acacia", "🌸 Cerezo", "🌿 Manglar", "🎋 Bambú"};
            String[] canonical = {
                "minecraft:oak_log", "minecraft:oak_log", "minecraft:birch_log", "minecraft:spruce_log", "minecraft:dark_oak_log", "minecraft:jungle_log", "minecraft:acacia_log", "minecraft:cherry_log", "minecraft:mangrove_log", "minecraft:bamboo"
            };
            for (int i = 0; i < woods.length; i++) {
                if (filter.isEmpty() || woods[i].contains(filter) || labels[i].toLowerCase().contains(filter) || insertNames[i].toLowerCase().contains(filter)) {
                    if (seenValues.add(woods[i])) {
                        results.add(new SmartSuggestion(labels[i], insertNames[i], canonical[i], "SUBTYPE"));
                    }
                }
            }
            if (!filter.isEmpty()) {
                addDictionaryMatches(results, seenValues, filter, limit);
            }
            // Troncos de todos los mods (cualquier bloque *_log / *_stem / *_wood / *_hyphae)
            addRegistryMatches(results, seenValues, filter, e -> e.item() instanceof net.minecraft.world.item.BlockItem
                    && (e.id().getPath().endsWith("_log") || e.id().getPath().endsWith("_stem") || e.id().getPath().endsWith("_wood") || e.id().getPath().endsWith("_hyphae")));
            return results;
        }

        if (isHunting) {
            String[] mobs = {"vaca", "cerdo", "oveja", "pollo", "zombie", "esqueleto", "arana", "creeper", "calamar", "enderman", "blaze"};
            String[] insertNames = {"Vaca", "Cerdo", "Oveja", "Pollo", "Zombi", "Esqueleto", "Araña", "Creeper", "Calamar", "Enderman", "Blaze"};
            String[] labels = {"🐄 Vaca (Carne/Cuero)", "🐖 Cerdo (Comida)", "🐑 Oveja (Lana)", "🐔 Pollo (Plumas)", "🧟 Zombi (Carne)", "🏹 Esqueleto (Huesos)", "🕷 Araña (Hilo)", "🧨 Creeper (Pólvora)", "🐙 Calamar (Tinta)", "🔮 Enderman (Perlas)", "🔥 Blaze (Varas)"};
            String[] canonical = {
                "minecraft:cow", "minecraft:pig", "minecraft:sheep", "minecraft:chicken", "minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:creeper", "minecraft:squid", "minecraft:enderman", "minecraft:blaze"
            };
            for (int i = 0; i < mobs.length; i++) {
                if (filter.isEmpty() || mobs[i].contains(filter) || labels[i].toLowerCase().contains(filter) || insertNames[i].toLowerCase().contains(filter)) {
                    if (seenValues.add(mobs[i])) {
                        results.add(new SmartSuggestion(labels[i], insertNames[i], canonical[i], "SUBTYPE"));
                    }
                }
            }
            if (!filter.isEmpty()) {
                addDictionaryMatches(results, seenValues, filter, limit);
            }
            return results;
        }

        if (isGathering) {
            String[] crops = {"trigo", "zanahorias", "patatas", "cana_de_azucar", "semillas", "flores", "manzanas", "bambu", "sandia"};
            String[] insertNames = {"Trigo", "Zanahorias", "Patatas", "Caña de Azúcar", "Semillas", "Flores", "Manzanas", "Bambú", "Sandía"};
            String[] labels = {"🌾 Trigo", "🥕 Zanahorias", "🥔 Patatas", "🎋 Caña de Azúcar", "🌱 Semillas", "🌸 Flores", "🍎 Manzanas", "🎋 Bambú", "🍉 Sandía"};
            String[] canonical = {
                "minecraft:wheat", "minecraft:carrot", "minecraft:potato", "minecraft:sugar_cane", "minecraft:wheat_seeds", "minecraft:dandelion", "minecraft:apple", "minecraft:bamboo", "minecraft:melon_slice"
            };
            for (int i = 0; i < crops.length; i++) {
                if (filter.isEmpty() || crops[i].contains(filter) || labels[i].toLowerCase().contains(filter) || insertNames[i].toLowerCase().contains(filter)) {
                    if (seenValues.add(crops[i])) {
                        results.add(new SmartSuggestion(labels[i], insertNames[i], canonical[i], "SUBTYPE"));
                    }
                }
            }
            if (!filter.isEmpty()) {
                addDictionaryMatches(results, seenValues, filter, limit);
            }
            addRegistryMatches(results, seenValues, filter, e -> true);
            return results;
        }

        // Generic obtaining / search
        if (filter.isEmpty()) {
            results.add(new SmartSuggestion("💎 Diamante", "Diamante", "minecraft:diamond", "ITEM"));
            results.add(new SmartSuggestion("⛏ Hierro", "Hierro", "minecraft:iron_ingot", "ITEM"));
            results.add(new SmartSuggestion("🌲 Madera", "Madera", "minecraft:oak_log", "ITEM"));
            results.add(new SmartSuggestion("🥩 Comida", "Comida", "minecraft:cooked_beef", "ITEM"));
            results.add(new SmartSuggestion("🪨 Piedra", "Piedra", "minecraft:stone", "ITEM"));
        } else {
            addDictionaryMatches(results, seenValues, filter, limit);
        }
        // Cualquier objeto del juego y de los mods con nombres localizados reales
        addRegistryMatches(results, seenValues, filter, e -> true);
        return results;
    }

    /** Nombre bonito del mod (p. ej. "Create") a partir de su id, con caché. */
    private static final Map<String, String> MOD_NAMES = new HashMap<>();

    private static String modDisplayName(String modId) {
        return MOD_NAMES.computeIfAbsent(modId, id -> net.minecraftforge.fml.ModList.get()
                .getModContainerById(id)
                .map(c -> c.getModInfo().getDisplayName())
                .orElse(id));
    }

    /**
     * Añade objetos/bloques de TODO el registro del juego (vanilla + mods instalados).
     * El texto insertado en la orden es el nombre visible en el idioma de Minecraft (p. ej. "Piedra luminosa", "Rueda dentada de latón"),
     * mientras que el identificador técnico canónico (p. ej. "minecraft:glowstone", "create:brass_cogwheel") se conserva internamente.
     */
    private static void addRegistryMatches(List<SmartSuggestion> results, Set<String> seenValues, String filter,
                                           java.util.function.Predicate<CatalogEntry> accept) {
        String f = normalize(filter);
        List<CatalogEntry> starts = new ArrayList<>();
        List<CatalogEntry> contains = new ArrayList<>();
        for (CatalogEntry e : getAllCatalogEntries()) {
            if (!accept.test(e)) continue;
            if (f.isEmpty()) {
                contains.add(e);
                continue;
            }
            String disp = normalize(e.displayName());
            String path = e.id().getPath();
            if (disp.startsWith(f) || path.startsWith(f)) {
                starts.add(e);
            } else if (disp.contains(f) || path.contains(f) || e.modId().startsWith(f)) {
                contains.add(e);
            }
        }
        for (List<CatalogEntry> group : List.of(starts, contains)) {
            for (CatalogEntry e : group) {
                String canonicalId = e.id().toString();
                if (!seenValues.add(canonicalId)) continue;
                String insertText = e.displayName();
                String label = "minecraft".equals(e.modId()) ? e.displayName() : e.displayName() + " · " + modDisplayName(e.modId());
                results.add(new SmartSuggestion(label, insertText, canonicalId, "ITEM"));
            }
        }
    }

    public static boolean hasTargetItem(String[] tokens, int startIndex, boolean endsWithSpace) {
        int maxIndex = endsWithSpace ? tokens.length : tokens.length - 1;
        for (int i = startIndex; i < maxIndex; i++) {
            String t = tokens[i].trim();
            if (!isNumber(t) && isRecognizedTarget(t)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRecognizedTarget(String word) {
        if (word == null || word.isEmpty()) return false;
        String w = normalize(word);
        if (SPANISH_DICT.containsKey(w)) return true;
        if (w.contains(":")) {
            try {
                ResourceLocation rl = new ResourceLocation(w);
                if (BuiltInRegistries.ITEM.containsKey(rl) || BuiltInRegistries.BLOCK.containsKey(rl)) return true;
            } catch (Exception ignored) {}
        }
        for (String ore : new String[]{"hierro", "oro", "diamante", "carbon", "cobre", "redstone", "esmeralda", "lapislazuli", "lapis", "netherita", "adoquin", "piedra", "obsidiana", "pizarra"}) {
            if (w.equals(ore)) return true;
        }
        for (String wood : new String[]{"roble", "abedul", "abeto", "jungla", "acacia", "cerezo", "manglar", "bambu", "madera", "arbol", "tronco"}) {
            if (w.equals(wood)) return true;
        }
        for (String mob : new String[]{"vaca", "cerdo", "oveja", "pollo", "zombie", "esqueleto", "arana", "creeper", "calamar", "enderman", "blaze"}) {
            if (w.equals(mob)) return true;
        }
        for (String crop : new String[]{"trigo", "zanahoria", "patata", "papa", "semilla", "flor", "manzana", "sandia", "calabaza", "azucar"}) {
            if (w.equals(crop)) return true;
        }
        return false;
    }

    public static boolean hasDestination(String[] tokens) {
        for (String t : tokens) {
            String w = normalize(t);
            if (w.equals("traer") || w.equals("trae") || w.equals("guardar") || w.equals("guarda") ||
                w.equals("baul") || w.equals("cofre") || w.equals("base") || w.equals("sigueme") ||
                w.equals("inventario") || w.equals("depositar") || w.equals("deposita")) {
                return true;
            }
        }
        return false;
    }

    private static void addDictionaryMatches(List<SmartSuggestion> results, String token, int limit) {
        addDictionaryMatches(results, null, token, limit);
    }

    private static void addDictionaryMatches(List<SmartSuggestion> results, Set<String> seenValues, String token, int limit) {
        if (token.isEmpty()) return;
        for (Map.Entry<String, ResourceLocation> entry : SPANISH_DICT.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(token) || key.contains(token)) {
                if (seenValues == null || seenValues.add(key)) {
                    String disp = DISPLAY_NAMES.getOrDefault(entry.getValue(), key);
                    String canonicalId = entry.getValue().toString();
                    results.add(new SmartSuggestion(disp, disp, canonicalId, "ITEM"));
                    if (results.size() >= limit) return;
                }
            }
        }
    }

    private static boolean hasAnyNumber(String[] tokens, boolean endsWithSpace) {
        int maxIndex = endsWithSpace ? tokens.length : tokens.length - 1;
        for (int i = 0; i < maxIndex; i++) {
            if (isNumber(tokens[i])) return true;
        }
        return false;
    }

    private static boolean hasAnyMatch(String[] tokens, int startIndex, String[] pool) {
        for (int i = startIndex; i < tokens.length; i++) {
            String t = tokens[i].toLowerCase();
            for (String p : pool) {
                if (t.equals(p) || (p.contains(t) && t.length() >= 3)) return true;
            }
        }
        return false;
    }

    private static boolean isNumber(String s) {
        if (s == null || s.isEmpty()) return false;
        return s.matches("\\d+");
    }

    public record CatalogEntry(Item item, ResourceLocation id, String displayName, String modId) {}

    private static List<CatalogEntry> cachedAllEntries = null;

    public static List<CatalogEntry> getAllCatalogEntries() {
        if (cachedAllEntries == null) {
            List<CatalogEntry> list = new ArrayList<>();
            for (Map.Entry<net.minecraft.resources.ResourceKey<Item>, Item> entry : BuiltInRegistries.ITEM.entrySet()) {
                Item item = entry.getValue();
                if (item == net.minecraft.world.item.Items.AIR) continue;
                ResourceLocation id = entry.getKey().location();
                String disp = item.getName(new net.minecraft.world.item.ItemStack(item)).getString();
                list.add(new CatalogEntry(item, id, disp, id.getNamespace()));
            }
            list.sort(Comparator.comparing(CatalogEntry::displayName, String.CASE_INSENSITIVE_ORDER));
            cachedAllEntries = list;
        }
        return cachedAllEntries;
    }

    public static List<CatalogEntry> searchCatalog(String query) {
        List<CatalogEntry> all = getAllCatalogEntries();
        if (query == null || query.trim().isEmpty()) {
            return all;
        }
        String q = normalize(query.trim());
        List<CatalogEntry> matched = new ArrayList<>();

        ResourceLocation dictLoc = SPANISH_DICT.get(q);

        for (CatalogEntry entry : all) {
            if (dictLoc != null && entry.id().equals(dictLoc)) {
                matched.add(0, entry);
                continue;
            }
            String dispNorm = normalize(entry.displayName());
            String pathNorm = entry.id().getPath().toLowerCase();
            String fullId = entry.id().toString().toLowerCase();

            if (dispNorm.contains(q) || pathNorm.contains(q) || fullId.contains(q)) {
                matched.add(entry);
            }
        }
        return matched;
    }
}
