package com.aicompanion;

import com.aicompanion.ai.nav.NavigationAction;
import com.aicompanion.internal.baritone.goals.*;
import com.aicompanion.internal.baritone.movements.MovementType;
import com.aicompanion.internal.baritone.path.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

public class InternalBaritoneTest {

    @Test
    public void testGoalBlock() {
        GoalBlock goal = new GoalBlock(10, 64, 20);
        assertTrue(goal.isInGoal(10, 64, 20));
        assertFalse(goal.isInGoal(10, 65, 20));
        assertEquals(0.0, goal.heuristic(10, 64, 20), 0.001);
        assertEquals(1.0, goal.heuristic(10, 64, 21), 0.001);
    }

    @Test
    public void testGoalNear() {
        GoalNear goal = new GoalNear(0, 64, 0, 3.0);
        assertTrue(goal.isInGoal(0, 64, 0));
        assertTrue(goal.isInGoal(2, 64, 1));
        assertFalse(goal.isInGoal(5, 64, 5));
        assertEquals(0.0, goal.heuristic(1, 64, 1), 0.001);
        assertTrue(goal.heuristic(10, 64, 0) > 0.0);
    }

    @Test
    public void testGoalXZ() {
        GoalXZ goal = new GoalXZ(50, -50);
        assertTrue(goal.isInGoal(50, 10, -50));
        assertTrue(goal.isInGoal(50, 100, -50));
        assertFalse(goal.isInGoal(51, 64, -50));
    }

    @Test
    public void testGoalInteractBlock() {
        BlockPos block = new BlockPos(10, 64, 10);
        GoalInteractBlock goal = new GoalInteractBlock(block, 3.0);

        // Can't be inside the block itself
        assertFalse(goal.isInGoal(10, 64, 10));

        // Standing adjacent to the block
        assertTrue(goal.isInGoal(9, 64, 10));
        assertTrue(goal.isInGoal(11, 64, 10));

        // Too far away (> 3.0 blocks)
        assertFalse(goal.isInGoal(15, 64, 10));
    }

    @Test
    public void testPathAndAdapter() {
        PathNode n0 = new PathNode(0, 64, 0, null, MovementType.TRAVERSE, null, 0.0, 3.0);
        PathNode n1 = new PathNode(1, 64, 0, n0, MovementType.TRAVERSE, null, 1.0, 2.0);
        PathNode n2 = new PathNode(2, 65, 0, n1, MovementType.ASCEND, null, 2.4, 1.0);
        PathNode n3 = new PathNode(3, 65, 0, n2, MovementType.BRIDGE, new BlockPos(3, 64, 0), 4.9, 0.0);

        Path path = Path.fromEndNode(n3);
        assertEquals(4, path.length());
        assertEquals(0, path.getNode(0).x);
        assertEquals(3, path.getNode(3).x);

        Queue<NavigationAction> actions = BaritonePathAdapter.adaptToActions(path, 1.2);
        assertNotNull(actions);
        assertFalse(actions.isEmpty());

        // Check that BRIDGE generated PLACE_BLOCK action
        boolean hasPlace = actions.stream().anyMatch(a -> a.getType() == NavigationAction.Type.PLACE_BLOCK);
        assertTrue(hasPlace, "Path adapter should have generated a PLACE_BLOCK action for BRIDGE movement");

        // Check that ASCEND generated JUMP action
        boolean hasJump = actions.stream().anyMatch(a -> a.getType() == NavigationAction.Type.JUMP);
        assertTrue(hasJump, "Path adapter should have generated a JUMP action for ASCEND movement");
    }
}
