package com.aicompanion;

import com.aicompanion.ai.nav.ScaffoldBlockHelper;
import com.aicompanion.inventory.EquipmentManager;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ScaffoldAndToolSafety11359Test {

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void testAncientDebrisAndGlassForbiddenAsScaffolding() {
        ItemStack ancientDebris = new ItemStack(Items.ANCIENT_DEBRIS);
        ItemStack glass = new ItemStack(Items.GLASS);
        ItemStack stainedGlass = new ItemStack(Items.RED_STAINED_GLASS);
        ItemStack tintedGlass = new ItemStack(Items.TINTED_GLASS);
        ItemStack netheriteBlock = new ItemStack(Items.NETHERITE_BLOCK);
        ItemStack diamondBlock = new ItemStack(Items.DIAMOND_BLOCK);
        ItemStack chest = new ItemStack(Items.CHEST);
        ItemStack furnace = new ItemStack(Items.FURNACE);

        assertTrue(ScaffoldBlockHelper.isForbiddenScaffold(ancientDebris, null), "Ancient Debris must NEVER be used as scaffolding!");
        assertTrue(ScaffoldBlockHelper.isForbiddenScaffold(glass, null), "Glass must NEVER be used as scaffolding!");
        assertTrue(ScaffoldBlockHelper.isForbiddenScaffold(stainedGlass, null), "Stained Glass must NEVER be used as scaffolding!");
        assertTrue(ScaffoldBlockHelper.isForbiddenScaffold(tintedGlass, null), "Tinted Glass must NEVER be used as scaffolding!");
        assertTrue(ScaffoldBlockHelper.isForbiddenScaffold(netheriteBlock, null), "Netherite Block must NEVER be used as scaffolding!");
        assertTrue(ScaffoldBlockHelper.isForbiddenScaffold(diamondBlock, null), "Diamond Block must NEVER be used as scaffolding!");
        assertTrue(ScaffoldBlockHelper.isForbiddenScaffold(chest, null), "Chests must NEVER be used as scaffolding!");
        assertTrue(ScaffoldBlockHelper.isForbiddenScaffold(furnace, null), "Furnaces must NEVER be used as scaffolding!");

        assertEquals(0, ScaffoldBlockHelper.getScaffoldPriority(ancientDebris), "Ancient debris priority must be 0");
        assertEquals(0, ScaffoldBlockHelper.getScaffoldPriority(glass), "Glass priority must be 0");
        assertEquals(0, ScaffoldBlockHelper.getScaffoldPriority(stainedGlass), "Stained glass priority must be 0");
    }

    @Test
    public void testSafeScaffoldBlocksAllowedWithCorrectPriority() {
        ItemStack scaffolding = new ItemStack(Items.SCAFFOLDING);
        ItemStack dirt = new ItemStack(Items.DIRT);
        ItemStack grass = new ItemStack(Items.GRASS_BLOCK);
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);
        ItemStack cobbledDeepslate = new ItemStack(Items.COBBLED_DEEPSLATE);
        ItemStack stone = new ItemStack(Items.STONE);
        ItemStack andesite = new ItemStack(Items.ANDESITE);

        assertFalse(ScaffoldBlockHelper.isForbiddenScaffold(scaffolding, null), "Scaffolding item must be allowed!");
        assertFalse(ScaffoldBlockHelper.isForbiddenScaffold(dirt, null), "Dirt must be allowed as scaffolding!");
        assertFalse(ScaffoldBlockHelper.isForbiddenScaffold(grass, null), "Grass block must be allowed as scaffolding!");
        assertFalse(ScaffoldBlockHelper.isForbiddenScaffold(cobble, null), "Cobblestone must be allowed as scaffolding!");
        assertFalse(ScaffoldBlockHelper.isForbiddenScaffold(cobbledDeepslate, null), "Cobbled deepslate must be allowed as scaffolding!");
        assertFalse(ScaffoldBlockHelper.isForbiddenScaffold(stone, null), "Stone must be allowed as scaffolding!");
        assertFalse(ScaffoldBlockHelper.isForbiddenScaffold(andesite, null), "Andesite must be allowed as scaffolding!");

        assertEquals(150, ScaffoldBlockHelper.getScaffoldPriority(scaffolding));
        assertEquals(100, ScaffoldBlockHelper.getScaffoldPriority(dirt));
        assertEquals(100, ScaffoldBlockHelper.getScaffoldPriority(grass));
        assertEquals(80, ScaffoldBlockHelper.getScaffoldPriority(cobble));
        assertEquals(80, ScaffoldBlockHelper.getScaffoldPriority(cobbledDeepslate));
        assertEquals(70, ScaffoldBlockHelper.getScaffoldPriority(stone));
        assertEquals(70, ScaffoldBlockHelper.getScaffoldPriority(andesite));
    }

    @Test
    public void testIsPreciousOrFragileBlock() {
        assertTrue(ScaffoldBlockHelper.isPreciousOrFragileBlock(Blocks.ANCIENT_DEBRIS, new ItemStack(Items.ANCIENT_DEBRIS)));
        assertTrue(ScaffoldBlockHelper.isPreciousOrFragileBlock(Blocks.GLASS, new ItemStack(Items.GLASS)));
        assertTrue(ScaffoldBlockHelper.isPreciousOrFragileBlock(Blocks.TINTED_GLASS, new ItemStack(Items.TINTED_GLASS)));
        assertTrue(ScaffoldBlockHelper.isPreciousOrFragileBlock(Blocks.NETHERITE_BLOCK, new ItemStack(Items.NETHERITE_BLOCK)));
        assertTrue(ScaffoldBlockHelper.isPreciousOrFragileBlock(Blocks.DIAMOND_ORE, new ItemStack(Items.DIAMOND_ORE)));
        assertTrue(ScaffoldBlockHelper.isPreciousOrFragileBlock(Blocks.CHEST, new ItemStack(Items.CHEST)));

        assertFalse(ScaffoldBlockHelper.isPreciousOrFragileBlock(Blocks.DIRT, new ItemStack(Items.DIRT)));
        assertFalse(ScaffoldBlockHelper.isPreciousOrFragileBlock(Blocks.COBBLESTONE, new ItemStack(Items.COBBLESTONE)));
        assertFalse(ScaffoldBlockHelper.isPreciousOrFragileBlock(Blocks.STONE, new ItemStack(Items.STONE)));
    }

    @Test
    public void testSwordClassification() {
        ItemStack diamondSword = new ItemStack(Items.DIAMOND_SWORD);
        ItemStack ironSword = new ItemStack(Items.IRON_SWORD);
        ItemStack stoneSword = new ItemStack(Items.STONE_SWORD);
        ItemStack diamondPick = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack diamondAxe = new ItemStack(Items.DIAMOND_AXE);

        assertTrue(EquipmentManager.isSword(diamondSword));
        assertTrue(EquipmentManager.isSword(ironSword));
        assertTrue(EquipmentManager.isSword(stoneSword));
        assertFalse(EquipmentManager.isSword(diamondPick));
        assertFalse(EquipmentManager.isSword(diamondAxe));
    }
}
