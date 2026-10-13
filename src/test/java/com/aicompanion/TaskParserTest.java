package com.aicompanion;

import com.aicompanion.ai.parser.TaskParser;
import com.aicompanion.ai.planning.Goal;
import com.aicompanion.ai.skills.CompanionSkillRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TaskParserTest {

    @Test
    public void testSimpleMineOrder() {
        List<Goal> goals = TaskParser.parse("mina 20 diamantes");
        assertEquals(1, goals.size());
        assertEquals(Goal.Type.OBTAIN, goals.get(0).getType());
        assertEquals("minecraft:diamond_ore", goals.get(0).getTargetResource().toString());
        assertEquals(20, goals.get(0).getAmount());
    }

    @Test
    public void testSpanishGoldOrder() {
        List<Goal> goals = TaskParser.parse("Alex tráeme 40 de oro");
        assertEquals(1, goals.size());
        assertEquals(Goal.Type.OBTAIN, goals.get(0).getType());
        assertEquals("minecraft:gold_ore", goals.get(0).getTargetResource().toString());
        assertEquals(40, goals.get(0).getAmount());
    }

    @Test
    public void testKillOrder() {
        List<Goal> goals = TaskParser.parse("mata 10 creepers");
        assertEquals(1, goals.size());
        assertEquals(Goal.Type.KILL, goals.get(0).getType());
        assertEquals("minecraft:creeper", goals.get(0).getTargetResource().toString());
        assertEquals(10, goals.get(0).getAmount());
    }

    @Test
    public void testFollowAndStay() {
        List<Goal> follow = TaskParser.parse("sígueme");
        assertEquals(1, follow.size());
        assertEquals(Goal.Type.FOLLOW, follow.get(0).getType());

        List<Goal> stay = TaskParser.parse("quédate aquí");
        assertEquals(1, stay.size());
        assertEquals(Goal.Type.STAY, stay.get(0).getType());
    }

    @Test
    public void testCompoundOrder() {
        String compound = "Consígueme 40 de oro, después corta 64 de roble y después vuelve a casa";
        List<Goal> goals = TaskParser.parse(compound);
        assertEquals(3, goals.size());
        assertEquals(Goal.Type.OBTAIN, goals.get(0).getType());
        assertEquals("minecraft:gold_ore", goals.get(0).getTargetResource().toString());
        assertEquals(40, goals.get(0).getAmount());

        assertEquals(Goal.Type.OBTAIN, goals.get(1).getType());
        assertEquals("minecraft:oak_log", goals.get(1).getTargetResource().toString());
        assertEquals(64, goals.get(1).getAmount());

        assertEquals(Goal.Type.RETURN_HOME, goals.get(2).getType());
    }

    @Test
    public void testSkillRegistrySecurity() {
        assertTrue(CompanionSkillRegistry.isValidSkill("mine"));
        assertTrue(CompanionSkillRegistry.isValidSkill("chop"));
        assertTrue(CompanionSkillRegistry.isValidSkill("fight"));
        assertTrue(CompanionSkillRegistry.isValidSkill("build"));

        // Reject illegal / non-existent skills (cannot invent items or fly)
        assertFalse(CompanionSkillRegistry.isValidSkill("give"));
        assertFalse(CompanionSkillRegistry.isValidSkill("fly"));
        assertFalse(CompanionSkillRegistry.isValidSkill("teleport_to_diamonds"));
    }

    @Test
    public void testUserChainedVaultAndUniversalItems() {
        // User request: "quiero que busques 4 de oro guardes en el vaul tal... y despues busques madera"
        String order = "busques 4 de oro guardes en el vaul y despues busques madera";
        List<Goal> goals = TaskParser.parse(order);
        assertEquals(3, goals.size());

        // 1. 4 de oro
        assertEquals(Goal.Type.OBTAIN, goals.get(0).getType());
        assertEquals("minecraft:gold_ore", goals.get(0).getTargetResource().toString());
        assertEquals(4, goals.get(0).getAmount());

        // 2. Guardar en baúl / vaúl
        assertEquals(Goal.Type.DEPOSIT, goals.get(1).getType());

        // 3. Madera
        assertEquals(Goal.Type.OBTAIN, goals.get(2).getType());
        assertEquals("minecraft:oak_log", goals.get(2).getTargetResource().toString());
        assertEquals("any", goals.get(2).getStringParam());

        // Universal items: tinta
        List<Goal> inkGoal = TaskParser.parse("busca 6 de tinta");
        assertEquals(1, inkGoal.size());
        assertEquals("minecraft:ink_sac", inkGoal.get(0).getTargetResource().toString());
        assertEquals(6, inkGoal.get(0).getAmount());

        // Dynamic Suggestions check
        var suggs = com.aicompanion.ai.parser.ItemSearchRegistry.getSuggestions("tin", 5);
        assertFalse(suggs.isEmpty());
        assertTrue(suggs.stream().anyMatch(s -> s.insertText().contains("tinta")));
    }

    @Test
    public void testUserCaptureOneBugFix() {
        // User exact capture 1 order: "a y minar madera 2 y traer a mi"
        String order = "a y minar madera 2 y traer a mi";
        List<Goal> goals = TaskParser.parse(order);
        assertEquals(2, goals.size());

        // 1. Minar madera 2
        assertEquals(Goal.Type.OBTAIN, goals.get(0).getType());
        assertEquals("minecraft:oak_log", goals.get(0).getTargetResource().toString());
        assertEquals("any", goals.get(0).getStringParam());
        assertEquals(2, goals.get(0).getAmount());

        // 2. Traer a mí
        assertEquals(Goal.Type.RETURN_OWNER, goals.get(1).getType());
    }

    @Test
    public void testMiningGoldAndDeliveryVariants() {
        List<Goal> g1 = TaskParser.parse("mina oro 5 y traer a mi");
        assertEquals(2, g1.size());
        assertEquals(Goal.Type.OBTAIN, g1.get(0).getType());
        assertEquals("minecraft:gold_ore", g1.get(0).getTargetResource().toString());
        assertEquals(5, g1.get(0).getAmount());
        assertEquals(Goal.Type.RETURN_OWNER, g1.get(1).getType());

        List<Goal> g2 = TaskParser.parse("minar 16 diamantes y traer a mi");
        assertEquals(2, g2.size());
        assertEquals("minecraft:diamond_ore", g2.get(0).getTargetResource().toString());
        assertEquals(16, g2.get(0).getAmount());
        assertEquals(Goal.Type.RETURN_OWNER, g2.get(1).getType());

        List<Goal> g3 = TaskParser.parse("ve a minar 16 diamantes y luego traemelos");
        assertEquals(2, g3.size());
        assertEquals("minecraft:diamond_ore", g3.get(0).getTargetResource().toString());
        assertEquals(16, g3.get(0).getAmount());
        assertEquals(Goal.Type.RETURN_OWNER, g3.get(1).getType());

        List<Goal> g4 = TaskParser.parse("consigue 16 diamantes");
        assertEquals(1, g4.size());
        assertEquals("minecraft:diamond_ore", g4.get(0).getTargetResource().toString());
        assertEquals(16, g4.get(0).getAmount());
    }
}

