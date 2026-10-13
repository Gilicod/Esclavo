package com.aicompanion;

import com.aicompanion.internal.baritone.goals.GoalInteractBlock;
import com.aicompanion.internal.baritone.goals.GoalNear;
import com.aicompanion.internal.baritone.goals.GoalExploreRegion;
import com.aicompanion.internal.baritone.goals.GoalXZ;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enterprise Autonomous Mining Watchdog, Wall Approach & Goal Navigation Test Suite for Esclavo 1.13.39:
 * - GoalInteractBlock target block resolution and reach boundary.
 * - GoalNear reach boundary when approaching cave wall obstruction.
 * - GoalExploreRegion and GoalXZ coordinates resolution.
 */
public class Companion11339BehaviorTest {

    @Test
    public void testGoalInteractBlockResolution() {
        BlockPos target = new BlockPos(-34, 21, -97);
        GoalInteractBlock goal = new GoalInteractBlock(target, 3.2);

        assertEquals(target, goal.getTargetBlock(), "Target block must match target");
        assertEquals(3.2, goal.getMaxReach(), 0.001, "Max reach must be preserved");

        // Standing right at reach
        assertTrue(goal.isInGoal(-34, 21, -95), "Within 2 blocks should be in goal");
        // Cannot be inside target itself
        assertFalse(goal.isInGoal(-34, 21, -97), "Inside target block cannot be in goal");
        // Far away
        assertFalse(goal.isInGoal(-32, 17, -85), "12m away must not be in goal");
    }

    @Test
    public void testGoalNearForWallObstructionApproach() {
        BlockPos wallPos = new BlockPos(-33, 18, -87);
        GoalNear goal = new GoalNear(wallPos, 2);

        assertEquals(wallPos, goal.getCenter(), "Center must match wall position");
        // Near wall
        assertTrue(goal.isInGoal(-33, 18, -86), "Within 1 block is in goal");
        // Far away
        assertFalse(goal.isInGoal(-32, 17, -80), "7 blocks away is not in goal");
    }

    @Test
    public void testGoalXZResolution() {
        GoalXZ goal = new GoalXZ(100, -200);
        assertEquals(100, goal.getX());
        assertEquals(-200, goal.getZ());
        assertTrue(goal.isInGoal(100, 64, -200));
        assertFalse(goal.isInGoal(105, 64, -200));
    }

    @Test
    public void testGoalExploreRegion() {
        GoalExploreRegion goal = new GoalExploreRegion(0, 0, 32.0);
        // Inside radius is NOT yet in goal (goal is to reach the outer exploration zone)
        assertFalse(goal.isInGoal(0, 64, 0));
        assertTrue(goal.isInGoal(40, 64, 0));
    }
}
