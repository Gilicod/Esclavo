package com.aicompanion;

import com.aicompanion.ai.dimension.DimensionalResourcePolicy;
import com.aicompanion.ai.mining.MiningTarget;
import com.aicompanion.ai.parser.TaskParser;
import com.aicompanion.ai.planning.Goal;
import com.aicompanion.client.gui.hud.HudConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enterprise Behavior & Integration Test Suite for Esclavo 1.13.27:
 * - HUD & Compass Configuration persistence, validation, and defaults.
 * - Dimensional Resource Policy (Nether, End, Overworld resource classification).
 * - Multi-dimensional goal parsing and mining target validation.
 */
public class Companion11327BehaviorTest {

    @Test
    public void testHudConfigDefaultsAndValidation() {
        HudConfig config = new HudConfig();

        // Check defaults
        assertTrue(config.hudVisible);
        assertTrue(config.compassEnabled);
        assertEquals(1.0f, config.hudScale, 0.001f);
        assertEquals(0.8f, config.hudOpacity, 0.001f);
        assertEquals(HudConfig.DisplayMode.FULL, config.hudMode);
        assertEquals(HudConfig.CompassStyle.CIRCULAR, config.compassStyle);
        assertEquals(HudConfig.CompassPreset.TOP_RIGHT, config.compassPreset);

        // Test validation clamping
        config.hudScale = 0.1f;
        config.validate();
        assertEquals(0.40f, config.hudScale, 0.001f);

        config.hudScale = 2.5f;
        config.validate();
        assertEquals(1.50f, config.hudScale, 0.001f);

        config.hudOpacity = -0.5f;
        config.validate();
        assertEquals(0.0f, config.hudOpacity, 0.001f);

        config.hudOpacity = 1.8f;
        config.validate();
        assertEquals(1.0f, config.hudOpacity, 0.001f);
    }

    @Test
    public void testDimensionalResourcePolicyNether() {
        assertTrue(DimensionalResourcePolicy.isNetherResource("ancient_debris"));
        assertTrue(DimensionalResourcePolicy.isNetherResource("netherite_scrap"));
        assertTrue(DimensionalResourcePolicy.isNetherResource("quartz"));
        assertTrue(DimensionalResourcePolicy.isNetherResource("nether_quartz_ore"));
        assertTrue(DimensionalResourcePolicy.isNetherResource("glowstone"));
        assertTrue(DimensionalResourcePolicy.isNetherResource("blaze_rod"));
        assertTrue(DimensionalResourcePolicy.isNetherResource("ghast_tear"));
        assertTrue(DimensionalResourcePolicy.isNetherResource("wither_skeleton_skull"));
        assertTrue(DimensionalResourcePolicy.isNetherResource("soul_sand"));
        assertTrue(DimensionalResourcePolicy.isNetherResource("blackstone"));

        assertEquals(new net.minecraft.resources.ResourceLocation("the_nether"), DimensionalResourcePolicy.getRequiredDimensionId("ancient_debris"));
        assertEquals(new net.minecraft.resources.ResourceLocation("the_nether"), DimensionalResourcePolicy.getRequiredDimensionId("quartz"));
        assertEquals(new net.minecraft.resources.ResourceLocation("the_nether"), DimensionalResourcePolicy.getRequiredDimensionId("blaze"));
    }

    @Test
    public void testDimensionalResourcePolicyEnd() {
        assertTrue(DimensionalResourcePolicy.isEndResource("shulker_shell"));
        assertTrue(DimensionalResourcePolicy.isEndResource("shulker_box"));
        assertTrue(DimensionalResourcePolicy.isEndResource("elytra"));
        assertTrue(DimensionalResourcePolicy.isEndResource("end_stone"));
        assertTrue(DimensionalResourcePolicy.isEndResource("chorus_fruit"));
        assertTrue(DimensionalResourcePolicy.isEndResource("purpur_block"));

        assertEquals(new net.minecraft.resources.ResourceLocation("the_end"), DimensionalResourcePolicy.getRequiredDimensionId("elytra"));
        assertEquals(new net.minecraft.resources.ResourceLocation("the_end"), DimensionalResourcePolicy.getRequiredDimensionId("shulker_shell"));
    }

    @Test
    public void testDimensionalResourcePolicyOverworld() {
        assertFalse(DimensionalResourcePolicy.isNetherResource("iron_ore"));
        assertFalse(DimensionalResourcePolicy.isEndResource("iron_ore"));
        assertEquals(new net.minecraft.resources.ResourceLocation("overworld"), DimensionalResourcePolicy.getRequiredDimensionId("iron_ore"));
        assertEquals(new net.minecraft.resources.ResourceLocation("overworld"), DimensionalResourcePolicy.getRequiredDimensionId("diamond"));
        assertEquals(new net.minecraft.resources.ResourceLocation("overworld"), DimensionalResourcePolicy.getRequiredDimensionId("oak_log"));
    }

    @Test
    public void testMiningTargetNetheriteAndQuartz() {
        java.util.Set<String> netheriteDrops = java.util.Set.of("ancient_debris", "netherite_scrap");
        assertTrue(MiningTarget.isExactDropName(netheriteDrops, "ancient_debris"));
        assertTrue(MiningTarget.isExactDropName(netheriteDrops, "netherite_scrap"));
        assertFalse(MiningTarget.isExactDropName(netheriteDrops, "diamond_pickaxe"));

        java.util.Set<String> quartzDrops = java.util.Set.of("quartz");
        assertTrue(MiningTarget.isExactDropName(quartzDrops, "quartz"));

        java.util.Set<String> obsidianDrops = java.util.Set.of("obsidian");
        assertTrue(MiningTarget.isExactDropName(obsidianDrops, "obsidian"));
    }

    @Test
    public void testNetherGoalParsing() {
        List<Goal> goals = TaskParser.parse("mina cuarzo 16 y luego vuelve conmigo");
        assertEquals(2, goals.size());
        assertEquals(Goal.Type.OBTAIN, goals.get(0).getType());
        assertEquals(Goal.Type.RETURN_OWNER, goals.get(1).getType());
    }
}
