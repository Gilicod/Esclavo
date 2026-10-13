package com.aicompanion;

import com.aicompanion.ai.building.Blueprint;
import com.aicompanion.ai.building.BlueprintRegistry;
import com.aicompanion.ai.building.BlueprintTransformer;
import com.aicompanion.ai.building.BuildingConfig;
import com.aicompanion.ai.building.SurvivalBlockBreakOperation;
import com.aicompanion.ai.equipment.ToolSelectionPolicy;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SurvivalBuilding11358Test {

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void testSurvivalBlockBreakOperationEnumsAndState() {
        SurvivalBlockBreakOperation op = new SurvivalBlockBreakOperation();
        assertFalse(op.isActive());
        assertEquals(0, op.getBreakTicks());
        assertEquals(0, op.getRequiredTicks());
        assertNull(op.getCurrentTarget());

        // Verify all enum constants
        assertEquals(4, SurvivalBlockBreakOperation.Status.values().length);
        assertNotNull(SurvivalBlockBreakOperation.Status.IN_PROGRESS);
        assertNotNull(SurvivalBlockBreakOperation.Status.SUCCESS);
        assertNotNull(SurvivalBlockBreakOperation.Status.BLOCKED);
        assertNotNull(SurvivalBlockBreakOperation.Status.CANCELLED);

        assertEquals(3, SurvivalBlockBreakOperation.BreakReason.values().length);
        assertNotNull(SurvivalBlockBreakOperation.BreakReason.TERRAIN_LEVELING);
        assertNotNull(SurvivalBlockBreakOperation.BreakReason.OBSTACLE_CLEARING);
        assertNotNull(SurvivalBlockBreakOperation.BreakReason.SCAFFOLD_CLEANUP);

        // Null companion / level / pos returns BLOCKED safely without NPE
        assertEquals(SurvivalBlockBreakOperation.Status.BLOCKED,
                op.tick(null, null, null, SurvivalBlockBreakOperation.BreakReason.OBSTACLE_CLEARING));
        assertFalse(op.isActive());
    }

    @Test
    public void testToolSelectionPolicyCoverage() {
        // Pickaxe Preferred Blocks
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState ironOre = Blocks.IRON_ORE.defaultBlockState();
        BlockState netherrack = Blocks.NETHERRACK.defaultBlockState();

        assertTrue(ToolSelectionPolicy.isPickaxePreferred(stone));
        assertTrue(ToolSelectionPolicy.isPickaxePreferred(deepslate));
        assertTrue(ToolSelectionPolicy.isPickaxePreferred(cobble));
        assertTrue(ToolSelectionPolicy.isPickaxePreferred(ironOre));
        assertTrue(ToolSelectionPolicy.isPickaxePreferred(netherrack));

        // Axe Preferred Blocks
        BlockState oakLog = Blocks.OAK_LOG.defaultBlockState();
        BlockState birchPlanks = Blocks.BIRCH_PLANKS.defaultBlockState();
        BlockState oakFence = Blocks.OAK_FENCE.defaultBlockState();
        BlockState oakDoor = Blocks.OAK_DOOR.defaultBlockState();
        BlockState chest = Blocks.CHEST.defaultBlockState();

        assertTrue(ToolSelectionPolicy.isAxePreferred(oakLog));
        assertTrue(ToolSelectionPolicy.isAxePreferred(birchPlanks));
        assertTrue(ToolSelectionPolicy.isAxePreferred(oakFence));
        assertTrue(ToolSelectionPolicy.isAxePreferred(oakDoor));
        assertTrue(ToolSelectionPolicy.isAxePreferred(chest));

        // Shovel Preferred Blocks
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState sand = Blocks.SAND.defaultBlockState();
        BlockState gravel = Blocks.GRAVEL.defaultBlockState();
        BlockState clay = Blocks.CLAY.defaultBlockState();

        assertTrue(ToolSelectionPolicy.isShovelPreferred(dirt));
        assertTrue(ToolSelectionPolicy.isShovelPreferred(grass));
        assertTrue(ToolSelectionPolicy.isShovelPreferred(sand));
        assertTrue(ToolSelectionPolicy.isShovelPreferred(gravel));
        assertTrue(ToolSelectionPolicy.isShovelPreferred(clay));
    }

    @Test
    public void testManualCoordsBuildingConfigPersistence() {
        BuildingConfig cfg = new BuildingConfig("modern_villa");
        cfg.setPlacementMode(BuildingConfig.PlacementMode.MANUAL_COORDS);
        cfg.setTargetPos(new BlockPos(1250, 68, -840));
        cfg.setBuildMode(BuildingConfig.BuildMode.SINGLE_MATERIAL_HOLLOW);
        cfg.setPrimaryMaterial(new ResourceLocation("minecraft", "quartz_block"));
        cfg.setRotation(180);
        cfg.setAutoGather(false);

        String serialized = cfg.serialize();
        assertTrue(serialized.contains("place=MANUAL_COORDS"));
        assertTrue(serialized.contains("pos=1250,68,-840"));
        assertTrue(serialized.contains("mat=minecraft:quartz_block"));
        assertTrue(serialized.contains("mode=SINGLE_MATERIAL_HOLLOW"));

        BuildingConfig deserialized = BuildingConfig.deserialize(serialized);
        assertEquals(BuildingConfig.PlacementMode.MANUAL_COORDS, deserialized.getPlacementMode());
        assertEquals(new BlockPos(1250, 68, -840), deserialized.getTargetPos());
        assertEquals(BuildingConfig.BuildMode.SINGLE_MATERIAL_HOLLOW, deserialized.getBuildMode());
        assertEquals(new ResourceLocation("minecraft", "quartz_block"), deserialized.getPrimaryMaterial());
        assertEquals(180, deserialized.getRotation());
    }

    @Test
    public void testBuildingConfigTransformationPersistence() {
        Blueprint base = BlueprintRegistry.get("small_house");
        assertNotNull(base, "Default blueprint 'small_house' must be present in registry");

        BuildingConfig cfg = new BuildingConfig("small_house");
        cfg.setBuildMode(BuildingConfig.BuildMode.SINGLE_MATERIAL);
        cfg.setPrimaryMaterial(new ResourceLocation("minecraft", "deepslate_bricks"));
        cfg.setRotation(90);

        Blueprint transformed = BlueprintTransformer.transform(base, cfg);
        assertNotNull(transformed);
        assertTrue(transformed.getName().contains("custom"));

        // Simulate save to NBT
        CompoundTag tag = new CompoundTag();
        tag.putString("BlueprintName", base.getName());
        tag.putString("BuildingConfig", cfg.serialize());

        // Simulate reload from NBT
        assertTrue(tag.contains("BuildingConfig"));
        BuildingConfig loadedCfg = BuildingConfig.deserialize(tag.getString("BuildingConfig"));
        assertEquals(BuildingConfig.BuildMode.SINGLE_MATERIAL, loadedCfg.getBuildMode());
        assertEquals(new ResourceLocation("minecraft", "deepslate_bricks"), loadedCfg.getPrimaryMaterial());
        assertEquals(90, loadedCfg.getRotation());

        Blueprint reloadedTransformed = BlueprintTransformer.transform(base, loadedCfg);
        assertNotNull(reloadedTransformed);
        assertEquals(transformed.getDimensions(), reloadedTransformed.getDimensions());
        assertEquals(transformed.getBlocks().size(), reloadedTransformed.getBlocks().size());
    }
}
