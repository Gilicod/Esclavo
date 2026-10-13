package com.aicompanion.inventory;

import com.aicompanion.ai.mining.MiningTarget;
import com.aicompanion.entity.CompanionEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.function.Predicate;

/**
 * Authoritative inventory requirement tracker for Esclavo.
 * All mission progress and goal completions MUST be derived from real physical
 * inventory state. No fake increment counters (amountCollected++) or minimum 1 hacks.
 */
public class InventoryRequirementTracker {

    /**
     * Counts how many items matching the predicate are physically present
     * in the companion's main inventory (slots 0..35) and hands.
     */
    public static int countMatching(CompanionEntity companion, Predicate<ItemStack> matcher) {
        if (companion == null || matcher == null) return 0;
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        if (inv == null) return 0;

        int count = 0;
        // Search companion storage slots (0..35) + hands (40, 41)
        for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty() && matcher.test(stack)) {
                count += stack.getCount();
            }
        }
        ItemStack mainHand = inv.getStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND);
        if (!mainHand.isEmpty() && matcher.test(mainHand)) {
            count += mainHand.getCount();
        }
        ItemStack offHand = inv.getStackInSlot(CompanionInventoryHandler.SLOT_OFFHAND);
        if (!offHand.isEmpty() && matcher.test(offHand)) {
            count += offHand.getCount();
        }

        return count;
    }

    /**
     * Counts items matching a specific Item instance.
     */
    public static int countItem(CompanionEntity companion, Item item) {
        return countMatching(companion, stack -> stack.is(item));
    }

    /**
     * Counts items matching a ResourceLocation ID.
     */
    public static int countResource(CompanionEntity companion, ResourceLocation resourceId) {
        if (resourceId == null) return 0;
        Item item = BuiltInRegistries.ITEM.get(resourceId);
        if (item != null && item != Items.AIR) {
            return countItem(companion, item);
        }
        // Fallback matching by registry path
        String path = resourceId.getPath();
        return countMatching(companion, stack -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return id != null && id.getPath().equals(path);
        });
    }

    /**
     * Translates and matches wood types between Spanish and English IDs.
     */
    public static boolean isWoodTypeMatch(String itemNameOrDesc, String requestedWood) {
        if (itemNameOrDesc == null) return false;
        String s = itemNameOrDesc.toLowerCase();
        String target = requestedWood != null ? requestedWood.toLowerCase().trim() : "";
        if (target.isEmpty() || target.equals("any") || target.equals("all") || target.equals("madera")
                || target.equals("wood") || target.equals("cualquiera") || target.equals("arbol")
                || target.equals("árbol") || target.equals("tronco") || target.equals("leña")) {
            return true;
        }

        String resolved = com.aicompanion.ai.parser.SpanishAliases.resolveWood(target);
        if (resolved == null || resolved.isEmpty() || resolved.equals("any")) {
            resolved = target;
        }

        if (resolved.equals("oak")) {
            return s.contains("oak") && !s.contains("dark_oak");
        } else if (resolved.equals("dark_oak")) {
            return s.contains("dark_oak");
        } else {
            return s.contains(resolved);
        }
    }

    /**
     * Counts logs/wood matching a requested wood type (or any wood if "any"/"wood"/"madera").
     */
    public static int countWood(CompanionEntity companion, String woodType) {
        if (companion == null) return 0;
        return countMatching(companion, stack -> {
            if (!stack.is(ItemTags.LOGS) && !stack.is(ItemTags.WARPED_STEMS) && !stack.is(ItemTags.CRIMSON_STEMS)) {
                return false;
            }
            String itemName = stack.getItem().getDescriptionId().toLowerCase();
            return isWoodTypeMatch(itemName, woodType);
        });
    }

    /**
     * Counts drops matching a specific MiningTarget (e.g. raw iron/iron ore, diamonds, coal, etc.)
     */
    public static int countMiningTarget(CompanionEntity companion, MiningTarget target) {
        if (companion == null || target == null) return 0;
        return countMatching(companion, target::isGoalItem);
    }

    /**
     * Verifies if companion owns at least requiredAmount of the matching predicate.
     */
    public static boolean hasEnough(CompanionEntity companion, Predicate<ItemStack> matcher, int requiredAmount) {
        return countMatching(companion, matcher) >= requiredAmount;
    }

    /**
     * Verifies if companion owns at least requiredAmount of wood.
     */
    public static boolean hasEnoughWood(CompanionEntity companion, String woodType, int requiredAmount) {
        return countWood(companion, woodType) >= requiredAmount;
    }

    /**
     * Verifies if companion owns at least requiredAmount of mining target.
     */
    public static boolean hasEnoughMiningTarget(CompanionEntity companion, MiningTarget target, int requiredAmount) {
        return countMiningTarget(companion, target) >= requiredAmount;
    }
}
