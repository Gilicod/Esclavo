package com.aicompanion.ai.building;

import com.aicompanion.AICompanionMod;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.block.state.BlockState;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sponge Schematic (.schem) format importer for Esclavo.
 * Loads external schematics from disk or network, parses block palettes and VarInt-encoded
 * voxel grids, classifies blocks into phased construction steps (Foundation, Walls, Roof, Furnishing),
 * and registers them directly into Esclavo's Blueprint system for autonomous companion construction.
 */
public class SchematicImporter {

    public static Blueprint importFile(File file) throws Exception {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("El archivo de estructura no existe o es inválido.");
        }
        if (file.getName().contains("..")) {
            throw new SecurityException("Nombre de archivo no permitido: " + file.getName());
        }
        if (file.length() > 25 * 1024 * 1024) {
            throw new IllegalArgumentException("El archivo supera el límite de seguridad de 25 MB: " + (file.length() / (1024 * 1024)) + " MB");
        }
        String fileName = file.getName().toLowerCase();
        String blueprintName = file.getName().replaceAll("(?i)\\.(schem|nbt|schematic)$", "").toLowerCase();

        if (fileName.endsWith(".nbt")) {
            try (InputStream is = new FileInputStream(file)) {
                return importNbtStructure(is, blueprintName);
            }
        } else if (fileName.endsWith(".schem")) {
            try (InputStream is = new FileInputStream(file)) {
                return importSchematic(is, blueprintName);
            }
        } else {
            // Auto-detect format by peeking into root NBT tag
            try (InputStream testIs = new FileInputStream(file)) {
                CompoundTag root = NbtIo.readCompressed(testIs);
                if (root.contains("size") && (root.contains("blocks") || root.contains("palette") || root.contains("palettes"))) {
                    try (InputStream nbtIs = new FileInputStream(file)) {
                        return importNbtStructure(nbtIs, blueprintName);
                    }
                } else {
                    try (InputStream schemIs = new FileInputStream(file)) {
                        return importSchematic(schemIs, blueprintName);
                    }
                }
            }
        }
    }

    public static Blueprint importSchematic(File file) throws Exception {
        return importFile(file);
    }

    public static Blueprint importSchematic(InputStream inputStream, String blueprintName) throws Exception {
        CompoundTag root = NbtIo.readCompressed(inputStream);
        CompoundTag schemTag = root.contains("Schematic") ? root.getCompound("Schematic") : root;

        short width = schemTag.getShort("Width");
        short height = schemTag.getShort("Height");
        short length = schemTag.getShort("Length");

        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IllegalArgumentException("Dimensiones del esquemático inválidas: " + width + "x" + height + "x" + length);
        }
        if (width > 256 || height > 256 || length > 256) {
            throw new IllegalArgumentException("Las dimensiones del esquemático superan el máximo permitido (256x256x256): " + width + "x" + height + "x" + length);
        }
        long volume = (long) width * height * length;
        if (volume > 500_000L) {
            throw new IllegalArgumentException("El volumen del esquemático (" + volume + " bloques) supera el límite máximo de 500,000 bloques");
        }

        // 1. Parse Block Palette
        CompoundTag paletteTag = schemTag.contains("Palette") ? schemTag.getCompound("Palette")
                : (schemTag.contains("BlockPalette") ? schemTag.getCompound("BlockPalette") : null);

        if (paletteTag == null || paletteTag.isEmpty()) {
            throw new IllegalArgumentException("El esquemático no contiene una paleta de bloques válida o está vacía.");
        }

        Map<Integer, BlockState> idToState = new HashMap<>();
        for (String stateString : paletteTag.getAllKeys()) {
            int id = paletteTag.getInt(stateString);
            BlockState state = parseBlockState(stateString);
            idToState.put(id, state);
        }

        // 2. Decode VarInt block data byte array
        if (!schemTag.contains("BlockData")) {
            throw new IllegalArgumentException("El esquemático no contiene el campo 'BlockData'.");
        }
        byte[] blockData = schemTag.getByteArray("BlockData");
        if (blockData == null || blockData.length == 0) {
            throw new IllegalArgumentException("El array 'BlockData' está vacío.");
        }
        List<Blueprint.BlockEntry> blockEntries = new ArrayList<>();
        int[] offset = new int[]{0};

        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int paletteId = readVarInt(blockData, offset);
                    BlockState state = idToState.get(paletteId);
                    if (state == null || state.isAir() || isTechnicalOrUnobtainable(state)) continue;

                    Blueprint.BuildPhase phase = determinePhase(y, height, state);
                    blockEntries.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), state, phase));
                }
            }
        }

        // 3. Sort entries so foundation is built first, followed by walls, roof, and furnishings
        blockEntries.sort((a, b) -> {
            int phaseComp = a.phase().compareTo(b.phase());
            if (phaseComp != 0) return phaseComp;
            return Integer.compare(a.offset().getY(), b.offset().getY());
        });

        Blueprint blueprint = new Blueprint(blueprintName, blockEntries);
        AICompanionMod.LOGGER.info("[ESCLAVO-BUILD] Successfully imported schematic '{}' with {} blocks ({}x{}x{})",
                blueprintName, blockEntries.size(), width, height, length);
        return blueprint;
    }

    public static boolean isTechnicalOrUnobtainable(BlockState state) {
        if (state == null || state.isAir()) return true;
        Block block = state.getBlock();
        return block == Blocks.STRUCTURE_VOID
                || block == Blocks.STRUCTURE_BLOCK
                || block == Blocks.JIGSAW
                || block == Blocks.BARRIER
                || block == Blocks.LIGHT
                || block == Blocks.BEDROCK
                || block == Blocks.REINFORCED_DEEPSLATE
                || block == Blocks.COMMAND_BLOCK
                || block == Blocks.CHAIN_COMMAND_BLOCK
                || block == Blocks.REPEATING_COMMAND_BLOCK;
    }

    /**
     * Returns true when {@code state} is the secondary voxel of a 2-block item
     * (upper half of a door/tall plant, or head part of a bed) that is placed automatically
     * when placing the primary block (lower half / foot part).
     */
    public static boolean isSecondaryMultiBlockPart(BlockState state) {
        if (state == null || state.isAir()) return false;
        Block block = state.getBlock();
        if (block instanceof DoorBlock && state.hasProperty(DoorBlock.HALF)) {
            return state.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER;
        }
        if (block instanceof BedBlock && state.hasProperty(BedBlock.PART)) {
            return state.getValue(BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD;
        }
        if (block instanceof net.minecraft.world.level.block.DoublePlantBlock
                && state.hasProperty(net.minecraft.world.level.block.DoublePlantBlock.HALF)) {
            return state.getValue(net.minecraft.world.level.block.DoublePlantBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER;
        }
        return false;
    }


    public static Blueprint importNbtStructure(InputStream inputStream, String blueprintName) throws Exception {
        CompoundTag root = NbtIo.readCompressed(inputStream);
        StructureTemplate template = new StructureTemplate();
        template.load(BuiltInRegistries.BLOCK.asLookup(), root);
        Vec3i size = template.getSize();
        int width = size.getX();
        int height = size.getY();
        int length = size.getZ();

        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IllegalArgumentException("Dimensiones de la estructura NBT inválidas: " + width + "x" + height + "x" + length);
        }
        if (width > 256 || height > 256 || length > 256) {
            throw new IllegalArgumentException("Las dimensiones de la estructura superan el máximo permitido (256x256x256): " + width + "x" + height + "x" + length);
        }

        List<Blueprint.BlockEntry> blockEntries = new ArrayList<>();

        // 1. Primary: Extract blocks directly from NBT structure tags
        net.minecraft.nbt.ListTag paletteTag = null;
        if (root.contains("palette", 9)) {
            paletteTag = root.getList("palette", 10);
        } else if (root.contains("palettes", 9)) {
            net.minecraft.nbt.ListTag palettes = root.getList("palettes", 9);
            if (!palettes.isEmpty()) {
                paletteTag = (net.minecraft.nbt.ListTag) palettes.get(0);
            }
        }

        if (paletteTag != null && root.contains("blocks", 9)) {
            List<BlockState> paletteList = new ArrayList<>(paletteTag.size());
            for (int i = 0; i < paletteTag.size(); i++) {
                paletteList.add(net.minecraft.nbt.NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), paletteTag.getCompound(i)));
            }

            net.minecraft.nbt.ListTag blocksTag = root.getList("blocks", 10);
            for (int i = 0; i < blocksTag.size(); i++) {
                CompoundTag blockCompound = blocksTag.getCompound(i);
                net.minecraft.nbt.ListTag posTag = blockCompound.getList("pos", 3);
                if (posTag.size() < 3) continue;
                int x = posTag.getInt(0);
                int y = posTag.getInt(1);
                int z = posTag.getInt(2);
                int stateIdx = blockCompound.getInt("state");
                if (stateIdx < 0 || stateIdx >= paletteList.size()) continue;
                BlockState state = paletteList.get(stateIdx);
                if (state == null || state.isAir() || isTechnicalOrUnobtainable(state)) continue;

                Blueprint.BuildPhase phase = determinePhase(y, height, state);
                blockEntries.add(new Blueprint.BlockEntry(new Vec3i(x, y, z), state, phase));
            }
        }

        // 2. Fallback: reflection on StructureTemplate.palettes
        if (blockEntries.isEmpty()) {
            try {
                java.lang.reflect.Field field = StructureTemplate.class.getDeclaredField("palettes");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<StructureTemplate.Palette> palettes = (List<StructureTemplate.Palette>) field.get(template);
                if (palettes != null && !palettes.isEmpty()) {
                    for (StructureTemplate.StructureBlockInfo info : palettes.get(0).blocks()) {
                        BlockState state = info.state();
                        if (state == null || state.isAir() || isTechnicalOrUnobtainable(state)) continue;
                        BlockPos p = info.pos();
                        Blueprint.BuildPhase phase = determinePhase(p.getY(), height, state);
                        blockEntries.add(new Blueprint.BlockEntry(new Vec3i(p.getX(), p.getY(), p.getZ()), state, phase));
                    }
                }
            } catch (Throwable ignored) {}
        }

        blockEntries.sort((a, b) -> {
            int phaseComp = a.phase().compareTo(b.phase());
            if (phaseComp != 0) return phaseComp;
            return Integer.compare(a.offset().getY(), b.offset().getY());
        });

        Blueprint blueprint = new Blueprint(blueprintName, blockEntries);
        AICompanionMod.LOGGER.info("[ESCLAVO-BUILD] Successfully imported NBT structure '{}' with {} blocks ({}x{}x{})",
                blueprintName, blockEntries.size(), width, height, length);
        return blueprint;
    }

    public static Blueprint importAndRegister(File file) {
        try {
            Blueprint bp = importFile(file);
            if (bp != null) {
                BlueprintRegistry.register(bp);
            }
            return bp;
        } catch (Exception e) {
            AICompanionMod.LOGGER.error("[ESCLAVO-BUILD] Failed to import structure {}: {}", file.getName(), e.getMessage());
            return null;
        }
    }

    public static void loadSchematicsDirectory(File schematicsDir) {
        if (!schematicsDir.exists()) {
            schematicsDir.mkdirs();
        }
        createReadmeIfAbsent(schematicsDir);
        File[] files = schematicsDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".schem") || lower.endsWith(".nbt") || lower.endsWith(".schematic");
        });
        if (files == null) return;
        for (File f : files) {
            importAndRegister(f);
        }
    }

    private static void createReadmeIfAbsent(File schematicsDir) {
        File readme = new File(schematicsDir, "LEEME_COMO_AGREGAR_CASAS.txt");
        if (readme.exists()) return;
        try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(new java.io.FileOutputStream(readme), java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write("================================================================================\r\n"
                    + "ESCLAVO — CARPETA DE ESQUEMATICOS Y CASAS DESCARGADAS\r\n"
                    + "================================================================================\r\n\r\n"
                    + "Aqui puedes colocar cualquier casa, mansion, castillo o estructura que descargues\r\n"
                    + "de internet o guardes tu mismo en Minecraft.\r\n\r\n"
                    + "FORMATOS COMPATIBLES:\r\n"
                    + "1. Archivos .schem (Formato moderno Sponge / WorldEdit 1.13+)\r\n"
                    + "2. Archivos .nbt (Formato oficial de Bloques de Estructuras / Structure Blocks de Minecraft)\r\n\r\n"
                    + "COMO USARLOS:\r\n"
                    + "1. Copia tu archivo (ej: 'micasa.schem' o 'torre.nbt') en esta carpeta.\r\n"
                    + "2. En el juego, abre la interfaz de Esclavo (Shift + Clic derecho en Esclavo o Silbato).\r\n"
                    + "3. Ve a la pestana 'Construccion' (icono de martillo / ladrillo).\r\n"
                    + "4. Con las flechas < y > podras ver y seleccionar tu casa descargada.\r\n"
                    + "5. Puedes rotarla (0, 90, 180, 270 grados) y pulsar:\r\n"
                    + "   - 'CONSTRUIR AHORA' (si tienes los materiales en el inventario).\r\n"
                    + "   - 'CONSEGUIR Y CONSTRUIR' (Esclavo saldra a talar, minar y recolectar los materiales).\r\n"
                    + "6. Tambien puedes ordenarlo por chat: 'construye micasa' o '/companion build micasa'.\r\n"
                    + "================================================================================\r\n");
        } catch (Exception e) {
            // Ignore error
        }
    }

    private static BlockState parseBlockState(String stateString) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), stateString, true).blockState();
        } catch (Exception ignored) {
            try {
                String cleanId = stateString.contains("[") ? stateString.substring(0, stateString.indexOf('[')) : stateString;
                ResourceLocation rl = new ResourceLocation(cleanId);
                Block block = BuiltInRegistries.BLOCK.get(rl);
                if (block != Blocks.AIR) {
                    return block.defaultBlockState();
                }
            } catch (Exception e) {
                // Return air fallback
            }
        }
        return Blocks.AIR.defaultBlockState();
    }

    public static boolean isOptionalFurnishing(BlockState state) {
        if (state == null || state.isAir()) return false;
        Block block = state.getBlock();
        return block instanceof BedBlock
                || block instanceof DoorBlock
                || block instanceof net.minecraft.world.level.block.FlowerPotBlock
                || block == Blocks.TORCH
                || block == Blocks.WALL_TORCH
                || block == Blocks.SOUL_TORCH
                || block == Blocks.SOUL_WALL_TORCH
                || block == Blocks.LANTERN
                || block == Blocks.SOUL_LANTERN
                || block == Blocks.CHEST
                || block == Blocks.TRAPPED_CHEST
                || block == Blocks.BARREL
                || block == Blocks.CRAFTING_TABLE
                || block == Blocks.FURNACE
                || block == Blocks.BLAST_FURNACE
                || block == Blocks.SMOKER
                || block == Blocks.BOOKSHELF
                || block == Blocks.CHISELED_BOOKSHELF
                || block == Blocks.LECTERN
                || block == Blocks.CARTOGRAPHY_TABLE
                || block == Blocks.FLETCHING_TABLE
                || block == Blocks.SMITHING_TABLE
                || block == Blocks.LOOM
                || block == Blocks.STONECUTTER
                || block == Blocks.GRINDSTONE
                || block == Blocks.ANVIL
                || block == Blocks.CHIPPED_ANVIL
                || block == Blocks.DAMAGED_ANVIL
                || block == Blocks.BREWING_STAND
                || block == Blocks.CAULDRON
                || block == Blocks.WATER_CAULDRON
                || block == Blocks.COMPOSTER
                || block == Blocks.BELL
                || block == Blocks.LADDER
                || block == Blocks.COBWEB
                || block == Blocks.CHAIN
                || block == Blocks.IRON_BARS
                || state.is(BlockTags.BEDS)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.WOOL_CARPETS)
                || state.is(BlockTags.BANNERS)
                || state.is(BlockTags.CANDLES)
                || state.is(BlockTags.ALL_SIGNS)
                || state.is(BlockTags.CAMPFIRES);
    }

    private static Blueprint.BuildPhase determinePhase(int y, int totalHeight, BlockState state) {
        if (y == 0) {
            return Blueprint.BuildPhase.FOUNDATION;
        }
        if (isOptionalFurnishing(state)) {
            return Blueprint.BuildPhase.FURNISHING;
        }
        if (y >= Math.max(1, totalHeight - 2) && (state.is(BlockTags.STAIRS) || state.is(BlockTags.SLABS) || y == totalHeight - 1)) {
            return Blueprint.BuildPhase.ROOF;
        }
        return Blueprint.BuildPhase.WALLS;
    }

    private static int readVarInt(byte[] data, int[] offset) {
        int value = 0;
        int length = 0;
        byte currentByte;
        while (offset[0] < data.length) {
            currentByte = data[offset[0]++];
            value |= (currentByte & 0x7F) << (length++ * 7);
            if (length > 5) break;
            if ((currentByte & 0x80) != 0x80) break;
        }
        return value;
    }
}
