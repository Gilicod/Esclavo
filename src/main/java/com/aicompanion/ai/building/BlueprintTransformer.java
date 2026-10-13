package com.aicompanion.ai.building;

import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.*;

/**
 * Pure architectural transformation engine for Esclavo.
 * Classifies blueprint voxels (exterior envelope, interior rooms, furniture, decor, roof, floors),
 * applies building modes and smart material substitution, and outputs the exact transformed Blueprint.
 * Shared deterministically between client 3D preview and server BuildTask.
 */
public class BlueprintTransformer {

    public static Blueprint transform(Blueprint source, BuildingConfig config) {
        if (source == null) return null;
        if (config == null || (config.getBuildMode() == BuildingConfig.BuildMode.ORIGINAL && config.getRotation() == 0)) {
            boolean hasTech = false;
            for (Blueprint.BlockEntry e : source.getBlocks()) {
                if (SchematicImporter.isTechnicalOrUnobtainable(e.state())) {
                    hasTech = true;
                    break;
                }
            }
            if (!hasTech) return source;
        }

        List<Blueprint.BlockEntry> originalBlocks = source.getBlocks();
        if (originalBlocks.isEmpty()) return source;

        // 1. Calculate Bounding Box
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (Blueprint.BlockEntry entry : originalBlocks) {
            Vec3i pos = entry.offset();
            if (pos.getX() < minX) minX = pos.getX();
            if (pos.getX() > maxX) maxX = pos.getX();
            if (pos.getY() < minY) minY = pos.getY();
            if (pos.getY() > maxY) maxY = pos.getY();
            if (pos.getZ() < minZ) minZ = pos.getZ();
            if (pos.getZ() > maxZ) maxZ = pos.getZ();
        }

        int sizeX = (maxX - minX + 1);
        int sizeY = (maxY - minY + 1);
        int sizeZ = (maxZ - minZ + 1);

        // 2. Build 3D Occupancy Grid for 3D Flood-Fill
        boolean[][][] occupied = new boolean[sizeX][sizeY][sizeZ];
        Map<Vec3i, BlockState> blockMap = new HashMap<>();

        for (Blueprint.BlockEntry entry : originalBlocks) {
            Vec3i pos = entry.offset();
            int gx = pos.getX() - minX;
            int gy = pos.getY() - minY;
            int gz = pos.getZ() - minZ;
            if (gx >= 0 && gx < sizeX && gy >= 0 && gy < sizeY && gz >= 0 && gz < sizeZ) {
                occupied[gx][gy][gz] = true;
                blockMap.put(pos, entry.state());
            }
        }

        // 3. 3D Flood-Fill from Padded Bounding Box to identify Exterior Air
        int padX = sizeX + 2;
        int padY = sizeY + 2;
        int padZ = sizeZ + 2;
        boolean[][][] exteriorAir = new boolean[padX][padY][padZ];
        Queue<int[]> queue = new ArrayDeque<>();

        // Start flood from (0, 0, 0) in padded space
        exteriorAir[0][0][0] = true;
        queue.add(new int[]{0, 0, 0});

        int[][] directions = {
                {1, 0, 0}, {-1, 0, 0},
                {0, 1, 0}, {0, -1, 0},
                {0, 0, 1}, {0, 0, -1}
        };

        while (!queue.isEmpty()) {
            int[] curr = queue.poll();
            int cx = curr[0];
            int cy = curr[1];
            int cz = curr[2];

            for (int[] d : directions) {
                int nx = cx + d[0];
                int ny = cy + d[1];
                int nz = cz + d[2];

                if (nx >= 0 && nx < padX && ny >= 0 && ny < padY && nz >= 0 && nz < padZ) {
                    if (!exteriorAir[nx][ny][nz]) {
                        // In structure space:
                        int sx = nx - 1;
                        int sy = ny - 1;
                        int sz = nz - 1;
                        boolean isSolid = (sx >= 0 && sx < sizeX && sy >= 0 && sy < sizeY && sz >= 0 && sz < sizeZ) && occupied[sx][sy][sz];
                        if (!isSolid) {
                            exteriorAir[nx][ny][nz] = true;
                            queue.add(new int[]{nx, ny, nz});
                        }
                    }
                }
            }
        }

        // 4. Resolve Primary Material and Family Replacements
        Block primaryBlock = BuiltInRegistries.BLOCK.get(config.getPrimaryMaterial());
        if (primaryBlock == Blocks.AIR) {
            primaryBlock = Blocks.COBBLESTONE;
        }

        BlockFamilyMapping family = resolveFamily(primaryBlock);

        // 5. Transform and Filter Blocks
        List<Blueprint.BlockEntry> transformedList = new ArrayList<>();
        BuildingConfig.BuildMode mode = config.getBuildMode();

        for (Blueprint.BlockEntry entry : originalBlocks) {
            Vec3i pos = entry.offset();
            BlockState state = entry.state();
            if (state == null || state.isAir() || SchematicImporter.isTechnicalOrUnobtainable(state)) continue;

            int gx = pos.getX() - minX;
            int gy = pos.getY() - minY;
            int gz = pos.getZ() - minZ;

            // Check if this block is adjacent to exterior air
            boolean isExteriorEnvelope = false;
            for (int[] d : directions) {
                int px = (gx + 1) + d[0];
                int py = (gy + 1) + d[1];
                int pz = (gz + 1) + d[2];
                if (px >= 0 && px < padX && py >= 0 && py < padY && pz >= 0 && pz < padZ) {
                    if (exteriorAir[px][py][pz]) {
                        isExteriorEnvelope = true;
                        break;
                    }
                }
            }

            BlockClassification classification = classifyBlock(entry, gy, sizeY, isExteriorEnvelope);

            // Filter block based on selected mode
            if (!shouldKeepBlock(classification, mode, config)) {
                continue;
            }

            // Apply material substitution if required
            BlockState finalState = state;
            if (mode != BuildingConfig.BuildMode.ORIGINAL) {
                finalState = substituteMaterial(state, classification, primaryBlock, family, config.getMaterialMode());
            }

            transformedList.add(new Blueprint.BlockEntry(pos, finalState, entry.phase()));
        }

        Blueprint transformedBp = new Blueprint(source.getName() + "_custom", transformedList);

        // 6. Apply Rotation if specified
        if (config.getRotation() != 0) {
            transformedBp = transformedBp.rotate(config.getRotation());
        }

        return transformedBp;
    }

    public enum BlockCategory {
        FOUNDATION,
        EXTERIOR_WALL,
        INTERIOR_WALL,
        ROOF,
        FLOOR,
        DOOR,
        WINDOW,
        STAIRS,
        FURNITURE,
        DECOR,
        LIGHTING,
        CHEST,
        OTHER
    }

    public record BlockClassification(BlockCategory category, boolean isExterior) {}

    private static BlockClassification classifyBlock(Blueprint.BlockEntry entry, int y, int totalHeight, boolean isExterior) {
        BlockState state = entry.state();
        Block block = state.getBlock();

        // 1. Doors and gates
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock) {
            return new BlockClassification(BlockCategory.DOOR, isExterior);
        }

        // 2. Windows
        if (block == Blocks.GLASS || block == Blocks.GLASS_PANE || block == Blocks.TINTED_GLASS
                || block instanceof StainedGlassBlock || block instanceof StainedGlassPaneBlock
                || block instanceof IronBarsBlock) {
            return new BlockClassification(BlockCategory.WINDOW, isExterior);
        }

        // 3. Lighting
        if (block instanceof TorchBlock || block instanceof LanternBlock || block instanceof CampfireBlock
                || block == Blocks.SEA_LANTERN || block == Blocks.GLOWSTONE || block == Blocks.SHROOMLIGHT
                || block == Blocks.REDSTONE_LAMP || block == Blocks.FROGSPAWN) {
            return new BlockClassification(BlockCategory.LIGHTING, isExterior);
        }

        // 4. Chests and Storage
        if (block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof EnderChestBlock
                || block instanceof ShulkerBoxBlock) {
            return new BlockClassification(BlockCategory.CHEST, isExterior);
        }

        // 5. Furniture & workstations
        if (block instanceof BedBlock || block instanceof CraftingTableBlock || block instanceof FurnaceBlock
                || block instanceof BlastFurnaceBlock || block instanceof SmokerBlock || block instanceof BrewingStandBlock
                || block instanceof AnvilBlock || block instanceof LoomBlock || block instanceof CartographyTableBlock
                || block instanceof SmithingTableBlock || block instanceof GrindstoneBlock || block instanceof StonecutterBlock) {
            return new BlockClassification(BlockCategory.FURNITURE, isExterior);
        }

        // 6. Decor
        if (block instanceof FlowerPotBlock || block instanceof WoolCarpetBlock || block instanceof BannerBlock
                || block instanceof SkullBlock || block instanceof WallSkullBlock || block == Blocks.BOOKSHELF
                || block instanceof BellBlock) {
            return new BlockClassification(BlockCategory.DECOR, isExterior);
        }

        // 7. Roof
        if (entry.phase() == Blueprint.BuildPhase.ROOF || (y >= Math.max(1, totalHeight - 2) && (state.is(BlockTags.STAIRS) || state.is(BlockTags.SLABS)))) {
            return new BlockClassification(BlockCategory.ROOF, true);
        }

        // 8. Foundation
        if (y == 0 || entry.phase() == Blueprint.BuildPhase.FOUNDATION) {
            return new BlockClassification(BlockCategory.FOUNDATION, isExterior);
        }

        // 9. Stairs & Ladders
        if (state.is(BlockTags.STAIRS) || block instanceof LadderBlock || block instanceof ScaffoldingBlock) {
            return new BlockClassification(BlockCategory.STAIRS, isExterior);
        }

        // 10. Horizontal Floors
        if (state.is(BlockTags.SLABS) || block == Blocks.OAK_PLANKS || block == Blocks.COBBLESTONE || block == Blocks.SMOOTH_STONE) {
            if (!isExterior) {
                return new BlockClassification(BlockCategory.FLOOR, false);
            }
        }

        // 11. Walls
        if (isExterior) {
            return new BlockClassification(BlockCategory.EXTERIOR_WALL, true);
        } else {
            return new BlockClassification(BlockCategory.INTERIOR_WALL, false);
        }
    }

    private static boolean shouldKeepBlock(BlockClassification c, BuildingConfig.BuildMode mode, BuildingConfig config) {
        return switch (mode) {
            case ORIGINAL -> true;

            case SINGLE_MATERIAL -> {
                // Keep functional items, structure will be replaced by material
                if (c.category() == BlockCategory.CHEST) yield config.isKeepChests();
                yield true;
            }

            case SINGLE_MATERIAL_HOLLOW -> {
                // Keep exterior shell, roof, foundation, essential floors, doors and windows
                if (c.category() == BlockCategory.FURNITURE || c.category() == BlockCategory.DECOR) yield false;
                if (c.category() == BlockCategory.CHEST) yield false;
                if (c.category() == BlockCategory.INTERIOR_WALL) yield false;
                yield true;
            }

            case EXTERIOR_ONLY -> {
                // Only exterior envelope and roof
                if (!c.isExterior() && c.category() != BlockCategory.FOUNDATION && c.category() != BlockCategory.ROOF) yield false;
                if (c.category() == BlockCategory.FURNITURE || c.category() == BlockCategory.DECOR || c.category() == BlockCategory.CHEST) yield false;
                yield true;
            }

            case BASIC_STRUCTURE -> {
                // No glass, decor, furniture or chests
                if (c.category() == BlockCategory.WINDOW || c.category() == BlockCategory.FURNITURE
                        || c.category() == BlockCategory.DECOR || c.category() == BlockCategory.CHEST) yield false;
                yield true;
            }

            case NO_DECORATION -> {
                // Keeps all architecture and rooms, removes clutter
                if (c.category() == BlockCategory.FURNITURE || c.category() == BlockCategory.DECOR) yield false;
                if (c.category() == BlockCategory.CHEST) yield config.isKeepChests();
                yield true;
            }

            case CUSTOM -> {
                yield switch (c.category()) {
                    case FOUNDATION -> config.isKeepFoundations();
                    case EXTERIOR_WALL -> true;
                    case INTERIOR_WALL -> config.isKeepInteriorWalls();
                    case ROOF -> config.isKeepRoof();
                    case FLOOR -> config.isKeepFloors();
                    case DOOR -> config.isKeepDoors();
                    case WINDOW -> config.isKeepWindows();
                    case STAIRS -> config.isKeepStairs();
                    case FURNITURE -> config.isKeepFurniture();
                    case DECOR -> config.isKeepDecor();
                    case LIGHTING -> config.isKeepLighting();
                    case CHEST -> config.isKeepChests();
                    case OTHER -> true;
                };
            }
        };
    }

    private static BlockState substituteMaterial(BlockState state, BlockClassification c, Block primary, BlockFamilyMapping family, BuildingConfig.MaterialMode matMode) {
        // Do not substitute interactive/functional blocks
        if (c.category() == BlockCategory.DOOR || c.category() == BlockCategory.WINDOW
                || c.category() == BlockCategory.LIGHTING || c.category() == BlockCategory.FURNITURE
                || c.category() == BlockCategory.CHEST || c.category() == BlockCategory.DECOR) {
            return state;
        }

        if (matMode == BuildingConfig.MaterialMode.LITERAL) {
            return primary.defaultBlockState();
        }

        // Family substitution: Preserve stair/slab/wall geometry
        if (state.is(BlockTags.STAIRS) && family.stairs != null) {
            BlockState newState = family.stairs.defaultBlockState();
            return copyMatchingProperties(state, newState);
        }

        if (state.is(BlockTags.SLABS) && family.slab != null) {
            BlockState newState = family.slab.defaultBlockState();
            return copyMatchingProperties(state, newState);
        }

        if (state.is(BlockTags.WALLS) && family.wall != null) {
            BlockState newState = family.wall.defaultBlockState();
            return copyMatchingProperties(state, newState);
        }

        if (state.is(BlockTags.FENCES) && family.fence != null) {
            BlockState newState = family.fence.defaultBlockState();
            return copyMatchingProperties(state, newState);
        }

        if ((state.is(BlockTags.LOGS) || state.getBlock() instanceof RotatedPillarBlock) && family.log != null) {
            BlockState newState = family.log.defaultBlockState();
            return copyMatchingProperties(state, newState);
        }

        return primary.defaultBlockState();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState copyMatchingProperties(BlockState from, BlockState to) {
        BlockState result = to;
        for (Property prop : from.getProperties()) {
            if (result.hasProperty(prop)) {
                try {
                    Comparable val = from.getValue(prop);
                    result = result.setValue(prop, val);
                } catch (Exception ignored) {}
            }
        }
        return result;
    }

    public record BlockFamilyMapping(Block full, Block stairs, Block slab, Block wall, Block fence, Block log) {}

    public static BlockFamilyMapping resolveFamily(Block base) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(base);
        String path = id.getPath();

        // 1. Cobblestone family
        if (path.contains("cobblestone") || path.contains("adoquin")) {
            return new BlockFamilyMapping(Blocks.COBBLESTONE, Blocks.COBBLESTONE_STAIRS, Blocks.COBBLESTONE_SLAB, Blocks.COBBLESTONE_WALL, null, null);
        }

        // 2. Stone Bricks family
        if (path.contains("stone_brick") || path.contains("ladrillo")) {
            return new BlockFamilyMapping(Blocks.STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_WALL, null, null);
        }

        // 3. Deepslate Bricks / Cobbled Deepslate
        if (path.contains("deepslate") || path.contains("pizarra")) {
            return new BlockFamilyMapping(Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICK_SLAB, Blocks.DEEPSLATE_BRICK_WALL, null, null);
        }

        // 4. Sandstone
        if (path.contains("sandstone") || path.contains("arenisca")) {
            return new BlockFamilyMapping(Blocks.SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_SLAB, Blocks.SANDSTONE_WALL, null, null);
        }

        // 5. Wood Families (Oak, Spruce, Birch, Jungle, Acacia, Dark Oak, Mangrove, Cherry)
        if (path.contains("spruce") || path.contains("abeto")) {
            return new BlockFamilyMapping(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, null, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_LOG);
        }
        if (path.contains("birch") || path.contains("abedul")) {
            return new BlockFamilyMapping(Blocks.BIRCH_PLANKS, Blocks.BIRCH_STAIRS, Blocks.BIRCH_SLAB, null, Blocks.BIRCH_FENCE, Blocks.BIRCH_LOG);
        }
        if (path.contains("dark_oak") || path.contains("oscuro")) {
            return new BlockFamilyMapping(Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, null, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_LOG);
        }
        if (path.contains("cherry") || path.contains("cerezo")) {
            return new BlockFamilyMapping(Blocks.CHERRY_PLANKS, Blocks.CHERRY_STAIRS, Blocks.CHERRY_SLAB, null, Blocks.CHERRY_FENCE, Blocks.CHERRY_LOG);
        }
        if (path.contains("oak") || path.contains("roble") || path.contains("wood") || path.contains("plank")) {
            return new BlockFamilyMapping(Blocks.OAK_PLANKS, Blocks.OAK_STAIRS, Blocks.OAK_SLAB, null, Blocks.OAK_FENCE, Blocks.OAK_LOG);
        }

        // Generic fallback: use base for full block, attempt matching suffix in registry
        String baseName = path.replaceAll("(_stairs|_slab|_wall|_fence|_log|_planks)$", "");
        Block stairs = BuiltInRegistries.BLOCK.get(new ResourceLocation(id.getNamespace(), baseName + "_stairs"));
        Block slab = BuiltInRegistries.BLOCK.get(new ResourceLocation(id.getNamespace(), baseName + "_slab"));
        Block wall = BuiltInRegistries.BLOCK.get(new ResourceLocation(id.getNamespace(), baseName + "_wall"));
        Block fence = BuiltInRegistries.BLOCK.get(new ResourceLocation(id.getNamespace(), baseName + "_fence"));

        return new BlockFamilyMapping(
                base,
                stairs != Blocks.AIR ? stairs : null,
                slab != Blocks.AIR ? slab : null,
                wall != Blocks.AIR ? wall : null,
                fence != Blocks.AIR ? fence : null,
                null
        );
    }
}
