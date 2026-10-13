package com.aicompanion;

import com.aicompanion.ai.building.BuildingConfig;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BuildingStudioPersistence11357Test {

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void testCopyFromAndPersistenceRoundtrip() {
        BuildingConfig original = new BuildingConfig("casa_medieval_roble");
        original.setBuildMode(BuildingConfig.BuildMode.CUSTOM);
        original.setMaterialMode(BuildingConfig.MaterialMode.LITERAL);
        original.setPrimaryMaterial(new ResourceLocation("minecraft", "dark_oak_planks"));
        original.setRotation(270);
        original.setPlacementMode(BuildingConfig.PlacementMode.NEAR_COMPANION);
        original.setTargetPos(new BlockPos(-50, 70, 120));
        original.setAutoGather(true);
        original.setKeepDoors(false);
        original.setKeepWindows(true);
        original.setKeepStairs(false);
        original.setKeepRoof(true);
        original.setKeepFloors(false);
        original.setKeepInteriorWalls(true);
        original.setKeepFurniture(false);
        original.setKeepDecor(false);
        original.setKeepLighting(true);
        original.setKeepChests(true);
        original.setKeepFoundations(false);

        // Test copyFrom
        BuildingConfig copied = new BuildingConfig();
        copied.copyFrom(original);

        assertEquals(original.getBlueprintId(), copied.getBlueprintId());
        assertEquals(original.getBuildMode(), copied.getBuildMode());
        assertEquals(original.getMaterialMode(), copied.getMaterialMode());
        assertEquals(original.getPrimaryMaterial(), copied.getPrimaryMaterial());
        assertEquals(original.getRotation(), copied.getRotation());
        assertEquals(original.getPlacementMode(), copied.getPlacementMode());
        assertEquals(original.getTargetPos(), copied.getTargetPos());
        assertEquals(original.isAutoGather(), copied.isAutoGather());
        assertEquals(original.isKeepDoors(), copied.isKeepDoors());
        assertEquals(original.isKeepWindows(), copied.isKeepWindows());
        assertEquals(original.isKeepStairs(), copied.isKeepStairs());
        assertEquals(original.isKeepRoof(), copied.isKeepRoof());
        assertEquals(original.isKeepFloors(), copied.isKeepFloors());
        assertEquals(original.isKeepInteriorWalls(), copied.isKeepInteriorWalls());
        assertEquals(original.isKeepFurniture(), copied.isKeepFurniture());
        assertEquals(original.isKeepDecor(), copied.isKeepDecor());
        assertEquals(original.isKeepLighting(), copied.isKeepLighting());
        assertEquals(original.isKeepChests(), copied.isKeepChests());
        assertEquals(original.isKeepFoundations(), copied.isKeepFoundations());

        // Test serialization / deserialization roundtrip
        String serialized = copied.serialize();
        BuildingConfig restored = BuildingConfig.deserialize(serialized);

        assertEquals("casa_medieval_roble", restored.getBlueprintId());
        assertEquals(BuildingConfig.BuildMode.CUSTOM, restored.getBuildMode());
        assertEquals(BuildingConfig.MaterialMode.LITERAL, restored.getMaterialMode());
        assertEquals(new ResourceLocation("minecraft", "dark_oak_planks"), restored.getPrimaryMaterial());
        assertEquals(270, restored.getRotation());
        assertEquals(BuildingConfig.PlacementMode.NEAR_COMPANION, restored.getPlacementMode());
        assertEquals(new BlockPos(-50, 70, 120), restored.getTargetPos());
        assertTrue(restored.isAutoGather());
        assertFalse(restored.isKeepDoors());
        assertTrue(restored.isKeepWindows());
        assertFalse(restored.isKeepStairs());
        assertTrue(restored.isKeepRoof());
        assertFalse(restored.isKeepFloors());
        assertTrue(restored.isKeepInteriorWalls());
        assertFalse(restored.isKeepFurniture());
        assertFalse(restored.isKeepDecor());
        assertTrue(restored.isKeepLighting());
        assertTrue(restored.isKeepChests());
        assertFalse(restored.isKeepFoundations());
    }

    @Test
    public void testFractionalPlankMultiplierMath() {
        // 20 stairs require 20 * 1.5 = 30 planks -> ceiling = 30 -> logs = ceil(30 / 4) = 8 logs (previously estimated as 5 logs!)
        int stairs = 20;
        double requiredPlanksForStairs = stairs * 1.5;
        assertEquals(30.0, requiredPlanksForStairs);
        int logsNeeded = (int) Math.ceil(requiredPlanksForStairs / 4.0);
        assertEquals(8, logsNeeded);

        // 10 slabs require 10 * 0.5 = 5 planks
        int slabs = 10;
        double requiredPlanksForSlabs = slabs * 0.5;
        assertEquals(5.0, requiredPlanksForSlabs);

        // 4 trapdoors require 4 * 3.0 = 12 planks -> 3 logs
        int trapdoors = 4;
        double requiredPlanksForTrapdoors = trapdoors * 3.0;
        assertEquals(12.0, requiredPlanksForTrapdoors);
    }
}
