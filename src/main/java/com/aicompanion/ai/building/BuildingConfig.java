package com.aicompanion.ai.building;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Encapsulates all user selections and customization options for constructing a blueprint.
 * Can be serialized to a compact string for network packets between client and server.
 */
public class BuildingConfig {

    public enum BuildMode {
        ORIGINAL("Original", "Conserva los bloques y decoraciones originales del archivo."),
        SINGLE_MATERIAL("Material Único", "Reemplaza paredes y techos por un solo material, conservando funciones."),
        SINGLE_MATERIAL_HOLLOW("Material Único + Vacío", "Exterior del material elegido, silueta completa pero interior vacío."),
        EXTERIOR_ONLY("Solo Exterior", "Construye únicamente fachadas exteriores y techo, sin divisiones internas."),
        BASIC_STRUCTURE("Estructura Básica", "Cimientos, columnas, muros y techo; sin cristales ni muebles."),
        NO_DECORATION("Sin Decoración", "Conserva habitaciones y divisiones pero omite muebles y adornos."),
        CUSTOM("Personalizado", "Configura manualmente qué elementos estructurales incluir o excluir.");

        private final String displayName;
        private final String description;

        BuildMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum MaterialMode {
        FAMILY_SUBSTITUTION("Por Familia (Escaleras, losas y muros coherentes)"),
        LITERAL("Literal (Todo al mismo bloque exacto)");

        private final String displayName;

        MaterialMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum PlacementMode {
        NEAR_PLAYER("Cerca de mí"),
        NEAR_COMPANION("Cerca de Esclavo"),
        MANUAL_COORDS("Coordenadas exactas");

        private final String displayName;

        PlacementMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Main Properties
    private String blueprintId = "small_house";
    private BuildMode buildMode = BuildMode.ORIGINAL;
    private MaterialMode materialMode = MaterialMode.FAMILY_SUBSTITUTION;
    private ResourceLocation primaryMaterial = new ResourceLocation("minecraft", "cobblestone");
    private int rotation = 0; // 0, 90, 180, 270

    // Custom toggles
    private boolean keepDoors = true;
    private boolean keepWindows = true;
    private boolean keepStairs = true;
    private boolean keepRoof = true;
    private boolean keepFloors = true;
    private boolean keepInteriorWalls = true;
    private boolean keepFurniture = true;
    private boolean keepDecor = true;
    private boolean keepLighting = true;
    private boolean keepChests = false; // Safe default: empty chests only if explicitly enabled
    private boolean keepFoundations = true;

    // Placement
    private PlacementMode placementMode = PlacementMode.NEAR_PLAYER;
    private BlockPos targetPos = null;
    private boolean autoGather = false;
    private boolean forceNew = false;

    public BuildingConfig() {}

    public BuildingConfig(String blueprintId) {
        this.blueprintId = blueprintId;
    }

    public void copyFrom(BuildingConfig other) {
        if (other == null) return;
        this.blueprintId = other.blueprintId;
        this.buildMode = other.buildMode;
        this.materialMode = other.materialMode;
        this.primaryMaterial = other.primaryMaterial;
        this.rotation = other.rotation;
        this.keepDoors = other.keepDoors;
        this.keepWindows = other.keepWindows;
        this.keepStairs = other.keepStairs;
        this.keepRoof = other.keepRoof;
        this.keepFloors = other.keepFloors;
        this.keepInteriorWalls = other.keepInteriorWalls;
        this.keepFurniture = other.keepFurniture;
        this.keepDecor = other.keepDecor;
        this.keepLighting = other.keepLighting;
        this.keepChests = other.keepChests;
        this.keepFoundations = other.keepFoundations;
        this.placementMode = other.placementMode;
        this.targetPos = other.targetPos;
        this.autoGather = other.autoGather;
        this.forceNew = other.forceNew;
    }

    public boolean isForceNew() {
        return forceNew;
    }

    public void setForceNew(boolean forceNew) {
        this.forceNew = forceNew;
    }

    // Getters and Setters
    public String getBlueprintId() { return blueprintId; }
    public void setBlueprintId(String id) { this.blueprintId = id != null ? id : "small_house"; }

    public BuildMode getBuildMode() { return buildMode; }
    public void setBuildMode(BuildMode mode) { this.buildMode = mode != null ? mode : BuildMode.ORIGINAL; }

    public MaterialMode getMaterialMode() { return materialMode; }
    public void setMaterialMode(MaterialMode mode) { this.materialMode = mode != null ? mode : MaterialMode.FAMILY_SUBSTITUTION; }

    public ResourceLocation getPrimaryMaterial() { return primaryMaterial; }
    public void setPrimaryMaterial(ResourceLocation loc) { this.primaryMaterial = loc != null ? loc : new ResourceLocation("minecraft", "cobblestone"); }

    public int getRotation() { return rotation; }
    public void setRotation(int rot) { this.rotation = ((rot % 360) + 360) % 360; }

    public boolean isKeepDoors() { return keepDoors; }
    public void setKeepDoors(boolean val) { this.keepDoors = val; }

    public boolean isKeepWindows() { return keepWindows; }
    public void setKeepWindows(boolean val) { this.keepWindows = val; }

    public boolean isKeepStairs() { return keepStairs; }
    public void setKeepStairs(boolean val) { this.keepStairs = val; }

    public boolean isKeepRoof() { return keepRoof; }
    public void setKeepRoof(boolean val) { this.keepRoof = val; }

    public boolean isKeepFloors() { return keepFloors; }
    public void setKeepFloors(boolean val) { this.keepFloors = val; }

    public boolean isKeepInteriorWalls() { return keepInteriorWalls; }
    public void setKeepInteriorWalls(boolean val) { this.keepInteriorWalls = val; }

    public boolean isKeepFurniture() { return keepFurniture; }
    public void setKeepFurniture(boolean val) { this.keepFurniture = val; }

    public boolean isKeepDecor() { return keepDecor; }
    public void setKeepDecor(boolean val) { this.keepDecor = val; }

    public boolean isKeepLighting() { return keepLighting; }
    public void setKeepLighting(boolean val) { this.keepLighting = val; }

    public boolean isKeepChests() { return keepChests; }
    public void setKeepChests(boolean val) { this.keepChests = val; }

    public boolean isKeepFoundations() { return keepFoundations; }
    public void setKeepFoundations(boolean val) { this.keepFoundations = val; }

    public PlacementMode getPlacementMode() { return placementMode; }
    public void setPlacementMode(PlacementMode mode) { this.placementMode = mode != null ? mode : PlacementMode.NEAR_PLAYER; }

    public BlockPos getTargetPos() { return targetPos; }
    public void setTargetPos(BlockPos pos) { this.targetPos = pos; }

    public boolean isAutoGather() { return autoGather; }
    public void setAutoGather(boolean val) { this.autoGather = val; }

    /**
     * Serializes this configuration into a URL/token-style string suitable for C2S_ActionPacket payload.
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder("cfg:");
        sb.append("id=").append(blueprintId);
        sb.append(";mode=").append(buildMode.name());
        sb.append(";matMode=").append(materialMode.name());
        sb.append(";mat=").append(primaryMaterial.toString());
        sb.append(";rot=").append(rotation);
        sb.append(";doors=").append(keepDoors);
        sb.append(";win=").append(keepWindows);
        sb.append(";stairs=").append(keepStairs);
        sb.append(";roof=").append(keepRoof);
        sb.append(";floors=").append(keepFloors);
        sb.append(";inwalls=").append(keepInteriorWalls);
        sb.append(";furn=").append(keepFurniture);
        sb.append(";decor=").append(keepDecor);
        sb.append(";light=").append(keepLighting);
        sb.append(";chests=").append(keepChests);
        sb.append(";found=").append(keepFoundations);
        sb.append(";place=").append(placementMode.name());
        if (targetPos != null) {
            sb.append(";pos=").append(targetPos.getX()).append(",").append(targetPos.getY()).append(",").append(targetPos.getZ());
        }
        if (autoGather) {
            sb.append(";gather=true");
        }
        if (forceNew) {
            sb.append(";new=true");
        }
        return sb.toString();
    }

    /**
     * Parses a serialized string into a BuildingConfig instance.
     */
    public static BuildingConfig deserialize(String payload) {
        BuildingConfig cfg = new BuildingConfig();
        if (payload == null || payload.isEmpty()) return cfg;

        if (payload.startsWith("cfg:")) {
            String data = payload.substring(4);
            String[] tokens = data.split(";");
            for (String token : tokens) {
                int eq = token.indexOf('=');
                if (eq < 0) continue;
                String k = token.substring(0, eq).trim();
                String v = token.substring(eq + 1).trim();

                switch (k) {
                    case "id" -> cfg.setBlueprintId(v);
                    case "mode" -> {
                        try { cfg.setBuildMode(BuildMode.valueOf(v)); } catch (Exception ignored) {}
                    }
                    case "matMode" -> {
                        try { cfg.setMaterialMode(MaterialMode.valueOf(v)); } catch (Exception ignored) {}
                    }
                    case "mat" -> {
                        ResourceLocation rl = ResourceLocation.tryParse(v);
                        if (rl != null) cfg.setPrimaryMaterial(rl);
                    }
                    case "rot" -> {
                        try { cfg.setRotation(Integer.parseInt(v)); } catch (Exception ignored) {}
                    }
                    case "doors" -> cfg.setKeepDoors(Boolean.parseBoolean(v));
                    case "win" -> cfg.setKeepWindows(Boolean.parseBoolean(v));
                    case "stairs" -> cfg.setKeepStairs(Boolean.parseBoolean(v));
                    case "roof" -> cfg.setKeepRoof(Boolean.parseBoolean(v));
                    case "floors" -> cfg.setKeepFloors(Boolean.parseBoolean(v));
                    case "inwalls" -> cfg.setKeepInteriorWalls(Boolean.parseBoolean(v));
                    case "furn" -> cfg.setKeepFurniture(Boolean.parseBoolean(v));
                    case "decor" -> cfg.setKeepDecor(Boolean.parseBoolean(v));
                    case "light" -> cfg.setKeepLighting(Boolean.parseBoolean(v));
                    case "chests" -> cfg.setKeepChests(Boolean.parseBoolean(v));
                    case "found" -> cfg.setKeepFoundations(Boolean.parseBoolean(v));
                    case "place" -> {
                        try { cfg.setPlacementMode(PlacementMode.valueOf(v)); } catch (Exception ignored) {}
                    }
                    case "pos" -> {
                        String[] coords = v.split(",");
                        if (coords.length >= 3) {
                            try {
                                cfg.setTargetPos(new BlockPos(Integer.parseInt(coords[0]), Integer.parseInt(coords[1]), Integer.parseInt(coords[2])));
                            } catch (Exception ignored) {}
                        }
                    }
                    case "gather" -> cfg.setAutoGather(Boolean.parseBoolean(v));
                    case "new" -> cfg.setForceNew(Boolean.parseBoolean(v));
                }
            }
            return cfg;
        }

        // Backward compatibility: old format payload "small_house:oak:cobblestone:rot=90:auto_gather"
        boolean autoGather = payload.contains(":auto_gather") || payload.contains(":gather");
        cfg.setAutoGather(autoGather);
        String clean = payload.replace(":auto_gather", "").replace(":gather", "");

        if (clean.contains(":rot=")) {
            int rotIdx = clean.indexOf(":rot=");
            String rotStr = clean.substring(rotIdx + 5);
            int end = rotStr.indexOf(':');
            if (end != -1) rotStr = rotStr.substring(0, end);
            try { cfg.setRotation(Integer.parseInt(rotStr)); } catch (Exception ignored) {}
            clean = clean.replaceAll(":rot=\\d+", "");
        }

        String[] parts = clean.split(":");
        if (parts.length > 0 && !parts[0].isEmpty()) {
            cfg.setBlueprintId(parts[0]);
        }
        return cfg;
    }
}
