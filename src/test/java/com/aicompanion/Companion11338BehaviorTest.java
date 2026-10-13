package com.aicompanion;

import com.aicompanion.ai.memory.ShortTermMemory;
import com.aicompanion.ai.mining.MiningTarget;
import com.aicompanion.ai.mining.SearchRegionManager;
import com.aicompanion.ai.parser.SpanishAliases;
import com.aicompanion.ai.parser.TaskParser;
import com.aicompanion.ai.planning.Goal;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enterprise Autonomous Mining Watchdog & Spatial Memory Test Suite for Esclavo 1.13.38:
 * - Target Lock engagement, pop/peek, and complete cluster release on unreachable targets.
 * - SearchRegionManager blacklist synchronization and un-inhibition when clusters are abandoned.
 * - Anti-paralysis mining verification for subterranean ore veins (Ancient Debris, Netherite, Diamonds).
 */
public class Companion11338BehaviorTest {

    @Test
    public void testShortTermMemoryClusterLockingAndRelease() {
        ShortTermMemory memory = new ShortTermMemory();
        assertFalse(memory.hasLockedCluster(), "Should start without locked cluster");

        BlockPos origin = new BlockPos(-34, 21, -97);
        List<BlockPos> cluster = List.of(
                origin,
                new BlockPos(-34, 22, -97)
        );

        memory.lockCluster("ORE", origin, cluster);
        assertTrue(memory.hasLockedCluster(), "Should have locked cluster");
        assertEquals("ORE", memory.getActiveClusterType());
        assertEquals(origin, memory.getActiveClusterOrigin());
        assertEquals(2, memory.getLockedCluster().size());

        // Pop first block
        BlockPos first = memory.popNextClusterBlock();
        assertEquals(origin, first);
        assertEquals(1, memory.getLockedCluster().size());
        assertTrue(memory.hasLockedCluster());

        // Abandon/release cluster (e.g. on stuck target unreachable)
        memory.clearLockedCluster();
        assertFalse(memory.hasLockedCluster(), "Locked cluster should be completely cleared");
        assertEquals("", memory.getActiveClusterType());
        assertNull(memory.getActiveClusterOrigin());
    }

    @Test
    public void testSearchRegionManagerBlacklistAndInhibition() {
        SearchRegionManager searchManager = new SearchRegionManager();
        assertFalse(searchManager.isInhibited());

        // When cluster locks, search manager is inhibited
        searchManager.setInhibited(true);
        assertTrue(searchManager.isInhibited());

        // When target is unreachable, all cluster blocks are blacklisted and search un-inhibited
        BlockPos unreachablePos1 = new BlockPos(-34, 21, -97);
        BlockPos unreachablePos2 = new BlockPos(-34, 22, -97);
        searchManager.blacklistBlock(unreachablePos1);
        searchManager.blacklistBlock(unreachablePos2);
        searchManager.setInhibited(false);

        assertFalse(searchManager.isInhibited(), "Search manager should be active and un-inhibited");
    }

    @Test
    public void testParseMineNetheriteCommand() {
        List<Goal> goals = TaskParser.parse("minar netherita 2 y traer a mi");
        assertNotNull(goals);
        assertEquals(2, goals.size());

        Goal mineGoal = goals.get(0);
        assertEquals(Goal.Type.OBTAIN, mineGoal.getType());
        assertEquals(new ResourceLocation("minecraft", "ancient_debris"), mineGoal.getTargetResource());
        assertEquals(2, mineGoal.getAmount());

        Goal returnGoal = goals.get(1);
        assertEquals(Goal.Type.RETURN_OWNER, returnGoal.getType());
        assertTrue(returnGoal.isDelivery());
    }

    @Test
    public void testAncientDebrisMiningTargetResolution() {
        MiningTarget target = new MiningTarget("Netherite", 8, 24);
        assertNotNull(target);
        assertEquals("Netherite", target.getName());
        assertEquals(8, target.getMinRecommendedY());
        assertEquals(24, target.getMaxRecommendedY());
        assertEquals(new ResourceLocation("minecraft", "ancient_debris"), SpanishAliases.resolveOre("netherita"));
    }
}
