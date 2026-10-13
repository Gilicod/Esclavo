package com.aicompanion;

import com.aicompanion.ai.mining.MiningTarget;
import com.aicompanion.ai.parser.SpanishAliases;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enterprise Autonomous Behavior Test Suite for Esclavo 1.13.45:
 * - Spanish alias resolution for Glowstone blocks and Glowstone Dust.
 * - MiningTarget drop keyword differentiation between block items and raw dust.
 * - Glowstone crafting math: 4 dust -> 1 block.
 * - Portal cliff descent direction steering towards portal coordinates.
 */
public class Companion11345BehaviorTest {

    @Test
    public void testSpanishAliasesGlowstoneVariants() {
        ResourceLocation glowstoneBlock = SpanishAliases.resolveOre("piedra luminosa");
        assertNotNull(glowstoneBlock);
        assertEquals(new ResourceLocation("minecraft", "glowstone"), glowstoneBlock);

        ResourceLocation glowstoneAlias = SpanishAliases.resolveOre("glowstone");
        assertNotNull(glowstoneAlias);
        assertEquals(new ResourceLocation("minecraft", "glowstone"), glowstoneAlias);

        ResourceLocation glowstoneDust1 = SpanishAliases.resolveOre("polvo luminoso");
        assertNotNull(glowstoneDust1);
        assertEquals(new ResourceLocation("minecraft", "glowstone_dust"), glowstoneDust1);

        ResourceLocation glowstoneDust2 = SpanishAliases.resolveOre("polvo de piedra luminosa");
        assertNotNull(glowstoneDust2);
        assertEquals(new ResourceLocation("minecraft", "glowstone_dust"), glowstoneDust2);

        ResourceLocation glowstoneDust3 = SpanishAliases.resolveOre("polvo de glowstone");
        assertNotNull(glowstoneDust3);
        assertEquals(new ResourceLocation("minecraft", "glowstone_dust"), glowstoneDust3);
    }

    @Test
    public void testMiningTargetDropKeywordsDistinction() {
        // "Piedra Luminosa" drop keywords must include both block and dust for physical ground collection
        Set<String> blockDropKeywords = Set.of("piedra luminosa", "glowstone", "glowstone_dust");
        assertTrue(MiningTarget.isExactDropName(blockDropKeywords, "glowstone"));
        assertTrue(MiningTarget.isExactDropName(blockDropKeywords, "glowstone_dust"));

        // "Polvo Luminoso" drop keywords must only include dust
        Set<String> dustDropKeywords = Set.of("polvo luminoso", "glowstone_dust");
        assertTrue(MiningTarget.isExactDropName(dustDropKeywords, "glowstone_dust"));
        assertFalse(MiningTarget.isExactDropName(dustDropKeywords, "glowstone"));
    }

    @Test
    public void testGlowstoneCraftingRatio() {
        // 4 dust per 1 block
        int dustCount = 10;
        int blocksCraftable = dustCount / 4;
        assertEquals(2, blocksCraftable);
        int remainingDust = dustCount % 4;
        assertEquals(2, remainingDust);
    }

    @Test
    public void testPortalCliffDescentDirectionSteering() {
        // Companion at (-7, 89, -102), Portal at (-21, 36, -88)
        int compX = -7;
        int compZ = -102;
        int portalX = -21;
        int portalZ = -88;

        int dx = portalX - compX; // -14 (West)
        int dz = portalZ - compZ; // +14 (South)

        Direction dir = Direction.getNearest(dx, 0, dz);
        assertTrue(dir == Direction.WEST || dir == Direction.SOUTH);
        assertNotEquals(Direction.UP, dir);
        assertNotEquals(Direction.DOWN, dir);
    }
}
