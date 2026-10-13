package com.aicompanion;

import com.aicompanion.ai.equipment.ToolSelectionPolicy;
import com.aicompanion.inventory.EquipmentManager;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ToolClassification11356Test {

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void testPickaxeIsNotAxe() {
        ItemStack diamondPick = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack ironPick = new ItemStack(Items.IRON_PICKAXE);
        ItemStack woodPick = new ItemStack(Items.WOODEN_PICKAXE);
        ItemStack stonePick = new ItemStack(Items.STONE_PICKAXE);
        ItemStack netheritePick = new ItemStack(Items.NETHERITE_PICKAXE);

        assertFalse(EquipmentManager.isAxe(diamondPick), "Diamond pickaxe must NOT be classified as an axe!");
        assertFalse(EquipmentManager.isAxe(ironPick), "Iron pickaxe must NOT be classified as an axe!");
        assertFalse(EquipmentManager.isAxe(woodPick), "Wooden pickaxe must NOT be classified as an axe!");
        assertFalse(EquipmentManager.isAxe(stonePick), "Stone pickaxe must NOT be classified as an axe!");
        assertFalse(EquipmentManager.isAxe(netheritePick), "Netherite pickaxe must NOT be classified as an axe!");
    }

    @Test
    public void testAxeClassification() {
        ItemStack diamondAxe = new ItemStack(Items.DIAMOND_AXE);
        ItemStack ironAxe = new ItemStack(Items.IRON_AXE);
        ItemStack woodAxe = new ItemStack(Items.WOODEN_AXE);

        assertTrue(EquipmentManager.isAxe(diamondAxe), "Diamond axe must be classified as an axe!");
        assertTrue(EquipmentManager.isAxe(ironAxe), "Iron axe must be classified as an axe!");
        assertTrue(EquipmentManager.isAxe(woodAxe), "Wooden axe must be classified as an axe!");

        assertFalse(EquipmentManager.isPickaxe(diamondAxe), "Axe must NOT be classified as a pickaxe!");
        assertFalse(EquipmentManager.isShovel(diamondAxe), "Axe must NOT be classified as a shovel!");
    }

    @Test
    public void testPickaxeClassification() {
        ItemStack diamondPick = new ItemStack(Items.DIAMOND_PICKAXE);
        assertTrue(EquipmentManager.isPickaxe(diamondPick), "Diamond pickaxe must be classified as a pickaxe!");
        assertFalse(EquipmentManager.isShovel(diamondPick), "Pickaxe must NOT be classified as a shovel!");
        assertFalse(EquipmentManager.isHoe(diamondPick), "Pickaxe must NOT be classified as a hoe!");
    }

    @Test
    public void testShovelClassification() {
        ItemStack diamondShovel = new ItemStack(Items.DIAMOND_SHOVEL);
        assertTrue(EquipmentManager.isShovel(diamondShovel), "Diamond shovel must be classified as a shovel!");
        assertFalse(EquipmentManager.isAxe(diamondShovel), "Shovel must NOT be classified as an axe!");
        assertFalse(EquipmentManager.isPickaxe(diamondShovel), "Shovel must NOT be classified as a pickaxe!");
    }

    @Test
    public void testToolsAreNotClassifiedAsPrimaryWeapons() {
        ItemStack diamondPick = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack diamondShovel = new ItemStack(Items.DIAMOND_SHOVEL);
        ItemStack diamondHoe = new ItemStack(Items.DIAMOND_HOE);
        ItemStack diamondSword = new ItemStack(Items.DIAMOND_SWORD);
        ItemStack diamondAxe = new ItemStack(Items.DIAMOND_AXE);

        assertFalse(EquipmentManager.isSwordOrWeapon(diamondPick), "Pickaxe must NOT be classified as a weapon!");
        assertFalse(EquipmentManager.isSwordOrWeapon(diamondShovel), "Shovel must NOT be classified as a weapon!");
        assertFalse(EquipmentManager.isSwordOrWeapon(diamondHoe), "Hoe must NOT be classified as a weapon!");

        assertTrue(EquipmentManager.isSwordOrWeapon(diamondSword), "Sword must be classified as a weapon!");
        assertTrue(EquipmentManager.isSwordOrWeapon(diamondAxe), "Axe can be used as a combat weapon!");
    }

    @Test
    public void testToolBlockPreferences() {
        assertTrue(ToolSelectionPolicy.isAxePreferred(Blocks.OAK_LOG.defaultBlockState()), "Oak log prefers axe!");
        assertTrue(ToolSelectionPolicy.isAxePreferred(Blocks.OAK_PLANKS.defaultBlockState()), "Planks prefer axe!");
        assertTrue(ToolSelectionPolicy.isPickaxePreferred(Blocks.STONE.defaultBlockState()), "Stone prefers pickaxe!");
        assertTrue(ToolSelectionPolicy.isPickaxePreferred(Blocks.IRON_ORE.defaultBlockState()), "Iron ore prefers pickaxe!");
        assertTrue(ToolSelectionPolicy.isShovelPreferred(Blocks.DIRT.defaultBlockState()), "Dirt prefers shovel!");
        assertTrue(ToolSelectionPolicy.isShovelPreferred(Blocks.SAND.defaultBlockState()), "Sand prefers shovel!");
    }
}
