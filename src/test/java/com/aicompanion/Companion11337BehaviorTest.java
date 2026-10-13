package com.aicompanion;

import com.aicompanion.ai.dimension.DimensionalResourcePolicy;
import com.aicompanion.ai.gathering.GatherStrategyRegistry;
import com.aicompanion.ai.mining.MiningTarget;
import com.aicompanion.ai.parser.SpanishAliases;
import com.aicompanion.ai.parser.TaskParser;
import com.aicompanion.ai.planning.Goal;
import com.aicompanion.ai.planning.RecipeDependencyGraph;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enterprise Autonomous Expedition & Multi-Dimensional Test Suite for Esclavo 1.13.37:
 * - Natural Language Parsing for Nether expeditions ("minar netherita 2 y traer a mi", "conseguir cuarzo 16").
 * - Spanish Aliases and Resource mapping for Ancient Debris, Netherite, Quartz.
 * - Gather Strategy mapping (mining vs smelting vs crafting for Netherite progression).
 * - Tool tier verification (Diamond Pickaxe for Ancient Debris & Obsidian).
 * - Dimensional Resource Policy for Nether travel prerequisites.
 */
public class Companion11337BehaviorTest {

    @Test
    public void testParseMineNetheriteAndDeliverToOwner() {
        List<Goal> goals = TaskParser.parse("minar netherita 2 y traer a mi");
        assertNotNull(goals);
        assertEquals(2, goals.size(), "Should parse obtain goal + deliver to owner goal");

        Goal obtainGoal = goals.get(0);
        assertEquals(Goal.Type.OBTAIN, obtainGoal.getType());
        assertEquals(new ResourceLocation("minecraft", "ancient_debris"), obtainGoal.getTargetResource());
        assertEquals(2, obtainGoal.getAmount());

        Goal returnGoal = goals.get(1);
        assertEquals(Goal.Type.RETURN_OWNER, returnGoal.getType());
        assertTrue(returnGoal.isDelivery(), "Should mark return as delivery");
    }

    @Test
    public void testParseConseguirCuarzoAndDeliverToOwner() {
        List<Goal> goals = TaskParser.parse("conseguir cuarzo 16 y traer a mi");
        assertNotNull(goals);
        assertEquals(2, goals.size());

        Goal obtainGoal = goals.get(0);
        assertEquals(Goal.Type.OBTAIN, obtainGoal.getType());
        assertEquals(new ResourceLocation("minecraft", "nether_quartz_ore"), obtainGoal.getTargetResource());
        assertEquals(16, obtainGoal.getAmount());

        Goal returnGoal = goals.get(1);
        assertEquals(Goal.Type.RETURN_OWNER, returnGoal.getType());
        assertTrue(returnGoal.isDelivery());
    }

    @Test
    public void testParseMinarCuarzoDirect() {
        List<Goal> goals = TaskParser.parse("minar cuarzo 16");
        assertNotNull(goals);
        assertFalse(goals.isEmpty());

        Goal obtainGoal = goals.get(0);
        assertEquals(Goal.Type.OBTAIN, obtainGoal.getType());
        assertEquals(new ResourceLocation("minecraft", "nether_quartz_ore"), obtainGoal.getTargetResource());
        assertEquals(16, obtainGoal.getAmount());
    }

    @Test
    public void testSpanishAliasesForNetherOres() {
        assertEquals(new ResourceLocation("minecraft", "ancient_debris"), SpanishAliases.resolveOre("netherita"));
        assertEquals(new ResourceLocation("minecraft", "ancient_debris"), SpanishAliases.resolveOre("netherite"));
        assertEquals(new ResourceLocation("minecraft", "ancient_debris"), SpanishAliases.resolveOre("escombros ancestrales"));
        assertEquals(new ResourceLocation("minecraft", "nether_quartz_ore"), SpanishAliases.resolveOre("cuarzo"));
        assertEquals(new ResourceLocation("minecraft", "nether_quartz_ore"), SpanishAliases.resolveOre("quartz"));
        assertEquals(new ResourceLocation("minecraft", "nether_quartz_ore"), SpanishAliases.resolveOre("cuarzo del nether"));
    }

    @Test
    public void testGatherStrategyRegistryNetheriteChain() {
        assertEquals(GatherStrategyRegistry.StrategyType.BLOCK_MINE,
                GatherStrategyRegistry.determineStrategy(new ResourceLocation("minecraft", "ancient_debris")));
        assertEquals(GatherStrategyRegistry.StrategyType.BLOCK_MINE,
                GatherStrategyRegistry.determineStrategy(new ResourceLocation("minecraft", "netherite")));
        assertEquals(GatherStrategyRegistry.StrategyType.SMELTING,
                GatherStrategyRegistry.determineStrategy(new ResourceLocation("minecraft", "netherite_scrap")));
        assertEquals(GatherStrategyRegistry.StrategyType.CRAFTING,
                GatherStrategyRegistry.determineStrategy(new ResourceLocation("minecraft", "netherite_ingot")));
        assertEquals(GatherStrategyRegistry.StrategyType.BLOCK_MINE,
                GatherStrategyRegistry.determineStrategy(new ResourceLocation("minecraft", "quartz")));

        assertEquals(new ResourceLocation("minecraft", "ancient_debris"),
                GatherStrategyRegistry.getRawSmeltingSource(new ResourceLocation("minecraft", "netherite_scrap")));
    }

    @Test
    public void testPickaxeTierRequirements() {
        MiningTarget debrisTarget = new MiningTarget("ancient_debris", 8, 24);
        assertEquals(RecipeDependencyGraph.TIER_DIAMOND, RecipeDependencyGraph.getRequiredPickaxeTier(debrisTarget));

        MiningTarget netheriteTarget = new MiningTarget("netherite", 8, 24);
        assertEquals(RecipeDependencyGraph.TIER_DIAMOND, RecipeDependencyGraph.getRequiredPickaxeTier(netheriteTarget));

        MiningTarget obsidianTarget = new MiningTarget("obsidian", -64, 30);
        assertEquals(RecipeDependencyGraph.TIER_DIAMOND, RecipeDependencyGraph.getRequiredPickaxeTier(obsidianTarget));

        MiningTarget diamondTarget = new MiningTarget("diamond", -64, 16);
        assertEquals(RecipeDependencyGraph.TIER_IRON, RecipeDependencyGraph.getRequiredPickaxeTier(diamondTarget));

        MiningTarget ironTarget = new MiningTarget("iron", -16, 80);
        assertEquals(RecipeDependencyGraph.TIER_STONE, RecipeDependencyGraph.getRequiredPickaxeTier(ironTarget));
    }

    @Test
    public void testDimensionalResourcePolicyForNetherExpeditions() {
        assertTrue(DimensionalResourcePolicy.isNetherResource("netherita"));
        assertTrue(DimensionalResourcePolicy.isNetherResource("netherite"));
        assertTrue(DimensionalResourcePolicy.isNetherResource("ancient_debris"));
        assertTrue(DimensionalResourcePolicy.isNetherResource("cuarzo"));
        assertTrue(DimensionalResourcePolicy.isNetherResource("quartz"));
        assertTrue(DimensionalResourcePolicy.isNetherResource("glowstone"));

        assertEquals(new ResourceLocation("the_nether"), DimensionalResourcePolicy.getRequiredDimensionId("netherita"));
        assertEquals(new ResourceLocation("the_nether"), DimensionalResourcePolicy.getRequiredDimensionId("cuarzo"));
    }
}
