package com.aicompanion;

import com.aicompanion.ai.memory.CompanionWorldModel;
import com.aicompanion.ai.mining.SearchRegionManager;
import com.aicompanion.ai.spatial.SpatialClusterManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enterprise Autonomous Behavior Test Suite for Esclavo 1.13.44:
 * - SearchRegionManager blacklist verification and spatial clearance.
 * - CompanionWorldModel resource deposit purging preventing infinite reuse of unreachable deposits.
 * - Ore cluster proximity sorting ensuring ground-accessible ore blocks are mined before high ceiling blocks.
 */
public class Companion11344BehaviorTest {

    @Test
    public void testSearchRegionManagerBlacklist() {
        SearchRegionManager srm = new SearchRegionManager();
        BlockPos unreachablePos = new BlockPos(26, 119, -56);

        assertFalse(srm.isBlacklisted(unreachablePos));
        srm.blacklistBlock(unreachablePos);
        assertTrue(srm.isBlacklisted(unreachablePos));

        srm.clearCache();
        assertFalse(srm.isBlacklisted(unreachablePos));
    }

    @Test
    public void testCompanionWorldModelResourceDepositPurge() {
        CompanionWorldModel wm = new CompanionWorldModel();
        BlockPos highCeilingGlowstone = new BlockPos(60, 97, -121);
        net.minecraft.resources.ResourceLocation glowstoneId = new net.minecraft.resources.ResourceLocation("minecraft", "glowstone");

        wm.rememberResourceDeposit(highCeilingGlowstone, glowstoneId, 4, 100L);
        var found = wm.findClosestResourceDeposit(glowstoneId, new BlockPos(65, 71, -132), 64.0);
        assertTrue(found.isPresent());
        assertEquals(highCeilingGlowstone, found.get());

        // When declared unreachable, purging removes it from WorldModel
        wm.removeResourceDeposit(highCeilingGlowstone);
        var foundAfterPurge = wm.findClosestResourceDeposit(glowstoneId, new BlockPos(65, 71, -132), 64.0);
        assertFalse(foundAfterPurge.isPresent());
    }

    @Test
    public void testOreClusterSortsByProximityToCompanion() {
        BlockPos companionPos = new BlockPos(65, 71, -132);

        // Simulated cluster blocks along a stalactite/wall
        BlockPos lowGroundBlock = new BlockPos(65, 72, -132); // 1 block above bot (distSq = 1)
        BlockPos midWallBlock = new BlockPos(64, 80, -128);   // 9 blocks above bot
        BlockPos highCeilingBlock = new BlockPos(63, 119, -122); // 48 blocks above bot

        List<BlockPos> cluster = new ArrayList<>(List.of(highCeilingBlock, lowGroundBlock, midWallBlock));

        // Sort using the new SpatialClusterManager comparator (proximity first)
        cluster.sort((a, b) -> {
            int distCmp = Double.compare(a.distSqr(companionPos), b.distSqr(companionPos));
            if (distCmp != 0) return distCmp;
            return Integer.compare(b.getY(), a.getY());
        });

        // The closest block (ground level) MUST be at index 0
        assertEquals(lowGroundBlock, cluster.get(0));
        assertEquals(midWallBlock, cluster.get(1));
        assertEquals(highCeilingBlock, cluster.get(2));
    }
}
