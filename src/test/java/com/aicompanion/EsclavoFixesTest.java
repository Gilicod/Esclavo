package com.aicompanion;

import com.aicompanion.ai.mining.MiningTarget;
import com.aicompanion.ai.nav.MovementPolicy;
import com.aicompanion.ai.parser.TaskParser;
import com.aicompanion.ai.planning.Goal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the 1.10.0 fixes that can run without a Minecraft bootstrap:
 * delivery intent parsing, exact drop matching and natural movement speed clamping.
 */
public class EsclavoFixesTest {

    @Test
    public void deliveryVerbsCreateDeliveryGoal() {
        List<Goal> goals = TaskParser.parse("ve a minar 16 diamantes y luego traemelos");
        assertEquals(2, goals.size());
        assertEquals(Goal.Type.RETURN_OWNER, goals.get(1).getType());
        assertTrue(goals.get(1).isDelivery(), "traemelos must request an item delivery");

        List<Goal> g2 = TaskParser.parse("mina oro 5 y traer a mi");
        assertTrue(g2.get(1).isDelivery());
    }

    @Test
    public void plainReturnIsNotDelivery() {
        List<Goal> goals = TaskParser.parse("ven aqui");
        assertEquals(1, goals.size());
        assertEquals(Goal.Type.RETURN_OWNER, goals.get(0).getType());
        assertFalse(goals.get(0).isDelivery(), "ven aqui must only walk back, not hand items over");

        List<Goal> g2 = TaskParser.parse("regresa");
        assertEquals(1, g2.size());
        assertFalse(g2.get(0).isDelivery());
    }

    @Test
    public void depositAndKeepAreNotDelivery() {
        List<Goal> deposit = TaskParser.parse("mina hierro 10 y guardalos en un cofre");
        assertEquals(Goal.Type.DEPOSIT, deposit.get(deposit.size() - 1).getType());

        List<Goal> keep = TaskParser.parse("mina hierro 10 y conservalos");
        assertEquals(Goal.Type.STAY, keep.get(keep.size() - 1).getType());
        for (Goal g : keep) assertFalse(g.isDelivery());
    }

    @Test
    public void exactDropMatchingRejectsToolsAndSubstrings() {
        Set<String> stoneDrops = Set.of("cobblestone", "cobbled_deepslate", "stone", "deepslate");
        assertTrue(MiningTarget.isExactDropName(stoneDrops, "cobblestone"));
        assertFalse(MiningTarget.isExactDropName(stoneDrops, "stone_pickaxe"));
        assertFalse(MiningTarget.isExactDropName(stoneDrops, "redstone"));

        Set<String> diamondDrops = Set.of("diamond");
        assertTrue(MiningTarget.isExactDropName(diamondDrops, "diamond"));
        assertFalse(MiningTarget.isExactDropName(diamondDrops, "diamond_pickaxe"));
    }

    @Test
    public void legacyFastSpeedsAreClampedToNaturalRange() {
        double walk = MovementPolicy.walk();
        double max = MovementPolicy.max();
        assertTrue(max >= walk);
        assertEquals(walk, MovementPolicy.clamp(1.0), 1e-9);
        assertTrue(MovementPolicy.clamp(1.35) <= max + 1e-9);
        assertTrue(MovementPolicy.clamp(5.0) <= max + 1e-9, "absurd speeds must never pass the cap");
        assertTrue(MovementPolicy.clamp(Double.NaN) > 0);
        assertTrue(MovementPolicy.sprintingAllowed(), "sprinting is enabled by default");
    }
}
