package com.aicompanion;

import com.aicompanion.ai.mining.MiningTarget;
import com.aicompanion.ai.parser.TaskParser;
import com.aicompanion.ai.perception.RiskEvaluator;
import com.aicompanion.ai.planning.Goal;
import com.aicompanion.ai.tasks.MissionDropTracker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavior and regression test suite for Esclavo 1.13.26:
 * Validates null-safe threat evaluation, context-free hostile checks,
 * drop collection filtering, and mission recovery invariants.
 */
public class Companion11326BehaviorTest {

    @Test
    public void testRiskEvaluatorNullSafe() {
        // Must never throw NullPointerException even when companion or entity is null
        assertFalse(RiskEvaluator.isHostileThreat(null, null));
        assertFalse(RiskEvaluator.isInherentlyHostile(null));
    }

    @Test
    public void testMissionDropTrackerNullSafe() {
        assertFalse(MissionDropTracker.isPlayerDrop(null));
        assertFalse(MissionDropTracker.isWithinPickupReach(null, null));
        assertFalse(MissionDropTracker.hasLineOfSightToDrop(null, null));
    }

    @Test
    public void testComplexChainedMissionsWithCombatDefenseIntent() {
        List<Goal> goals = TaskParser.parse("mina hierro 5 y luego vuelve conmigo");
        assertEquals(2, goals.size());
        assertEquals(Goal.Type.OBTAIN, goals.get(0).getType());
        assertEquals(Goal.Type.RETURN_OWNER, goals.get(1).getType());
    }

    @Test
    public void testDropNameExclusionPreventsFriendlyEquipmentAbsorption() {
        Set<String> ironDrops = Set.of("raw_iron", "iron_ore");
        assertTrue(MiningTarget.isExactDropName(ironDrops, "raw_iron"));
        assertFalse(MiningTarget.isExactDropName(ironDrops, "iron_sword"));
        assertFalse(MiningTarget.isExactDropName(ironDrops, "diamond_chestplate"));
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
