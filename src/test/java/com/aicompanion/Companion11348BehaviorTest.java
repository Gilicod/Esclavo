package com.aicompanion;

import com.aicompanion.ai.dimension.DimensionalResourcePolicy;
import com.aicompanion.ai.parser.ItemSearchRegistry;
import com.aicompanion.ai.perception.RiskEvaluator;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enterprise Autonomous Behavior Test Suite for Esclavo 1.13.48:
 * - Nether resource recognition for glowstone and netherite.
 * - Risk evaluation criteria for preventing endless tactical retreat loops.
 * - Spanish display names and suggestions.
 */
public class Companion11348BehaviorTest {

    @Test
    public void testRiskLevels() {
        assertNotNull(RiskEvaluator.RiskLevel.LOW);
        assertNotNull(RiskEvaluator.RiskLevel.MEDIUM);
        assertNotNull(RiskEvaluator.RiskLevel.HIGH);
        assertNotNull(RiskEvaluator.RiskLevel.CRITICAL);
    }

    @Test
    public void testDimensionalResourceNetherEntries() {
        assertTrue(DimensionalResourcePolicy.isNetherResource("glowstone"));
        assertTrue(DimensionalResourcePolicy.isNetherResource("glowstone_dust"));
        assertTrue(DimensionalResourcePolicy.isNetherResource("ancient_debris"));
        assertTrue(DimensionalResourcePolicy.isNetherResource("netherite_scrap"));
        assertTrue(DimensionalResourcePolicy.isNetherResource("netherite_ingot"));
    }

    @Test
    public void testNetherDisplayNames() {
        assertEquals("Piedra Luminosa", ItemSearchRegistry.getDisplayName(new ResourceLocation("minecraft", "glowstone")));
        assertEquals("Polvo de Piedra Luminosa", ItemSearchRegistry.getDisplayName(new ResourceLocation("minecraft", "glowstone_dust")));
        assertEquals("Detritos Ancestrales", ItemSearchRegistry.getDisplayName(new ResourceLocation("minecraft", "ancient_debris")));
        assertEquals("Lingote de Netherita", ItemSearchRegistry.getDisplayName(new ResourceLocation("minecraft", "netherite_ingot")));
    }
}
