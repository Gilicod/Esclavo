package com.aicompanion.ai.building;

import java.util.*;

public class BlueprintRegistry {
    private static final Map<String, Blueprint> BLUEPRINTS = new HashMap<>();

    static {
        // 1. Survival House (Small & Medium)
        register(ProceduralTemplates.createHouse("small_house", 5, ProceduralTemplates.WoodType.OAK, ProceduralTemplates.StoneType.COBBLESTONE));
        registerAlias("casa", "small_house");
        registerAlias("casa_pequena", "small_house");
        registerAlias("casa_basica", "small_house");

        register(ProceduralTemplates.createHouse("medium_house", 7, ProceduralTemplates.WoodType.OAK, ProceduralTemplates.StoneType.STONE_BRICKS));
        registerAlias("casa_grande", "medium_house");
        registerAlias("mansion", "medium_house");

        // 2. Fast Survival Shelter
        register(ProceduralTemplates.createShelter("shelter"));
        registerAlias("refugio", "shelter");
        registerAlias("choza", "shelter");

        // 3. Defensive Watchtower
        register(ProceduralTemplates.createWatchtower("watchtower", 9));
        registerAlias("torre", "watchtower");
        registerAlias("torre_vigilancia", "watchtower");

        // 4. Sturdy Bridge
        register(ProceduralTemplates.createBridge("bridge", 12));
        registerAlias("puente", "bridge");

        // 5. Irrigated Farm
        register(ProceduralTemplates.createFarm("farm"));
        registerAlias("granja", "farm");
        registerAlias("huerto", "farm");

        // 6. Two Story House
        register(ProceduralTemplates.createTwoStoryHouse("two_story_house", 7, ProceduralTemplates.WoodType.OAK, ProceduralTemplates.StoneType.COBBLESTONE));
        registerAlias("casa_dos_pisos", "two_story_house");
        registerAlias("casa_2_pisos", "two_story_house");
        registerAlias("mansion_dos_pisos", "two_story_house");

        // 7. Warehouse
        register(ProceduralTemplates.createWarehouse("warehouse", ProceduralTemplates.WoodType.OAK, ProceduralTemplates.StoneType.STONE_BRICKS));
        registerAlias("almacen", "warehouse");
        registerAlias("bodega", "warehouse");
        registerAlias("deposito", "warehouse");

        // 8. Defensive Wall
        register(ProceduralTemplates.createDefensiveWall("defensive_wall", 12, ProceduralTemplates.StoneType.STONE_BRICKS));
        registerAlias("muralla", "defensive_wall");
        registerAlias("muro", "defensive_wall");
        registerAlias("pared_defensiva", "defensive_wall");
    }

    public static void register(Blueprint blueprint) {
        BLUEPRINTS.put(blueprint.getName().toLowerCase(), blueprint);
    }

    public static void registerAlias(String alias, String target) {
        Blueprint bp = BLUEPRINTS.get(target.toLowerCase());
        if (bp != null) {
            BLUEPRINTS.put(alias.toLowerCase(), bp);
        }
    }

    public static Blueprint getCustom(String name, String woodMat, String stoneMat) {
        ProceduralTemplates.WoodType wood = ProceduralTemplates.WoodType.parseWood(woodMat);
        ProceduralTemplates.StoneType stone = ProceduralTemplates.StoneType.parseStone(stoneMat);

        String clean = name != null ? name.trim().toLowerCase().replace(" ", "_") : "small_house";
        if (clean.contains("two_story") || clean.contains("dos_pisos") || clean.contains("2_pisos")) {
            return ProceduralTemplates.createTwoStoryHouse("two_story_house_" + woodMat + "_" + stoneMat, 7, wood, stone);
        }
        if (clean.contains("warehouse") || clean.contains("almacen") || clean.contains("bodega")) {
            return ProceduralTemplates.createWarehouse("warehouse_" + woodMat + "_" + stoneMat, wood, stone);
        }
        if (clean.contains("wall") || clean.contains("muralla") || clean.contains("muro")) {
            return ProceduralTemplates.createDefensiveWall("defensive_wall_" + stoneMat, 12, stone);
        }
        if (clean.contains("medium") || clean.contains("grande")) {
            return ProceduralTemplates.createHouse("medium_house_" + woodMat + "_" + stoneMat, 7, wood, stone);
        }
        if (clean.contains("shelter") || clean.contains("refugio")) {
            return ProceduralTemplates.createShelter("shelter");
        }
        if (clean.contains("watchtower") || clean.contains("torre")) {
            return ProceduralTemplates.createWatchtower("watchtower", 9);
        }
        if (clean.contains("bridge") || clean.contains("puente")) {
            return ProceduralTemplates.createBridge("bridge", 12);
        }
        if (clean.contains("farm") || clean.contains("granja")) {
            return ProceduralTemplates.createFarm("farm");
        }
        return ProceduralTemplates.createHouse("small_house_" + woodMat + "_" + stoneMat, 5, wood, stone);
    }

    private static final Set<String> PROCEDURAL_NAMES = new HashSet<>(Arrays.asList(
            "small_house", "medium_house", "two_story_house", "shelter", "watchtower",
            "warehouse", "defensive_wall", "bridge", "farm",
            "casa", "casa_pequena", "casa_basica", "casa_grande", "mansion", "refugio",
            "choza", "torre", "torre_vigilancia", "puente", "granja", "huerto",
            "casa_dos_pisos", "casa_2_pisos", "mansion_dos_pisos", "almacen", "bodega",
            "deposito", "muralla", "muro", "pared_defensiva"
    ));

    public static boolean isProcedural(String name) {
        if (name == null) return false;
        String clean = name.trim().toLowerCase().replace(" ", "_");
        if (clean.contains(":")) {
            clean = clean.split(":")[0];
        }
        return PROCEDURAL_NAMES.contains(clean) || clean.startsWith("small_house") || clean.startsWith("medium_house")
                || clean.startsWith("two_story_house") || clean.startsWith("shelter") || clean.startsWith("watchtower")
                || clean.startsWith("warehouse") || clean.startsWith("defensive_wall") || clean.startsWith("bridge") || clean.startsWith("farm");
    }

    public static boolean isCustomSchematic(String name) {
        if (name == null) return false;
        return !isProcedural(name);
    }

    public static List<String> getCustomSchematicIds() {
        Set<String> customIds = new LinkedHashSet<>();
        java.io.File dir = new java.io.File("config/esclavo/schematics");
        if (dir.exists() && dir.isDirectory()) {
            java.io.File[] files = dir.listFiles((d, n) -> {
                String lower = n.toLowerCase();
                return lower.endsWith(".schem") || lower.endsWith(".nbt") || lower.endsWith(".schematic");
            });
            if (files != null) {
                for (java.io.File f : files) {
                    String id = f.getName().replaceAll("(?i)\\.(schem|nbt|schematic)$", "").toLowerCase();
                    customIds.add(id);
                    if (!BLUEPRINTS.containsKey(id)) {
                        SchematicImporter.importAndRegister(f);
                    }
                }
            }
        }
        for (String key : BLUEPRINTS.keySet()) {
            if (!isProcedural(key)) {
                customIds.add(key);
            }
        }
        List<String> list = new ArrayList<>(customIds);
        Collections.sort(list);
        return list;
    }

    public static String getFormatDisplayName(String id) {
        if (id == null || id.isEmpty()) return "Casa";
        String[] words = id.replace("-", " ").replace("_", " ").trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) {
                sb.append(w.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    public static Blueprint get(String name) {
        if (name == null) return null;
        if (HouseSpec.isEncoded(name)) {
            return ProceduralTemplates.createHouse(HouseSpec.decode(name));
        }
        String clean = name.trim().toLowerCase().replace(" ", "_");
        if (clean.contains(":")) {
            String[] parts = clean.split(":");
            String bp = parts.length > 0 ? parts[0] : "small_house";
            String wood = parts.length > 1 ? parts[1] : "oak";
            String stone = parts.length > 2 ? parts[2] : "cobblestone";
            if (isProcedural(bp)) {
                return getCustom(bp, wood, stone);
            } else {
                Blueprint loaded = get(bp);
                if (loaded != null) return loaded;
            }
        }

        String baseName = clean.replaceAll("(?i)\\.(schem|nbt|schematic)$", "");

        // 1. Direct memory lookup
        Blueprint direct = BLUEPRINTS.get(baseName);
        if (direct != null) return direct;
        direct = BLUEPRINTS.get(clean);
        if (direct != null) return direct;

        // 2. Disk scan in config/esclavo/schematics/
        java.io.File dir = new java.io.File("config/esclavo/schematics");
        if (dir.exists()) {
            for (String ext : new String[]{"", ".nbt", ".schem", ".schematic"}) {
                java.io.File file = new java.io.File(dir, baseName + ext);
                if (file.exists() && file.isFile()) {
                    Blueprint imported = SchematicImporter.importAndRegister(file);
                    if (imported != null) return imported;
                }
            }
            java.io.File[] files = dir.listFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    String fBase = f.getName().replaceAll("(?i)\\.(schem|nbt|schematic)$", "").toLowerCase();
                    if (fBase.equalsIgnoreCase(baseName)) {
                        Blueprint imported = SchematicImporter.importAndRegister(f);
                        if (imported != null) return imported;
                    }
                }
            }
        }

        // 3. Fallback matching in registered blueprints
        for (Map.Entry<String, Blueprint> entry : BLUEPRINTS.entrySet()) {
            if (baseName.equals(entry.getKey()) || baseName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 4. If it was intended as a procedural building or alias
        if (isProcedural(clean)) {
            return getCustom(clean, "oak", "cobblestone");
        }

        return null;
    }

    public static Set<String> getAvailable() {
        getCustomSchematicIds();
        return Collections.unmodifiableSet(BLUEPRINTS.keySet());
    }
}
