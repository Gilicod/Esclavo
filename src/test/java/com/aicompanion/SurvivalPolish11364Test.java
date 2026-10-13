package com.aicompanion;

import com.aicompanion.ai.building.Blueprint;
import com.aicompanion.ai.building.BuildProject;
import com.aicompanion.ai.building.BuildingConfig;
import com.aicompanion.ai.building.BuildingMaterialsCalculator;
import com.aicompanion.ai.building.SchematicImporter;
import com.aicompanion.util.TemporaryBlockTracker;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SurvivalPolish11364Test {

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void testSecondaryMultiBlockPartFiltering() {
        BlockState doorLower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState doorUpper = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        assertFalse(SchematicImporter.isSecondaryMultiBlockPart(doorLower), "Lower door half must not be filtered");
        assertTrue(SchematicImporter.isSecondaryMultiBlockPart(doorUpper), "Upper door half must be filtered as secondary part");

        BlockState bedFoot = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT);
        BlockState bedHead = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD);
        assertFalse(SchematicImporter.isSecondaryMultiBlockPart(bedFoot), "Bed foot must not be filtered");
        assertTrue(SchematicImporter.isSecondaryMultiBlockPart(bedHead), "Bed head must be filtered as secondary part");

        BlockState tallGrassLower = Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState tallGrassUpper = Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
        assertFalse(SchematicImporter.isSecondaryMultiBlockPart(tallGrassLower), "Lower tall plant must not be filtered");
        assertTrue(SchematicImporter.isSecondaryMultiBlockPart(tallGrassUpper), "Upper tall plant must be filtered as secondary part");
    }

    @Test
    public void testTechnicalOrUnobtainableBlockFiltering() {
        assertTrue(SchematicImporter.isTechnicalOrUnobtainable(Blocks.JIGSAW.defaultBlockState()));
        assertTrue(SchematicImporter.isTechnicalOrUnobtainable(Blocks.STRUCTURE_BLOCK.defaultBlockState()));
        assertTrue(SchematicImporter.isTechnicalOrUnobtainable(Blocks.STRUCTURE_VOID.defaultBlockState()));
        assertTrue(SchematicImporter.isTechnicalOrUnobtainable(Blocks.BARRIER.defaultBlockState()));
        assertTrue(SchematicImporter.isTechnicalOrUnobtainable(Blocks.LIGHT.defaultBlockState()));
        assertTrue(SchematicImporter.isTechnicalOrUnobtainable(Blocks.BEDROCK.defaultBlockState()));

        assertFalse(SchematicImporter.isTechnicalOrUnobtainable(Blocks.OAK_PLANKS.defaultBlockState()));
        assertFalse(SchematicImporter.isTechnicalOrUnobtainable(Blocks.COBBLESTONE.defaultBlockState()));
        assertFalse(SchematicImporter.isTechnicalOrUnobtainable(Blocks.WHITE_BED.defaultBlockState()));
    }

    @Test
    public void testBuildingMaterialsCalculatorDeduplicatesMultiBlocksAndFiltersTechnical() {
        BlockState doorLower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState doorUpper = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        BlockState bedFoot = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT);
        BlockState bedHead = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD);
        BlockState jigsaw = Blocks.JIGSAW.defaultBlockState();

        Blueprint bp = new Blueprint(
                "test_multiblock_house",
                List.of(
                        new Blueprint.BlockEntry(new BlockPos(0, 0, 0), doorLower, Blueprint.BuildPhase.WALLS),
                        new Blueprint.BlockEntry(new BlockPos(0, 1, 0), doorUpper, Blueprint.BuildPhase.WALLS),
                        new Blueprint.BlockEntry(new BlockPos(1, 0, 0), bedFoot, Blueprint.BuildPhase.FURNISHING),
                        new Blueprint.BlockEntry(new BlockPos(1, 0, 1), bedHead, Blueprint.BuildPhase.FURNISHING),
                        new Blueprint.BlockEntry(new BlockPos(2, 0, 0), jigsaw, Blueprint.BuildPhase.FOUNDATION)
                )
        );

        BuildingMaterialsCalculator.MaterialSummary summary = BuildingMaterialsCalculator.calculate(bp, null);

        assertEquals(2, summary.totalBlocksToPlace(), "Only 1 door (lower) and 1 bed (foot) should be counted");
        assertEquals(1, summary.required().getOrDefault(Items.OAK_DOOR, 0), "Door count must be 1, not 2");
        assertEquals(1, summary.required().getOrDefault(Items.WHITE_BED, 0), "Bed count must be 1, not 2");
        assertFalse(summary.required().containsKey(Items.JIGSAW), "Jigsaw block must never be required");
    }

    @Test
    public void testTemporaryBlockTrackerPurgeDistance() {
        TemporaryBlockTracker tracker = new TemporaryBlockTracker();
        BlockPos nearPos = new BlockPos(10, 64, 10);
        BlockPos farPos = new BlockPos(200, 64, 200);
        tracker.track(nearPos, Blocks.DIRT);
        tracker.track(farPos, Blocks.DIRT);
        assertEquals(2, tracker.size());

        // Purge with null level (only distance check relative to anchor (0,64,0), maxDistSq = 2304 (48m))
        BlockPos nearest = tracker.purgeInvalidAndNearest(null, new Vec3(0, 64, 0), new BlockPos(0, 64, 0), 2304.0);
        assertEquals(nearPos, nearest, "Near scaffold block must be preserved and returned");
        assertEquals(1, tracker.size(), "Far scaffold block (>48m from anchor and companion) must be purged");
    }

    @Test
    public void testBuildProjectPersistenceAndAnchorContinuity() {
        BuildingConfig cfg = new BuildingConfig("casa_llanura_mediana");
        cfg.setRotation(90);
        cfg.setBuildMode(BuildingConfig.BuildMode.ORIGINAL);

        BlockPos anchor = new BlockPos(340, 70, -195);
        ResourceLocation overworld = new ResourceLocation("minecraft", "overworld");
        BuildProject project = new BuildProject(
                "casa_llanura_mediana",
                "Casa Llanura Mediana",
                anchor,
                overworld,
                cfg,
                420
        );
        project.setProjectId("PRJ-TEST1234");
        project.setCurrentBlockIndex(185);
        project.setStatus(BuildProject.Status.IN_PROGRESS);

        CompoundTag saved = project.save();
        BuildProject loaded = BuildProject.load(saved);

        assertNotNull(loaded);
        assertEquals("PRJ-TEST1234", loaded.getProjectId());
        assertEquals("casa_llanura_mediana", loaded.getBlueprintId());
        assertEquals(anchor, loaded.getOriginPos(), "Origin anchor must never shift after reload");
        assertEquals(90, loaded.getBuildingConfig().getRotation());
        assertEquals(overworld, loaded.getDimension());
        assertEquals(185, loaded.getCurrentBlockIndex());
        assertEquals(420, loaded.getTotalBlocks());
        assertEquals(BuildProject.Status.IN_PROGRESS, loaded.getStatus());
        assertTrue(loaded.isActive());
        assertTrue(loaded.isSameProject("casa_llanura_mediana", overworld));
    }
}
