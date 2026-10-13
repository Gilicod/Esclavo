package com.aicompanion.ai.planning;

import com.aicompanion.ai.mining.MiningTarget;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.inventory.CompanionInventoryHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dependency graph and tool tier resolver for Minecraft crafting and gathering.
 * Enables Esclavo to recursively plan prerequisite steps (gather wood -> craft table ->
 * pickaxe -> mine stone -> stone pick -> mine iron -> iron pick) when given complex goals.
 */
public class RecipeDependencyGraph {

    public static final int TIER_HAND = -1;
    public static final int TIER_WOOD = 0;
    public static final int TIER_STONE = 1;
    public static final int TIER_IRON = 2;
    public static final int TIER_DIAMOND = 3;
    public static final int TIER_NETHERITE = 4;

    /**
     * Determines the minimum pickaxe tier required to mine and collect drops from a block.
     */
    public static int getRequiredPickaxeTier(MiningTarget target) {
        if (target == null) return TIER_HAND;
        String name = target.getName().toLowerCase();
        if (name.contains("ancient") || name.contains("debris") || name.contains("obsidian")
                || name.contains("netherite") || name.contains("netherita")) {
            return TIER_DIAMOND;
        }
        if (name.contains("diamond") || name.contains("diamante") || name.contains("gold") || name.contains("oro")
                || name.contains("redstone") || name.contains("emerald") || name.contains("esmeralda")) {
            return TIER_IRON;
        }
        if (name.contains("iron") || name.contains("hierro") || name.contains("copper") || name.contains("cobre")
                || name.contains("lapis") || name.contains("lapislazuli")) {
            return TIER_STONE;
        }
        if (name.contains("stone") || name.contains("piedra") || name.contains("coal") || name.contains("carbon")
                || name.contains("cobblestone") || name.contains("adoquin")) {
            return TIER_WOOD;
        }
        return TIER_HAND;
    }

    /**
     * Finds the highest pickaxe tier currently owned in companion inventory.
     */
    public static int getHighestOwnedPickaxeTier(CompanionEntity companion) {
        if (companion == null) return TIER_HAND;
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        int highestTier = TIER_HAND;

        for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (com.aicompanion.inventory.EquipmentManager.isPickaxe(stack)) {
                int tier = TIER_WOOD;
                if (stack.getItem() instanceof TieredItem tiered) {
                    tier = tiered.getTier().getLevel();
                }
                if (tier > highestTier) {
                    highestTier = tier;
                }
            }
        }

        return highestTier;
    }

    /**
     * Checks if companion has any axe.
     */
    public static boolean hasAnyAxe(CompanionEntity companion) {
        if (companion == null) return false;
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (com.aicompanion.inventory.EquipmentManager.isAxe(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the highest axe tier currently owned in companion inventory.
     */
    public static int getHighestOwnedAxeTier(CompanionEntity companion) {
        if (companion == null) return TIER_HAND;
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        int highestTier = TIER_HAND;

        for (int i = 0; i < CompanionInventoryHandler.TOTAL_SLOTS; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (com.aicompanion.inventory.EquipmentManager.isAxe(stack)) {
                int tier = TIER_WOOD;
                if (stack.getItem() instanceof TieredItem tiered) {
                    tier = tiered.getTier().getLevel();
                }
                if (tier > highestTier) {
                    highestTier = tier;
                }
            }
        }

        return highestTier;
    }

    public static boolean hasPickaxeOfTier(CompanionEntity companion, int minTier) {
        return getHighestOwnedPickaxeTier(companion) >= minTier;
    }

    public static boolean hasAxeOfTier(CompanionEntity companion, int minTier) {
        return getHighestOwnedAxeTier(companion) >= minTier;
    }

    public static int countRawWoodLogs(CompanionEntity companion) {
        if (companion == null) return 0;
        return com.aicompanion.inventory.InventoryRequirementTracker.countWood(companion, "any");
    }

    public static int countPlanks(CompanionEntity companion) {
        if (companion == null) return 0;
        return com.aicompanion.inventory.InventoryRequirementTracker.countMatching(companion, s -> s.is(net.minecraft.tags.ItemTags.PLANKS));
    }

    public static int countSticks(CompanionEntity companion) {
        if (companion == null) return 0;
        return companion.getInventoryHandler().countItem(Items.STICK);
    }

    public static int countCobblestone(CompanionEntity companion) {
        if (companion == null) return 0;
        return companion.getInventoryHandler().countItem(Items.COBBLESTONE);
    }

    public static int countIronIngots(CompanionEntity companion) {
        if (companion == null) return 0;
        return companion.getInventoryHandler().countItem(Items.IRON_INGOT);
    }
}
