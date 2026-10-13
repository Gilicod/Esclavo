package com.aicompanion;

import com.aicompanion.ai.building.BuildingConfig;
import com.aicompanion.ai.building.Blueprint;
import com.aicompanion.ai.building.BlueprintRegistry;
import com.aicompanion.ai.building.BlueprintTransformer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BuildingStudio11354Test {

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void testBuildingConfigDefaults() {
        BuildingConfig cfg = new BuildingConfig("small_house");
        assertEquals("small_house", cfg.getBlueprintId());
        assertEquals(BuildingConfig.BuildMode.ORIGINAL, cfg.getBuildMode());
        assertEquals(0, cfg.getRotation());
        assertEquals(BuildingConfig.PlacementMode.NEAR_PLAYER, cfg.getPlacementMode());
        assertTrue(cfg.isKeepRoof());
        assertTrue(cfg.isKeepDoors());
        assertTrue(cfg.isKeepWindows());
        assertTrue(cfg.isKeepFurniture());
    }

    @Test
    public void testBuildingConfigSerializationRoundtrip() {
        BuildingConfig cfg = new BuildingConfig("modern_mansion");
        cfg.setBuildMode(BuildingConfig.BuildMode.SINGLE_MATERIAL_HOLLOW);
        cfg.setMaterialMode(BuildingConfig.MaterialMode.FAMILY_SUBSTITUTION);
        cfg.setPrimaryMaterial(new ResourceLocation("minecraft", "deepslate_bricks"));
        cfg.setRotation(180);
        cfg.setPlacementMode(BuildingConfig.PlacementMode.MANUAL_COORDS);
        cfg.setTargetPos(new BlockPos(100, 64, -200));
        cfg.setAutoGather(true);
        cfg.setKeepRoof(false);
        cfg.setKeepFurniture(false);

        String serialized = cfg.serialize();
        assertTrue(serialized.startsWith("cfg:id=modern_mansion;"));
        assertTrue(serialized.contains("mode=SINGLE_MATERIAL_HOLLOW"));
        assertTrue(serialized.contains("mat=minecraft:deepslate_bricks"));
        assertTrue(serialized.contains("rot=180"));
        assertTrue(serialized.contains("place=MANUAL_COORDS"));
        assertTrue(serialized.contains("pos=100,64,-200"));
        assertTrue(serialized.contains("gather=true"));
        assertTrue(serialized.contains("roof=false"));
        assertTrue(serialized.contains("furn=false"));

        BuildingConfig deserialized = BuildingConfig.deserialize(serialized);
        assertEquals("modern_mansion", deserialized.getBlueprintId());
        assertEquals(BuildingConfig.BuildMode.SINGLE_MATERIAL_HOLLOW, deserialized.getBuildMode());
        assertEquals(BuildingConfig.MaterialMode.FAMILY_SUBSTITUTION, deserialized.getMaterialMode());
        assertEquals(new ResourceLocation("minecraft", "deepslate_bricks"), deserialized.getPrimaryMaterial());
        assertEquals(180, deserialized.getRotation());
        assertEquals(BuildingConfig.PlacementMode.MANUAL_COORDS, deserialized.getPlacementMode());
        assertNotNull(deserialized.getTargetPos());
        assertEquals(100, deserialized.getTargetPos().getX());
        assertEquals(64, deserialized.getTargetPos().getY());
        assertEquals(-200, deserialized.getTargetPos().getZ());
        assertTrue(deserialized.isAutoGather());
        assertFalse(deserialized.isKeepRoof());
        assertFalse(deserialized.isKeepFurniture());
    }

    @Test
    public void testLegacyPayloadDeserialization() {
        // Legacy format: "small_house:oak:cobblestone:rot=90:auto_gather"
        BuildingConfig cfg = BuildingConfig.deserialize("small_house:oak:cobblestone:rot=90:auto_gather");
        assertEquals("small_house", cfg.getBlueprintId());
        assertEquals(90, cfg.getRotation());
        assertTrue(cfg.isAutoGather());
        assertEquals(BuildingConfig.BuildMode.ORIGINAL, cfg.getBuildMode());
    }

    @Test
    public void testBlueprintTransformationOriginal() {
        Blueprint bp = BlueprintRegistry.get("small_house");
        assertNotNull(bp, "small_house blueprint must exist in registry");

        BuildingConfig cfg = new BuildingConfig("small_house");
        cfg.setBuildMode(BuildingConfig.BuildMode.ORIGINAL);
        cfg.setRotation(0);

        Blueprint transformed = BlueprintTransformer.transform(bp, cfg);
        assertNotNull(transformed);
        assertEquals(bp.getBlocks().size(), transformed.getBlocks().size());
    }

    @Test
    public void testBlueprintTransformationRotation() {
        Blueprint bp = BlueprintRegistry.get("small_house");
        assertNotNull(bp);

        BuildingConfig cfg90 = new BuildingConfig("small_house");
        cfg90.setRotation(90);

        Blueprint transformed90 = BlueprintTransformer.transform(bp, cfg90);
        assertNotNull(transformed90);
        assertEquals(bp.getBlocks().size(), transformed90.getBlocks().size());
        // Dimensions should swap X and Z for 90-degree rotation
        assertEquals(bp.getDimensions().getX(), transformed90.getDimensions().getZ());
        assertEquals(bp.getDimensions().getZ(), transformed90.getDimensions().getX());
    }

    @Test
    public void testBlueprintTransformationNoDecoration() {
        Blueprint bp = BlueprintRegistry.get("small_house");
        assertNotNull(bp);

        BuildingConfig cfgNoDecor = new BuildingConfig("small_house");
        cfgNoDecor.setBuildMode(BuildingConfig.BuildMode.NO_DECORATION);

        Blueprint transformed = BlueprintTransformer.transform(bp, cfgNoDecor);
        assertNotNull(transformed);
        // Interior furniture and decor (bed, crafting table, chest, furnace, etc.) omitted
        assertTrue(transformed.getBlocks().size() < bp.getBlocks().size(),
                "NO_DECORATION mode should reduce total block count by removing interior elements");
    }

    @Test
    public void testBlueprintTransformationSingleMaterialHollow() {
        Blueprint bp = BlueprintRegistry.get("small_house");
        assertNotNull(bp);

        BuildingConfig cfgHollow = new BuildingConfig("small_house");
        cfgHollow.setBuildMode(BuildingConfig.BuildMode.SINGLE_MATERIAL_HOLLOW);
        cfgHollow.setPrimaryMaterial(new ResourceLocation("minecraft", "stone_bricks"));

        Blueprint transformed = BlueprintTransformer.transform(bp, cfgHollow);
        assertNotNull(transformed);
        assertTrue(transformed.getBlocks().size() > 0);
        assertTrue(transformed.getBlocks().size() < bp.getBlocks().size(),
                "SINGLE_MATERIAL_HOLLOW should omit all interior blocks");
    }

    @Test
    public void testImportCustomNbtHouses() throws Exception {
        java.io.File file = new java.io.File("config/esclavo/schematics/casa_llanura_pequena.nbt");
        assertTrue(file.exists(), "casa_llanura_pequena.nbt must exist in config/esclavo/schematics/");
        Blueprint bp = com.aicompanion.ai.building.SchematicImporter.importFile(file);
        assertNotNull(bp);
        assertTrue(bp.getBlocks().size() > 0, "Imported house must contain blocks");
        assertTrue(bp.getDimensions().getX() > 0);
        assertTrue(bp.getDimensions().getY() > 0);
        assertTrue(bp.getDimensions().getZ() > 0);
    }
}
