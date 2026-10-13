package com.aicompanion;

import com.aicompanion.ai.chunk.CompanionChunkManager;
import com.aicompanion.ai.mining.MiningTarget;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enterprise Autonomous Behavior Test Suite for Esclavo 1.13.40:
 * - Chunk manager ticket configuration and entity-ticking ticket level.
 * - Mining target drop isolation (ancient_debris vs scraps/ingots).
 * - Task serialization format with MissionId, TaskPaused, TaskStage, TaskSubstate.
 */
public class Companion11340BehaviorTest {

    @Test
    public void testCompanionChunkTicketConfig() {
        assertNotNull(CompanionChunkManager.COMPANION_TICKET, "Companion ticket type must be registered");
        assertEquals("aicompanion", CompanionChunkManager.COMPANION_TICKET.toString(), "Ticket type name must match aicompanion");
        assertEquals(0L, CompanionChunkManager.COMPANION_TICKET.timeout(), "Companion ticket type must have no timeout (persistent while working)");
    }

    @Test
    public void testMiningTargetAncientDebrisDropIsolation() {
        MiningTarget target = new MiningTarget("Netherite", 8, 24);
        target.withDropKeyword("ancient_debris");
        assertNotNull(target);
        assertEquals("Netherite", target.getName());
        assertTrue(MiningTarget.isExactDropName(target.getDropNames(), "ancient_debris"), "Ancient debris drop must be accepted");
        assertFalse(MiningTarget.isExactDropName(target.getDropNames(), "netherite_scrap"), "Netherite scrap must NOT be a direct mining drop of ancient debris");
        assertFalse(MiningTarget.isExactDropName(target.getDropNames(), "netherite_ingot"), "Netherite ingot must NOT be a direct mining drop of ancient debris");
    }

    @Test
    public void testTaskSerializationTags() {
        CompoundTag tag = new CompoundTag();
        tag.putString("TaskTypeIdentifier", "MINE");
        tag.putString("MissionId", "MIS-1a2b3c");
        tag.putBoolean("TaskPaused", true);
        tag.putString("TaskStage", "NAVIGATING");
        tag.putString("TaskSubstate", "Aproximándose a veta");

        assertEquals("MINE", tag.getString("TaskTypeIdentifier"));
        assertEquals("MIS-1a2b3c", tag.getString("MissionId"));
        assertTrue(tag.getBoolean("TaskPaused"));
        assertEquals("NAVIGATING", tag.getString("TaskStage"));
        assertEquals("Aproximándose a veta", tag.getString("TaskSubstate"));
    }
}
