package com.aicompanion.client.gui.building;

import com.aicompanion.ai.building.Blueprint;
import com.aicompanion.ai.building.BlueprintRegistry;
import com.aicompanion.ai.building.SchematicImporter;
import net.minecraft.Util;
import net.minecraft.core.Vec3i;

import java.io.File;
import java.util.*;

/**
 * Manages discovery, caching, searching, and metadata for both custom schematics
 * (config/esclavo/schematics/ and schematics/) and built-in procedural templates.
 */
public class SchematicLibraryManager {

    public record SchematicEntry(
            String id,
            String displayName,
            String format,
            String category,
            Vec3i dimensions,
            int blockCount,
            boolean isCustom,
            File file
    ) {}

    private static final List<SchematicEntry> ALL_ENTRIES = new ArrayList<>();
    private static final Set<String> FAVORITES = new HashSet<>();
    private static long lastScanTime = 0;

    public static List<SchematicEntry> getEntries(String searchQuery) {
        if (ALL_ENTRIES.isEmpty() || System.currentTimeMillis() - lastScanTime > 3000) {
            refreshLibrary();
        }

        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            return Collections.unmodifiableList(ALL_ENTRIES);
        }

        String filter = searchQuery.trim().toLowerCase();
        List<SchematicEntry> filtered = new ArrayList<>();
        for (SchematicEntry entry : ALL_ENTRIES) {
            if (entry.displayName().toLowerCase().contains(filter)
                    || entry.id().toLowerCase().contains(filter)
                    || entry.category().toLowerCase().contains(filter)
                    || entry.format().toLowerCase().contains(filter)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    public static synchronized void refreshLibrary() {
        lastScanTime = System.currentTimeMillis();
        ALL_ENTRIES.clear();
        Set<String> seenIds = new HashSet<>();

        // 1. Scan primary folder config/esclavo/schematics/
        File primaryDir = new File("config/esclavo/schematics");
        if (!primaryDir.exists()) primaryDir.mkdirs();
        scanDirectory(primaryDir, seenIds);

        // 2. Scan secondary optional folder schematics/
        File secondaryDir = new File("schematics");
        if (secondaryDir.exists() && secondaryDir.isDirectory()) {
            scanDirectory(secondaryDir, seenIds);
        }

        // 3. Register and list Built-in Procedural Templates
        addProcedural("small_house", "Casa de Supervivencia (5x5)", new Vec3i(5, 5, 5), 85);
        addProcedural("medium_house", "Casa Mediana (7x7)", new Vec3i(7, 6, 7), 160);
        addProcedural("two_story_house", "Casa 2 Pisos (7x7)", new Vec3i(7, 10, 7), 280);
        addProcedural("shelter", "Refugio Rápido (4x4)", new Vec3i(4, 3, 4), 38);
        addProcedural("watchtower", "Torre de Vigilancia", new Vec3i(5, 12, 5), 110);
        addProcedural("warehouse", "Almacén / Bodega", new Vec3i(9, 6, 9), 210);
        addProcedural("defensive_wall", "Muralla Defensiva", new Vec3i(12, 6, 3), 144);
        addProcedural("bridge", "Puente Sturdy", new Vec3i(3, 3, 12), 72);
        addProcedural("farm", "Granja Vallada", new Vec3i(9, 2, 9), 81);
    }

    private static void scanDirectory(File dir, Set<String> seenIds) {
        File[] files = dir.listFiles((d, n) -> {
            String lower = n.toLowerCase();
            return lower.endsWith(".schem") || lower.endsWith(".nbt") || lower.endsWith(".schematic");
        });
        if (files == null) return;

        for (File f : files) {
            String rawName = f.getName();
            String id = rawName.replaceAll("(?i)\\.(schem|nbt|schematic)$", "").toLowerCase();
            if (seenIds.contains(id)) continue;
            seenIds.add(id);

            String ext = "";
            int dot = rawName.lastIndexOf('.');
            if (dot >= 0) ext = rawName.substring(dot).toUpperCase();

            // Import or retrieve blueprint metadata
            Blueprint bp = BlueprintRegistry.get(id);
            if (bp == null) {
                bp = SchematicImporter.importAndRegister(f);
            }

            Vec3i dims = bp != null ? bp.getDimensions() : new Vec3i(0, 0, 0);
            int blockCount = bp != null ? bp.getBlocks().size() : 0;
            String disp = BlueprintRegistry.getFormatDisplayName(id);

            ALL_ENTRIES.add(new SchematicEntry(
                    id,
                    disp,
                    ext,
                    "Mis Schematics",
                    dims,
                    blockCount,
                    true,
                    f
            ));
        }
    }

    private static void addProcedural(String id, String name, Vec3i dims, int approxBlocks) {
        Blueprint bp = BlueprintRegistry.get(id);
        if (bp != null) {
            dims = bp.getDimensions();
            approxBlocks = bp.getBlocks().size();
        }
        ALL_ENTRIES.add(new SchematicEntry(
                id,
                name,
                "Plantilla",
                "Plantillas",
                dims,
                approxBlocks,
                false,
                null
        ));
    }

    public static void openSchematicsFolder() {
        try {
            File dir = new File("config/esclavo/schematics");
            if (!dir.exists()) dir.mkdirs();
            Util.getPlatform().openFile(dir);
        } catch (Exception ignored) {}
    }

    public static boolean isFavorite(String id) {
        return FAVORITES.contains(id);
    }

    public static void toggleFavorite(String id) {
        if (FAVORITES.contains(id)) {
            FAVORITES.remove(id);
        } else {
            FAVORITES.add(id);
        }
    }
}
