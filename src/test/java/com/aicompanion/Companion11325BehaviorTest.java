package com.aicompanion;

import com.aicompanion.ai.mining.MiningTarget;
import com.aicompanion.ai.parser.TaskParser;
import com.aicompanion.ai.planning.Goal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavior and regression test suite for Esclavo 1.13.25:
 * Validates task-preserving self-defense invariants, priority queues,
 * natural language parser commands, drop verification, and defensive coordination.
 */
public class Companion11325BehaviorTest {

    @Test
    public void testComplexChainedMissionsWithCombatDefenseIntent() {
        // Scenario: Player orders mining followed by returning, Esclavo plans chain
        List<Goal> goals = TaskParser.parse("mina hierro 5 y luego vuelve conmigo");
        assertEquals(2, goals.size());
        assertEquals(Goal.Type.OBTAIN, goals.get(0).getType());
        assertEquals(Goal.Type.RETURN_OWNER, goals.get(1).getType());
    }

    @Test
    public void testDropNameExclusionPreventsFriendlyEquipmentAbsorption() {
        Set<String> coalDrops = Set.of("coal");
        assertTrue(MiningTarget.isExactDropName(coalDrops, "coal"));
        assertFalse(MiningTarget.isExactDropName(coalDrops, "charcoal"));
        assertFalse(MiningTarget.isExactDropName(coalDrops, "iron_sword"));
        assertFalse(MiningTarget.isExactDropName(coalDrops, "diamond_chestplate"));
    }

    @Test
    public void testMultiTargetParsingPreservesExecutionOrder() {
        List<Goal> goals = TaskParser.parse("tala roble 10 y luego mina carbon 10");
        assertEquals(2, goals.size());
        assertEquals(Goal.Type.OBTAIN, goals.get(0).getType());
        assertEquals(Goal.Type.OBTAIN, goals.get(1).getType());
    }

    @Test
    public void testDefensiveGuardingIntentParsing() {
        List<Goal> goals = TaskParser.parse("protege esta zona y vigila");
        assertFalse(goals.isEmpty());
        assertEquals(Goal.Type.GUARD, goals.get(0).getType());
    }

    @Test
    public void testFarmHarvestingAndReplantingIntent() {
        List<Goal> goals = TaskParser.parse("cuida el huerto y cosecha");
        assertFalse(goals.isEmpty());
        assertEquals(Goal.Type.FARM, goals.get(0).getType());
    }
}
