package com.aicompanion.ai.building;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedural generation engine for high-quality player structures.
 * Generates realistic blueprints divided into explicit phases:
 * FOUNDATION, WALLS, ROOF, FURNISHING.
 */
public class ProceduralTemplates {

    public enum WoodType {
        OAK(Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_FENCE.defaultBlockState(), "Roble"),
        BIRCH(Blocks.BIRCH_PLANKS.defaultBlockState(), Blocks.BIRCH_LOG.defaultBlockState(), Blocks.BIRCH_FENCE.defaultBlockState(), "Abedul"),
        SPRUCE(Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_LOG.defaultBlockState(), Blocks.SPRUCE_FENCE.defaultBlockState(), "Abeto"),
        DARK_OAK(Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.DARK_OAK_LOG.defaultBlockState(), Blocks.DARK_OAK_FENCE.defaultBlockState(), "Roble Oscuro"),
        ACACIA(Blocks.ACACIA_PLANKS.defaultBlockState(), Blocks.ACACIA_LOG.defaultBlockState(), Blocks.ACACIA_FENCE.defaultBlockState(), "Acacia"),
        CHERRY(Blocks.CHERRY_PLANKS.defaultBlockState(), Blocks.CHERRY_LOG.defaultBlockState(), Blocks.CHERRY_FENCE.defaultBlockState(), "Cerezo"),
        JUNGLE(Blocks.JUNGLE_PLANKS.defaultBlockState(), Blocks.JUNGLE_LOG.defaultBlockState(), Blocks.JUNGLE_FENCE.defaultBlockState(), "Jungla"),
        MANGROVE(Blocks.MANGROVE_PLANKS.defaultBlockState(), Blocks.MANGROVE_LOG.defaultBlockState(), Blocks.MANGROVE_FENCE.defaultBlockState(), "Manglar"),
        BAMBOO(Blocks.BAMBOO_PLANKS.defaultBlockState(), Blocks.BAMBOO_BLOCK.defaultBlockState(), Blocks.BAMBOO_FENCE.defaultBlockState(), "Bambú");

        public final BlockState planks;
        public final BlockState log;
        public final BlockState fence;
        public final String displayName;

        WoodType(BlockState planks, BlockState log, BlockState fence, String displayName) {
            this.planks = planks;
            this.log = log;
            this.fence = fence;
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static WoodType parseWood(String str) {
            if (str == null) return OAK;
            String s = str.toLowerCase();
            if (s.contains("birch") || s.contains("abedul")) return BIRCH;
            if (s.contains("spruce") || s.contains("abeto")) return SPRUCE;
            if (s.contains("dark") || s.contains("oscuro")) return DARK_OAK;
            if (s.contains("acacia")) return ACACIA;
            if (s.contains("cherry") || s.contains("cerezo")) return CHERRY;
            if (s.contains("jungle") || s.contains("jungla")) return JUNGLE;
            if (s.contains("mangrove") || s.contains("manglar") || s.contains("mangle")) return MANGROVE;
            if (s.contains("bamboo") || s.contains("bambu") || s.contains("bambú")) return BAMBOO;
            return OAK;
        }
    }

    public enum StoneType {
        COBBLESTONE(Blocks.COBBLESTONE.defaultBlockState(), "Adoquín"),
        STONE_BRICKS(Blocks.STONE_BRICKS.defaultBlockState(), "Ladrillos de Piedra"),
        DEEPSLATE(Blocks.COBBLED_DEEPSLATE.defaultBlockState(), "Pizarra (Deepslate)"),
        SANDSTONE(Blocks.SANDSTONE.defaultBlockState(), "Arenisca");

        public final BlockState block;
        public final String displayName;

        StoneType(BlockState block, String displayName) {
            this.block = block;
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static StoneType parseStone(String str) {
            if (str == null) return COBBLESTONE;
            String s = str.toLowerCase();
            if (s.contains("brick") || s.contains("ladrillo")) return STONE_BRICKS;
            if (s.contains("deepslate") || s.contains("pizarra")) return DEEPSLATE;
            if (s.contains("sandstone") || s.contains("arenisca") || s.contains("arena")) return SANDSTONE;
            return COBBLESTONE;
        }
    }

    /**
     * Builds a realistic survival house (5x5 or 7x7) with solid foundation,
     * wooden plank walls, glass windows, door opening, pitched roof, and interior furnishing.
     */
    public static Blueprint createHouse(String name, int size, WoodType wood, StoneType stone) {
        List<Blueprint.BlockEntry> blocks = new ArrayList<>();
        int half = size / 2;
        int wallHeight = 3;

        // 1. Foundation (Floor at Y = 0)
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                blocks.add(new Blueprint.BlockEntry(new Vec3i(x, 0, z), stone.block, Blueprint.BuildPhase.FOUNDATION));
            }
        }

        // 2. Walls (Y = 1 to wallHeight)
        for (int y = 1; y <= wallHeight; y++) {
            for (int x = -half; x <= half; x++) {
                for (int z = -half; z <= half; z++) {
                    boolean isCorner = (Math.abs(x) == half && Math.abs(z) == half);
                    boolean isWall = (Math.abs(x) == half || Math.abs(z) == half);

                    if (!isWall) continue;

                    // Front door opening at z = -half, x = 0
                    if (z == -half && x == 0 && (y == 1 || y == 2)) {
                        continue; // Door passage
                    }

                    // Windows at middle heights
                    boolean isWindow = (y == 2 && ((Math.abs(x) == half && z == 0) || (Math.abs(z) == half && Math.abs(x) == 1)));
                    if (isWindow) {
                        blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), wood.fence, Blueprint.BuildPhase.WALLS));
                    } else if (isCorner) {
                        blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), wood.log, Blueprint.BuildPhase.WALLS));
                    } else {
                        blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), wood.planks, Blueprint.BuildPhase.WALLS));
                    }
                }
            }
        }

        // 3. Roof (Pyramidal / Gable ceiling)
        int roofStartY = wallHeight + 1;
        for (int layer = 0; layer <= half; layer++) {
            int y = roofStartY + layer;
            int rHalf = half - layer;
            for (int x = -rHalf; x <= rHalf; x++) {
                for (int z = -rHalf; z <= rHalf; z++) {
                    blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), wood.planks, Blueprint.BuildPhase.ROOF));
                }
            }
        }

        // 4. Interior Furnishing
        // Bed at back-left corner
        blocks.add(new Blueprint.BlockEntry(new Vec3i(-half + 1, 1, half - 1), Blocks.RED_BED.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        // Crafting table and Chest at back-right
        blocks.add(new Blueprint.BlockEntry(new Vec3i(half - 1, 1, half - 1), Blocks.CRAFTING_TABLE.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        blocks.add(new Blueprint.BlockEntry(new Vec3i(half - 1, 1, half - 2), Blocks.CHEST.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        // Lighting: Torches above door and inside
        blocks.add(new Blueprint.BlockEntry(new Vec3i(0, 2, half - 1), Blocks.TORCH.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        blocks.add(new Blueprint.BlockEntry(new Vec3i(0, 3, -half - 1), Blocks.TORCH.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));

        return new Blueprint(name, blocks);
    }

    /**
     * Builds a fast 4x4 survival shelter.
     */
    public static Blueprint createShelter(String name) {
        List<Blueprint.BlockEntry> blocks = new ArrayList<>();
        // Foundation
        for (int x = -1; x <= 2; x++) {
            for (int z = -1; z <= 2; z++) {
                blocks.add(new Blueprint.BlockEntry(new Vec3i(x, 0, z), Blocks.COBBLESTONE.defaultBlockState(), Blueprint.BuildPhase.FOUNDATION));
            }
        }
        // Walls
        for (int y = 1; y <= 2; y++) {
            for (int x = -1; x <= 2; x++) {
                for (int z = -1; z <= 2; z++) {
                    if (x == 0 && z == -1) continue; // Door
                    if (x == -1 || x == 2 || z == -1 || z == 2) {
                        blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), Blocks.OAK_PLANKS.defaultBlockState(), Blueprint.BuildPhase.WALLS));
                    }
                }
            }
        }
        // Roof
        for (int x = -1; x <= 2; x++) {
            for (int z = -1; z <= 2; z++) {
                blocks.add(new Blueprint.BlockEntry(new Vec3i(x, 3, z), Blocks.OAK_PLANKS.defaultBlockState(), Blueprint.BuildPhase.ROOF));
            }
        }
        // Furnishings
        blocks.add(new Blueprint.BlockEntry(new Vec3i(1, 1, 1), Blocks.TORCH.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        blocks.add(new Blueprint.BlockEntry(new Vec3i(-1, 1, 1), Blocks.CRAFTING_TABLE.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));

        return new Blueprint(name, blocks);
    }

    /**
     * Builds a tall watchtower with solid stone base, ladder shaft,
     * and an elevated observation battlement with torches.
     */
    public static Blueprint createWatchtower(String name, int height) {
        List<Blueprint.BlockEntry> blocks = new ArrayList<>();

        // Base & Pillars
        for (int y = 0; y <= height; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    boolean isCorner = (Math.abs(x) == 1 && Math.abs(z) == 1);
                    boolean isCenter = (x == 0 && z == 0);

                    if (y == 0) {
                        blocks.add(new Blueprint.BlockEntry(new Vec3i(x, 0, z), Blocks.COBBLESTONE.defaultBlockState(), Blueprint.BuildPhase.FOUNDATION));
                    } else if (isCorner) {
                        blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), Blocks.STONE_BRICKS.defaultBlockState(), Blueprint.BuildPhase.WALLS));
                    } else if (isCenter && y < height) {
                        blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), Blocks.LADDER.defaultBlockState(), Blueprint.BuildPhase.WALLS));
                    }
                }
            }
        }

        // Platform at top
        int topY = height;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x == 0 && z == 0) continue; // Trapdoor / ladder access
                blocks.add(new Blueprint.BlockEntry(new Vec3i(x, topY, z), Blocks.OAK_PLANKS.defaultBlockState(), Blueprint.BuildPhase.ROOF));
            }
        }

        // Battlements (Fences & Torches)
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) == 2 || Math.abs(z) == 2) {
                    blocks.add(new Blueprint.BlockEntry(new Vec3i(x, topY + 1, z), Blocks.OAK_FENCE.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
                }
            }
        }
        // 4 Torches at corners
        blocks.add(new Blueprint.BlockEntry(new Vec3i(-2, topY + 2, -2), Blocks.TORCH.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        blocks.add(new Blueprint.BlockEntry(new Vec3i(2, topY + 2, -2), Blocks.TORCH.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        blocks.add(new Blueprint.BlockEntry(new Vec3i(-2, topY + 2, 2), Blocks.TORCH.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        blocks.add(new Blueprint.BlockEntry(new Vec3i(2, topY + 2, 2), Blocks.TORCH.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));

        return new Blueprint(name, blocks);
    }

    /**
     * Builds a bridge spanning forward with railings and support arches.
     */
    public static Blueprint createBridge(String name, int length) {
        List<Blueprint.BlockEntry> blocks = new ArrayList<>();
        for (int z = 0; z < length; z++) {
            // Walkway (Width 3: x = -1, 0, 1)
            for (int x = -1; x <= 1; x++) {
                blocks.add(new Blueprint.BlockEntry(new Vec3i(x, 0, z), Blocks.COBBLESTONE.defaultBlockState(), Blueprint.BuildPhase.FOUNDATION));
            }
            // Railings
            blocks.add(new Blueprint.BlockEntry(new Vec3i(-1, 1, z), Blocks.OAK_FENCE.defaultBlockState(), Blueprint.BuildPhase.WALLS));
            blocks.add(new Blueprint.BlockEntry(new Vec3i(1, 1, z), Blocks.OAK_FENCE.defaultBlockState(), Blueprint.BuildPhase.WALLS));

            // Torches every 4 blocks along railings
            if (z % 4 == 0) {
                blocks.add(new Blueprint.BlockEntry(new Vec3i(-1, 2, z), Blocks.TORCH.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
                blocks.add(new Blueprint.BlockEntry(new Vec3i(1, 2, z), Blocks.TORCH.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
            }
        }
        return new Blueprint(name, blocks);
    }

    /**
     * Builds a fenced crop farm with central irrigation, tilled soil, and perimeter lighting.
     */
    public static Blueprint createFarm(String name) {
        List<Blueprint.BlockEntry> blocks = new ArrayList<>();
        int half = 3; // 7x7 area

        // Farmland & Water
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                if (x == 0 && z == 0) {
                    blocks.add(new Blueprint.BlockEntry(new Vec3i(x, 0, z), Blocks.WATER.defaultBlockState(), Blueprint.BuildPhase.FOUNDATION));
                } else {
                    blocks.add(new Blueprint.BlockEntry(new Vec3i(x, 0, z), Blocks.FARMLAND.defaultBlockState(), Blueprint.BuildPhase.FOUNDATION));
                }

                // Perimeter fence
                if (Math.abs(x) == half || Math.abs(z) == half) {
                    if (x == 0 && z == -half) {
                        blocks.add(new Blueprint.BlockEntry(new Vec3i(x, 1, z), Blocks.OAK_FENCE_GATE.defaultBlockState(), Blueprint.BuildPhase.WALLS));
                    } else {
                        blocks.add(new Blueprint.BlockEntry(new Vec3i(x, 1, z), Blocks.OAK_FENCE.defaultBlockState(), Blueprint.BuildPhase.WALLS));
                    }
                }
            }
        }

        // Corner torches
        blocks.add(new Blueprint.BlockEntry(new Vec3i(-half, 2, -half), Blocks.TORCH.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        blocks.add(new Blueprint.BlockEntry(new Vec3i(half, 2, -half), Blocks.TORCH.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        blocks.add(new Blueprint.BlockEntry(new Vec3i(-half, 2, half), Blocks.TORCH.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        blocks.add(new Blueprint.BlockEntry(new Vec3i(half, 2, half), Blocks.TORCH.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));

        return new Blueprint(name, blocks);
    }

    // =====================================================================================
    // Parametric house (interactive build dialogue)
    // =====================================================================================

    public static Block doorFor(WoodType wood) {
        return switch (wood) {
            case BIRCH -> Blocks.BIRCH_DOOR;
            case SPRUCE -> Blocks.SPRUCE_DOOR;
            case DARK_OAK -> Blocks.DARK_OAK_DOOR;
            case ACACIA -> Blocks.ACACIA_DOOR;
            case CHERRY -> Blocks.CHERRY_DOOR;
            case JUNGLE -> Blocks.JUNGLE_DOOR;
            case MANGROVE -> Blocks.MANGROVE_DOOR;
            case BAMBOO -> Blocks.BAMBOO_DOOR;
            default -> Blocks.OAK_DOOR;
        };
    }

    public static WoodType woodOf(HouseSpec spec) {
        try {
            return WoodType.valueOf(spec.woodOrDefault());
        } catch (Exception e) {
            return WoodType.OAK;
        }
    }

    /**
     * Generates a house from a {@link HouseSpec}. Local layout: the front (door) looks to -Z
     * (north); the whole blueprint is then rotated to the requested facing.
     * Each storey is 4 blocks high (3 wall rows + 1 slab/roof row). Upper storeys are reached
     * by a ladder column against the back wall. Windows, door, ladders, torches and furniture
     * are in the FURNISHING phase: if one of them can't be obtained the house is still closed
     * and safe, and the companion reports what is missing.
     */
    public static Blueprint createHouse(HouseSpec spec) {
        List<Blueprint.BlockEntry> blocks = new ArrayList<>();
        WoodType wood = woodOf(spec);
        int hw = spec.widthOrDefault() / 2;
        int hd = spec.depthOrDefault() / 2;
        int floors = spec.floorsOrDefault();

        BlockState wallBlock = spec.walls() == HouseSpec.Material.WOOD ? wood.planks : Blocks.COBBLESTONE.defaultBlockState();
        BlockState cornerBlock = spec.walls() == HouseSpec.Material.WOOD ? wood.log : Blocks.COBBLESTONE.defaultBlockState();
        BlockState floorBlock = spec.floor() == HouseSpec.Material.WOOD ? wood.planks : Blocks.COBBLESTONE.defaultBlockState();
        BlockState roofBlock = spec.roof() == HouseSpec.Material.WOOD ? wood.planks : Blocks.COBBLESTONE.defaultBlockState();

        int windowX = hw >= 3 ? 2 : 1;   // windows on front/back walls (never on the door column)
        int ladderX = 0;
        int ladderZ = hd - 1;            // interior cell against the back wall

        // 1. Ground floor (foundation)
        for (int x = -hw; x <= hw; x++) {
            for (int z = -hd; z <= hd; z++) {
                blocks.add(new Blueprint.BlockEntry(new Vec3i(x, 0, z), floorBlock, Blueprint.BuildPhase.FOUNDATION));
            }
        }

        for (int f = 0; f < floors; f++) {
            int base = f * 4;
            // 2. Walls of this storey
            for (int y = base + 1; y <= base + 3; y++) {
                for (int x = -hw; x <= hw; x++) {
                    for (int z = -hd; z <= hd; z++) {
                        boolean isWall = Math.abs(x) == hw || Math.abs(z) == hd;
                        if (!isWall) continue;
                        boolean isCorner = Math.abs(x) == hw && Math.abs(z) == hd;
                        // door opening (ground floor, front wall)
                        if (f == 0 && z == -hd && x == 0 && (y == base + 1 || y == base + 2)) continue;
                        if (spec.windowsOrDefault() && y == base + 2 && isWindowCell(x, z, hw, hd, windowX)) continue;
                        blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), isCorner ? cornerBlock : wallBlock, Blueprint.BuildPhase.WALLS));
                    }
                }
            }
            // 3. Slab between storeys (ring = wall material, interior = floor material, ladder hole)
            if (f < floors - 1) {
                int y = base + 4;
                for (int x = -hw; x <= hw; x++) {
                    for (int z = -hd; z <= hd; z++) {
                        boolean ring = Math.abs(x) == hw || Math.abs(z) == hd;
                        if (!ring && x == ladderX && z == ladderZ) continue; // ladder hole
                        blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), ring ? wallBlock : floorBlock, Blueprint.BuildPhase.WALLS));
                    }
                }
            }
        }

        // 4. Roof (stepped pyramid starting on top of the last storey)
        int roofY = floors * 4;
        for (int layer = 0; ; layer++) {
            int rx = hw - layer;
            int rz = hd - layer;
            if (rx < 0 || rz < 0) break;
            for (int x = -rx; x <= rx; x++) {
                for (int z = -rz; z <= rz; z++) {
                    blocks.add(new Blueprint.BlockEntry(new Vec3i(x, roofY + layer, z), roofBlock, Blueprint.BuildPhase.ROOF));
                }
            }
        }

        // 5. Furnishing / openings (optional elements)
        if (spec.doorOrDefault()) {
            BlockState door = doorFor(wood).defaultBlockState()
                    .setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH)
                    .setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
            blocks.add(new Blueprint.BlockEntry(new Vec3i(0, 1, -hd), door, Blueprint.BuildPhase.FURNISHING));
        }
        if (spec.windowsOrDefault()) {
            for (int f = 0; f < floors; f++) {
                int y = f * 4 + 2;
                for (int x = -hw; x <= hw; x++) {
                    for (int z = -hd; z <= hd; z++) {
                        if ((Math.abs(x) == hw || Math.abs(z) == hd) && isWindowCell(x, z, hw, hd, windowX)) {
                            blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), Blocks.GLASS.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
                        }
                    }
                }
            }
        }
        // Ladder column: from the ground floor up through every slab hole
        if (floors > 1) {
            BlockState ladder = Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.NORTH);
            for (int y = 1; y <= (floors - 1) * 4; y++) {
                blocks.add(new Blueprint.BlockEntry(new Vec3i(ladderX, y, ladderZ), ladder, Blueprint.BuildPhase.FURNISHING));
            }
        }
        // Light: wall torches on the east wall of every storey + one above the door outside
        BlockState wallTorchWest = Blocks.WALL_TORCH.defaultBlockState()
                .setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, net.minecraft.core.Direction.WEST);
        for (int f = 0; f < floors; f++) {
            blocks.add(new Blueprint.BlockEntry(new Vec3i(hw - 1, f * 4 + 2, -hd + 1), wallTorchWest, Blueprint.BuildPhase.FURNISHING));
        }
        blocks.add(new Blueprint.BlockEntry(new Vec3i(0, 3, -hd - 1), Blocks.WALL_TORCH.defaultBlockState()
                .setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, net.minecraft.core.Direction.NORTH), Blueprint.BuildPhase.FURNISHING));
        // Furniture on the ground floor (bed foot; BuildTask places the head half)
        blocks.add(new Blueprint.BlockEntry(new Vec3i(-hw + 1, 1, hd - 1), Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.NORTH), Blueprint.BuildPhase.FURNISHING));
        blocks.add(new Blueprint.BlockEntry(new Vec3i(hw - 1, 1, hd - 1), Blocks.CRAFTING_TABLE.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        if (hd - 2 > -hd + 1) {
            blocks.add(new Blueprint.BlockEntry(new Vec3i(hw - 1, 1, hd - 2), Blocks.CHEST.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        }

        // 6. Rotate to the requested facing
        net.minecraft.world.level.block.Rotation rot = switch (spec.facingOrDefault()) {
            case EAST -> net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
            case SOUTH -> net.minecraft.world.level.block.Rotation.CLOCKWISE_180;
            case WEST -> net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
            default -> net.minecraft.world.level.block.Rotation.NONE;
        };
        if (rot != net.minecraft.world.level.block.Rotation.NONE) {
            List<Blueprint.BlockEntry> rotated = new ArrayList<>(blocks.size());
            for (Blueprint.BlockEntry e : blocks) {
                rotated.add(new Blueprint.BlockEntry(rotateOffset(e.offset(), rot), e.state().rotate(rot), e.phase()));
            }
            blocks = rotated;
        }
        return new Blueprint(spec.encode(), blocks);
    }

    /**
     * Builds a spacious 2-story house (7x7 default) with interior ladder,
     * separate floors, windows on both stories, full furnishing, and pitched roof.
     */
    public static Blueprint createTwoStoryHouse(String name, int size, WoodType wood, StoneType stone) {
        List<Blueprint.BlockEntry> blocks = new ArrayList<>();
        int half = Math.max(3, size / 2); // default half 3 -> 7x7

        // 1. Foundation (Y = 0)
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                blocks.add(new Blueprint.BlockEntry(new Vec3i(x, 0, z), stone.block, Blueprint.BuildPhase.FOUNDATION));
            }
        }

        // 2. Floor 1 Walls (Y = 1 to 3)
        for (int y = 1; y <= 3; y++) {
            for (int x = -half; x <= half; x++) {
                for (int z = -half; z <= half; z++) {
                    boolean isCorner = (Math.abs(x) == half && Math.abs(z) == half);
                    boolean isWall = (Math.abs(x) == half || Math.abs(z) == half);
                    if (!isWall) continue;

                    // Front door passage
                    if (z == -half && x == 0 && (y == 1 || y == 2)) continue;

                    // Windows at Y = 2
                    boolean isWindow = (y == 2 && ((Math.abs(x) == half && z == 0) || (Math.abs(z) == half && Math.abs(x) == 1)));
                    if (isWindow) {
                        blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), wood.fence, Blueprint.BuildPhase.WALLS));
                    } else if (isCorner) {
                        blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), wood.log, Blueprint.BuildPhase.WALLS));
                    } else {
                        blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), wood.planks, Blueprint.BuildPhase.WALLS));
                    }
                }
            }
        }

        // 3. Second Floor Ceiling/Floor (Y = 4)
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                // Leave hatch/opening for ladder at corner
                if (x == half - 1 && z == half - 1) continue;
                blocks.add(new Blueprint.BlockEntry(new Vec3i(x, 4, z), wood.planks, Blueprint.BuildPhase.WALLS));
            }
        }

        // Ladder to second floor
        for (int y = 1; y <= 4; y++) {
            blocks.add(new Blueprint.BlockEntry(new Vec3i(half - 1, y, half), wood.log, Blueprint.BuildPhase.FURNISHING));
            blocks.add(new Blueprint.BlockEntry(new Vec3i(half - 1, y, half - 1), Blocks.LADDER.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        }

        // 4. Floor 2 Walls (Y = 5 to 7)
        for (int y = 5; y <= 7; y++) {
            for (int x = -half; x <= half; x++) {
                for (int z = -half; z <= half; z++) {
                    boolean isCorner = (Math.abs(x) == half && Math.abs(z) == half);
                    boolean isWall = (Math.abs(x) == half || Math.abs(z) == half);
                    if (!isWall) continue;

                    // Windows at Y = 6
                    boolean isWindow = (y == 6 && ((Math.abs(x) == half && z == 0) || (Math.abs(z) == half && Math.abs(x) == 1)));
                    if (isWindow) {
                        blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), wood.fence, Blueprint.BuildPhase.WALLS));
                    } else if (isCorner) {
                        blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), wood.log, Blueprint.BuildPhase.WALLS));
                    } else {
                        blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), wood.planks, Blueprint.BuildPhase.WALLS));
                    }
                }
            }
        }

        // 5. Pitched Roof (Y = 8 upwards)
        int roofStartY = 8;
        for (int layer = 0; layer <= half; layer++) {
            int y = roofStartY + layer;
            int rHalf = half - layer;
            for (int x = -rHalf; x <= rHalf; x++) {
                for (int z = -rHalf; z <= rHalf; z++) {
                    blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), wood.planks, Blueprint.BuildPhase.ROOF));
                }
            }
        }

        // 6. Furnishings
        // Floor 1: crafting table, furnace, double chest, torch
        blocks.add(new Blueprint.BlockEntry(new Vec3i(-half + 1, 1, half - 1), Blocks.CRAFTING_TABLE.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        blocks.add(new Blueprint.BlockEntry(new Vec3i(-half + 2, 1, half - 1), Blocks.FURNACE.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        blocks.add(new Blueprint.BlockEntry(new Vec3i(-half + 1, 1, -half + 1), Blocks.CHEST.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        blocks.add(new Blueprint.BlockEntry(new Vec3i(0, 3, 0), Blocks.TORCH.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));

        // Floor 2: bed, chest, torch
        blocks.add(new Blueprint.BlockEntry(new Vec3i(-half + 1, 5, half - 1), Blocks.WHITE_BED.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        blocks.add(new Blueprint.BlockEntry(new Vec3i(-half + 1, 5, -half + 1), Blocks.CHEST.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        blocks.add(new Blueprint.BlockEntry(new Vec3i(0, 7, 0), Blocks.TORCH.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));

        return new Blueprint(name, blocks);
    }

    /**
     * Builds a large warehouse / storage hall (9x7) with organized chest banks.
     */
    public static Blueprint createWarehouse(String name, WoodType wood, StoneType stone) {
        List<Blueprint.BlockEntry> blocks = new ArrayList<>();
        int hw = 4; // width 9 (X: -4..4)
        int hd = 3; // depth 7 (Z: -3..3)
        int wallHeight = 4;

        // 1. Foundation (Y = 0)
        for (int x = -hw; x <= hw; x++) {
            for (int z = -hd; z <= hd; z++) {
                blocks.add(new Blueprint.BlockEntry(new Vec3i(x, 0, z), stone.block, Blueprint.BuildPhase.FOUNDATION));
            }
        }

        // 2. Walls (Y = 1 to 4)
        for (int y = 1; y <= wallHeight; y++) {
            for (int x = -hw; x <= hw; x++) {
                for (int z = -hd; z <= hd; z++) {
                    boolean isCorner = (Math.abs(x) == hw && Math.abs(z) == hd);
                    boolean isWall = (Math.abs(x) == hw || Math.abs(z) == hd);
                    if (!isWall) continue;

                    // Double wide entrance at front (Z = -hd, X = -1..1, Y = 1..3)
                    if (z == -hd && Math.abs(x) <= 1 && y <= 3) continue;

                    // Stone base, wooden upper walls
                    if (y == 1) {
                        blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), stone.block, Blueprint.BuildPhase.WALLS));
                    } else if (isCorner || (Math.abs(x) == hw && z == 0)) {
                        blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), wood.log, Blueprint.BuildPhase.WALLS));
                    } else {
                        blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), wood.planks, Blueprint.BuildPhase.WALLS));
                    }
                }
            }
        }

        // 3. A-Frame Roof (Y = 5 upwards)
        int roofStartY = 5;
        for (int layer = 0; layer <= hd; layer++) {
            int y = roofStartY + layer;
            int zBound = hd - layer;
            for (int x = -hw - 1; x <= hw + 1; x++) {
                blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, zBound), wood.planks, Blueprint.BuildPhase.ROOF));
                if (zBound > 0) {
                    blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, -zBound), wood.planks, Blueprint.BuildPhase.ROOF));
                }
            }
        }

        // 4. Warehouse storage chest rows inside
        for (int z = -hd + 1; z <= hd - 1; z++) {
            if (z == 0) continue; // walkway
            blocks.add(new Blueprint.BlockEntry(new Vec3i(-hw + 1, 1, z), Blocks.CHEST.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
            blocks.add(new Blueprint.BlockEntry(new Vec3i(hw - 1, 1, z), Blocks.CHEST.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        }

        // Illumination
        blocks.add(new Blueprint.BlockEntry(new Vec3i(-2, 3, 0), Blocks.TORCH.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        blocks.add(new Blueprint.BlockEntry(new Vec3i(2, 3, 0), Blocks.TORCH.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));

        return new Blueprint(name, blocks);
    }

    /**
     * Builds a sturdy defensive wall section with rampart walkway, battlements, and torches.
     */
    public static Blueprint createDefensiveWall(String name, int length, StoneType stone) {
        List<Blueprint.BlockEntry> blocks = new ArrayList<>();
        int halfLen = Math.max(3, length / 2); // default 8-12 blocks

        // Foundation & Wall Body (Y = 0 to 4, Z = -1 to 1)
        for (int y = 0; y <= 4; y++) {
            for (int x = -halfLen; x <= halfLen; x++) {
                for (int z = -1; z <= 1; z++) {
                    Blueprint.BuildPhase phase = y == 0 ? Blueprint.BuildPhase.FOUNDATION : Blueprint.BuildPhase.WALLS;
                    blocks.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), stone.block, phase));
                }
            }
        }

        // Battlements / Crenellations at Y = 5 on outer (z = -1) and inner (z = 1) edges
        for (int x = -halfLen; x <= halfLen; x++) {
            if (Math.abs(x) % 2 == 0) {
                blocks.add(new Blueprint.BlockEntry(new Vec3i(x, 5, -1), stone.block, Blueprint.BuildPhase.WALLS));
                blocks.add(new Blueprint.BlockEntry(new Vec3i(x, 5, 1), stone.block, Blueprint.BuildPhase.WALLS));
            }
        }

        // Torches along the walkway (Z = 0)
        for (int x = -halfLen + 2; x <= halfLen - 2; x += 4) {
            blocks.add(new Blueprint.BlockEntry(new Vec3i(x, 5, 0), Blocks.TORCH.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        }

        // Access ladder at one end
        for (int y = 1; y <= 4; y++) {
            blocks.add(new Blueprint.BlockEntry(new Vec3i(-halfLen, y, 2), Blocks.LADDER.defaultBlockState(), Blueprint.BuildPhase.FURNISHING));
        }

        return new Blueprint(name, blocks);
    }

    private static boolean isWindowCell(int x, int z, int hw, int hd, int windowX) {
        boolean corner = Math.abs(x) == hw && Math.abs(z) == hd;
        if (corner) return false;
        if (Math.abs(x) == hw) return z == 0 || (hd >= 4 && Math.abs(z) == hd - 2);   // side walls
        if (Math.abs(z) == hd) return Math.abs(x) == windowX;                           // front/back
        return false;
    }

    static Vec3i rotateOffset(Vec3i v, net.minecraft.world.level.block.Rotation rot) {
        return switch (rot) {
            case CLOCKWISE_90 -> new Vec3i(-v.getZ(), v.getY(), v.getX());
            case CLOCKWISE_180 -> new Vec3i(-v.getX(), v.getY(), -v.getZ());
            case COUNTERCLOCKWISE_90 -> new Vec3i(v.getZ(), v.getY(), -v.getX());
            default -> v;
        };
    }
}
