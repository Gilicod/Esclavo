package com.aicompanion;

import com.aicompanion.ai.parser.SpanishAliases;
import com.aicompanion.ai.parser.TaskParser;
import com.aicompanion.ai.planning.Goal;
import com.aicompanion.ai.tasks.ReturnTask;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enterprise Autonomous Behavior Test Suite for Esclavo 1.13.43:
 * - Natural Language Parsing for Nether blocks with spaces ("piedra luminosa", "arena de almas", "piedra negra").
 * - Spanish NLP alias resolution for Nether resources (glowstone, soul sand, blackstone, basalt, etc.).
 * - ReturnTask delivery map serialization preventing duplicate transfers upon reload.
 */
public class Companion11343BehaviorTest {

    @Test
    public void testSpanishAliasesGlowstoneAndNether() {
        assertEquals(new ResourceLocation("minecraft", "glowstone"), SpanishAliases.resolveOre("piedra luminosa"));
        assertEquals(new ResourceLocation("minecraft", "glowstone"), SpanishAliases.resolveOre("glowstone"));
        assertEquals(new ResourceLocation("minecraft", "soul_sand"), SpanishAliases.resolveOre("arena de almas"));
        assertEquals(new ResourceLocation("minecraft", "blackstone"), SpanishAliases.resolveOre("piedra negra"));
        assertEquals(new ResourceLocation("minecraft", "basalt"), SpanishAliases.resolveOre("basalto"));
        assertEquals(new ResourceLocation("minecraft", "obsidian"), SpanishAliases.resolveOre("obsidiana"));
        assertEquals(new ResourceLocation("minecraft", "netherrack"), SpanishAliases.resolveOre("piedra del nether"));
    }

    @Test
    public void testParseGlowstoneAndSoulSandMissions() {
        List<Goal> glowGoals = TaskParser.parse("minar piedra luminosa 10 y traer a mi");
        assertNotNull(glowGoals);
        assertFalse(glowGoals.isEmpty());
        assertEquals(new ResourceLocation("minecraft", "glowstone"), glowGoals.get(0).getTargetResource());
        assertEquals(10, glowGoals.get(0).getAmount());

        List<Goal> soulGoals = TaskParser.parse("conseguir arena de almas 5");
        assertNotNull(soulGoals);
        assertFalse(soulGoals.isEmpty());
        assertEquals(new ResourceLocation("minecraft", "soul_sand"), soulGoals.get(0).getTargetResource());
        assertEquals(5, soulGoals.get(0).getAmount());

        List<Goal> blackstoneGoals = TaskParser.parse("minar piedra negra 64");
        assertNotNull(blackstoneGoals);
        assertFalse(blackstoneGoals.isEmpty());
        assertEquals(new ResourceLocation("minecraft", "blackstone"), blackstoneGoals.get(0).getTargetResource());
        assertEquals(64, blackstoneGoals.get(0).getAmount());
    }

    @Test
    public void testReturnTaskDeliveryMapSerialization() {
        ReturnTask task = new ReturnTask(null, false);
        task.setDelivery(List.of(new ResourceLocation("minecraft", "ancient_debris"), new ResourceLocation("minecraft", "quartz")));

        // Simulate save
        CompoundTag saved = task.save();
        assertNotNull(saved);
        assertTrue(saved.contains("DeliveryTargets"));
        assertTrue(saved.contains("DeliveredItemsMap"));

        // Put a simulated delivery entry into DeliveredItemsMap
        CompoundTag mapTag = new CompoundTag();
        mapTag.putInt("minecraft:ancient_debris", 2);
        saved.put("DeliveredItemsMap", mapTag);

        // Load into fresh task
        ReturnTask loadedTask = new ReturnTask(null, false);
        loadedTask.load(saved);

        assertEquals(2, loadedTask.getDeliveredCount(new ResourceLocation("minecraft", "ancient_debris")));
        assertEquals(0, loadedTask.getDeliveredCount(new ResourceLocation("minecraft", "quartz")));
    }
}
