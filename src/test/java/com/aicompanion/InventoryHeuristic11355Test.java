package com.aicompanion;

import com.aicompanion.inventory.CompanionInventoryHandler;
import com.aicompanion.inventory.ItemHeuristicValue;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class InventoryHeuristic11355Test {

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void testVanillaTrashClassification() {
        ItemStack diorite = new ItemStack(Items.DIORITE);
        ItemStack andesite = new ItemStack(Items.ANDESITE);
        ItemStack granite = new ItemStack(Items.GRANITE);
        ItemStack dirt = new ItemStack(Items.DIRT);
        ItemStack gravel = new ItemStack(Items.GRAVEL);
        ItemStack seeds = new ItemStack(Items.WHEAT_SEEDS);

        assertEquals(ItemHeuristicValue.PriorityCategory.DISPOSABLE_TRASH, ItemHeuristicValue.getCategory(diorite, null));
        assertEquals(ItemHeuristicValue.PriorityCategory.DISPOSABLE_TRASH, ItemHeuristicValue.getCategory(andesite, null));
        assertEquals(ItemHeuristicValue.PriorityCategory.DISPOSABLE_TRASH, ItemHeuristicValue.getCategory(granite, null));
        assertEquals(ItemHeuristicValue.PriorityCategory.MEDIUM_VALUE, ItemHeuristicValue.getCategory(dirt, null));
        assertEquals(ItemHeuristicValue.PriorityCategory.LOW_VALUE, ItemHeuristicValue.getCategory(gravel, null));
        assertEquals(ItemHeuristicValue.PriorityCategory.DISPOSABLE_TRASH, ItemHeuristicValue.getCategory(seeds, null));

        assertTrue(ItemHeuristicValue.isTrash(diorite, null));
        assertFalse(ItemHeuristicValue.isImmutable(diorite, null));
    }

    @Test
    public void testImmutableValuablesClassification() {
        ItemStack diamondSword = new ItemStack(Items.DIAMOND_SWORD);
        ItemStack ironPick = new ItemStack(Items.IRON_PICKAXE);
        ItemStack steak = new ItemStack(Items.COOKED_BEEF);
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        ItemStack ancientDebris = new ItemStack(Items.ANCIENT_DEBRIS);
        ItemStack goldenApple = new ItemStack(Items.GOLDEN_APPLE);

        assertEquals(ItemHeuristicValue.PriorityCategory.IMMUTABLE, ItemHeuristicValue.getCategory(diamondSword, null));
        assertEquals(ItemHeuristicValue.PriorityCategory.IMMUTABLE, ItemHeuristicValue.getCategory(ironPick, null));
        assertEquals(ItemHeuristicValue.PriorityCategory.IMMUTABLE, ItemHeuristicValue.getCategory(steak, null));
        assertEquals(ItemHeuristicValue.PriorityCategory.IMMUTABLE, ItemHeuristicValue.getCategory(diamond, null));
        assertEquals(ItemHeuristicValue.PriorityCategory.IMMUTABLE, ItemHeuristicValue.getCategory(ancientDebris, null));
        assertEquals(ItemHeuristicValue.PriorityCategory.IMMUTABLE, ItemHeuristicValue.getCategory(goldenApple, null));

        assertTrue(ItemHeuristicValue.isImmutable(diamond, null));
        assertFalse(ItemHeuristicValue.isTrash(diamond, null));
    }

    @Test
    public void testBuildingBlockSuppliesClassification() {
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);
        ItemStack stone = new ItemStack(Items.STONE);
        ItemStack deepslate = new ItemStack(Items.DEEPSLATE);
        ItemStack netherrack = new ItemStack(Items.NETHERRACK);

        assertEquals(ItemHeuristicValue.PriorityCategory.MEDIUM_VALUE, ItemHeuristicValue.getCategory(cobble, null));
        assertEquals(ItemHeuristicValue.PriorityCategory.MEDIUM_VALUE, ItemHeuristicValue.getCategory(stone, null));
        assertEquals(ItemHeuristicValue.PriorityCategory.MEDIUM_VALUE, ItemHeuristicValue.getCategory(deepslate, null));
        assertEquals(ItemHeuristicValue.PriorityCategory.MEDIUM_VALUE, ItemHeuristicValue.getCategory(netherrack, null));

        assertTrue(CompanionInventoryHandler.isCommonBuildingBlock(cobble));
        assertTrue(CompanionInventoryHandler.isCommonBuildingBlock(deepslate));
        assertTrue(CompanionInventoryHandler.isCommonBuildingBlock(netherrack));
    }

    @Test
    public void testLowValueClassification() {
        ItemStack feather = new ItemStack(Items.FEATHER);
        ItemStack flint = new ItemStack(Items.FLINT);
        ItemStack oakSapling = new ItemStack(Items.OAK_SAPLING);

        assertEquals(ItemHeuristicValue.PriorityCategory.LOW_VALUE, ItemHeuristicValue.getCategory(feather, null));
        assertEquals(ItemHeuristicValue.PriorityCategory.LOW_VALUE, ItemHeuristicValue.getCategory(flint, null));
        assertEquals(ItemHeuristicValue.PriorityCategory.LOW_VALUE, ItemHeuristicValue.getCategory(oakSapling, null));
    }
}
