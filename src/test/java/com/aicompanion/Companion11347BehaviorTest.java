package com.aicompanion;

import com.aicompanion.ai.parser.ItemSearchRegistry;
import com.aicompanion.ai.parser.TaskParser;
import com.aicompanion.ai.planning.Goal;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enterprise Autonomous Behavior Test Suite for Esclavo 1.13.47:
 * - GUI display name vs technical canonical ID separation in SmartSuggestion.
 * - Human-readable Spanish name insertions (no technical raw registry IDs in order input).
 * - Exact disambiguation between Glowstone block ("Piedra luminosa") and dust ("Polvo de piedra luminosa").
 * - Parser accuracy for mineral orders using clean Spanish names.
 */
public class Companion11347BehaviorTest {

    @Test
    public void testSmartSuggestionStructureAndValues() {
        ItemSearchRegistry.SmartSuggestion sugg = new ItemSearchRegistry.SmartSuggestion(
                "Piedra luminosa",
                "Piedra luminosa",
                "minecraft:glowstone",
                "Bloque"
        );

        assertEquals("Piedra luminosa", sugg.label());
        assertEquals("Piedra luminosa", sugg.insertText());
        assertEquals("minecraft:glowstone", sugg.canonicalId());
        assertEquals("Bloque", sugg.category());
        // Backwards compatibility value() must match insertText (clean Spanish name)
        assertEquals(sugg.insertText(), sugg.value());
        assertFalse(sugg.insertText().contains("minecraft:"), "Insert text must not contain technical namespace prefixes!");
    }

    @Test
    public void testSpanishDictionaryEntriesForNetherAndGlowstone() {
        String glowstone = ItemSearchRegistry.getDisplayName(new ResourceLocation("minecraft", "glowstone"));
        assertEquals("Piedra Luminosa", glowstone);

        String glowstoneDust = ItemSearchRegistry.getDisplayName(new ResourceLocation("minecraft", "glowstone_dust"));
        assertEquals("Polvo de Piedra Luminosa", glowstoneDust);

        String ancientDebris = ItemSearchRegistry.getDisplayName(new ResourceLocation("minecraft", "ancient_debris"));
        assertEquals("Detritos Ancestrales", ancientDebris);

        String netheriteIngot = ItemSearchRegistry.getDisplayName(new ResourceLocation("minecraft", "netherite_ingot"));
        assertEquals("Lingote de Netherita", netheriteIngot);

        String soulSand = ItemSearchRegistry.getDisplayName(new ResourceLocation("minecraft", "soul_sand"));
        assertEquals("Arena de Almas", soulSand);
    }

    @Test
    public void testTaskParserResolvesGlowstoneBlockVsDust() {
        // Test parsing block order
        List<Goal> blockGoals = TaskParser.parse("mina 4 piedra luminosa");
        assertFalse(blockGoals.isEmpty());
        Goal blockGoal = blockGoals.get(0);
        assertEquals(Goal.Type.OBTAIN, blockGoal.getType());
        assertEquals("minecraft:glowstone", blockGoal.getTargetResource().toString());
        assertEquals(4, blockGoal.getAmount());

        // Test parsing dust order
        List<Goal> dustGoals = TaskParser.parse("mina 8 polvo de piedra luminosa");
        assertFalse(dustGoals.isEmpty());
        Goal dustGoal = dustGoals.get(0);
        assertEquals(Goal.Type.OBTAIN, dustGoal.getType());
        assertEquals("minecraft:glowstone_dust", dustGoal.getTargetResource().toString());
        assertEquals(8, dustGoal.getAmount());
    }

    @Test
    public void testSuggestionsGenerateCleanSpanishNames() {
        List<ItemSearchRegistry.SmartSuggestion> suggestions = ItemSearchRegistry.getSmartSuggestions("piedra", 10);
        assertFalse(suggestions.isEmpty(), "Suggestions for 'piedra' should not be empty");

        for (ItemSearchRegistry.SmartSuggestion s : suggestions) {
            assertNotNull(s.insertText());
            assertNotNull(s.canonicalId());
            assertFalse(s.insertText().contains("minecraft:"), "insertText must never contain raw namespace: " + s.insertText());
        }
    }
}
