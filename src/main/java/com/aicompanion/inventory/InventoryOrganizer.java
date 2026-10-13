package com.aicompanion.inventory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Smart Autonomous Inventory Organizer for Esclavo (1.13.29).
 * Provides compaction of compatible stacks and categorization into 7 clean tiers:
 * 1. Herramientas (Tools)
 * 2. Armas y Defensa (Weapons & Armor defense items)
 * 3. Alimentos y Curación (Food & Potions)
 * 4. Materiales de Fabricación (Crafting components: logs, planks, sticks, string...)
 * 5. Minerales y Recursos Valiosos (Diamonds, Iron, Gold, Ancient Debris, Coal...)
 * 6. Bloques de Construcción (Cobblestone, Deepslate, Dirt, Sand...)
 * 7. Varios (Misc items)
 *
 * Guarantees 100% item conservation (sum of item counts before == after).
 * Armor (slots 36..39) and Hands (slots 40..41) are strictly preserved and never moved.
 */
public class InventoryOrganizer {

    public enum Category {
        TOOLS(1),
        WEAPONS(2),
        FOOD(3),
        CRAFTING(4),
        MINERALS(5),
        BUILDING(6),
        MISC(7);

        private final int sortPriority;

        Category(int sortPriority) {
            this.sortPriority = sortPriority;
        }

        public int getSortPriority() {
            return sortPriority;
        }
    }

    /**
     * Compacts compatible item stacks in the 36 main backpack slots.
     * Merges stacks of the same item and same NBT tags up to getMaxStackSize().
     *
     * @param inv The companion inventory handler
     * @return Number of slots freed by compaction
     */
    public static int compact(CompanionInventoryHandler inv) {
        if (inv == null) return 0;
        int freedSlots = 0;

        for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
            ItemStack base = inv.getStackInSlot(i);
            if (base.isEmpty()) continue;
            if (base.getCount() >= base.getMaxStackSize()) continue;

            for (int j = i + 1; j < CompanionInventoryHandler.MAIN_SLOTS; j++) {
                ItemStack target = inv.getStackInSlot(j);
                if (target.isEmpty()) continue;

                if (ItemStack.isSameItemSameTags(base, target)) {
                    int space = base.getMaxStackSize() - base.getCount();
                    int toMove = Math.min(space, target.getCount());

                    base.grow(toMove);
                    target.shrink(toMove);

                    if (target.isEmpty()) {
                        inv.setStackInSlot(j, ItemStack.EMPTY);
                        freedSlots++;
                    }

                    if (base.getCount() >= base.getMaxStackSize()) {
                        break;
                    }
                }
            }
        }
        return freedSlots;
    }

    /**
     * Fully organizes and defragments the 36 main backpack slots.
     * Compacts stacks, categorizes items, sorts them predictably, and places them
     * starting at slot 0. Does NOT touch armor or hand slots.
     *
     * @param inv The companion inventory handler
     */
    public static void organize(CompanionInventoryHandler inv) {
        if (inv == null) return;

        // Step 1: Compact identical stacks first
        compact(inv);

        // Step 2: Extract all items from main backpack slots (0..35)
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty()) {
                items.add(s.copy());
            }
            inv.setStackInSlot(i, ItemStack.EMPTY);
        }

        if (items.isEmpty()) return;

        // Step 3: Sort items according to categories and intra-category priority
        items.sort(Comparator
                .comparingInt((ItemStack s) -> getCategory(s).getSortPriority())
                .thenComparing(InventoryOrganizer::getIntraCategoryScore)
                .thenComparing((ItemStack s) -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString())
                .thenComparing((ItemStack s) -> -s.getCount())
        );

        // Step 4: Write sorted items back into slots 0..35
        for (int i = 0; i < items.size() && i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
            inv.setStackInSlot(i, items.get(i));
        }
    }

    public static Category getCategory(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Category.MISC;
        Item item = stack.getItem();

        // 1. WEAPONS & DEFENSE
        if (item instanceof SwordItem || item instanceof BowItem || item instanceof CrossbowItem ||
            item instanceof ShieldItem || item instanceof TridentItem) {
            return Category.WEAPONS;
        }

        // 2. TOOLS
        if (item instanceof PickaxeItem || item instanceof AxeItem || item instanceof ShovelItem ||
            item instanceof HoeItem || item instanceof ShearsItem || item instanceof FishingRodItem ||
            item instanceof FlintAndSteelItem || item == Items.COMPASS || item == Items.RECOVERY_COMPASS ||
            item == Items.CLOCK || item == Items.SPYGLASS || item == Items.LEAD) {
            return Category.TOOLS;
        }

        // 3. FOOD & HEALING
        if (item.isEdible() || item == Items.POTION || item == Items.SPLASH_POTION ||
            item == Items.LINGERING_POTION || item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE) {
            return Category.FOOD;
        }

        // 4. MINERALS & VALUABLES
        if (isMineralOrValuable(stack, item)) {
            return Category.MINERALS;
        }

        // 5. CRAFTING MATERIALS
        if (isCraftingComponent(stack, item)) {
            return Category.CRAFTING;
        }

        // 6. BUILDING BLOCKS
        if (item instanceof BlockItem) {
            return Category.BUILDING;
        }

        // 7. MISC
        return Category.MISC;
    }

    private static boolean isMineralOrValuable(ItemStack stack, Item item) {
        if (stack.is(ItemTags.COALS) || stack.is(ItemTags.IRON_ORES) || stack.is(ItemTags.GOLD_ORES) ||
            stack.is(ItemTags.DIAMOND_ORES) || stack.is(ItemTags.EMERALD_ORES) || stack.is(ItemTags.COPPER_ORES) ||
            stack.is(ItemTags.REDSTONE_ORES) || stack.is(ItemTags.LAPIS_ORES)) {
            return true;
        }
        return item == Items.DIAMOND || item == Items.NETHERITE_INGOT || item == Items.NETHERITE_SCRAP ||
               item == Items.ANCIENT_DEBRIS || item == Items.EMERALD || item == Items.RAW_IRON ||
               item == Items.RAW_GOLD || item == Items.RAW_COPPER || item == Items.IRON_INGOT ||
               item == Items.GOLD_INGOT || item == Items.COPPER_INGOT || item == Items.REDSTONE ||
               item == Items.LAPIS_LAZULI || item == Items.COAL || item == Items.CHARCOAL ||
               item == Items.AMETHYST_SHARD || item == Items.QUARTZ || item == Items.NETHER_QUARTZ_ORE ||
               item == Items.GOLD_NUGGET || item == Items.IRON_NUGGET;
    }

    private static boolean isCraftingComponent(ItemStack stack, Item item) {
        if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS)) {
            return true;
        }
        return item == Items.STICK || item == Items.STRING || item == Items.FEATHER ||
               item == Items.LEATHER || item == Items.BONE || item == Items.GUNPOWDER ||
               item == Items.FLINT || item == Items.SLIME_BALL || item == Items.BLAZE_ROD ||
               item == Items.ENDER_PEARL || item == Items.CLAY_BALL || item == Items.BRICK ||
               item == Items.NETHER_BRICK || item == Items.PAPER || item == Items.BOOK ||
               item == Items.SUGAR_CANE || item == Items.SUGAR || item == Items.WHEAT ||
               item == Items.BOWL || item == Items.GLASS_BOTTLE;
    }

    /**
     * Determines a sub-score within a category to place higher-tier items first.
     */
    private static int getIntraCategoryScore(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof TieredItem tiered) {
            Tier tier = tiered.getTier();
            int tierRank = 0;
            if (tier == Tiers.NETHERITE) tierRank = 100;
            else if (tier == Tiers.DIAMOND) tierRank = 80;
            else if (tier == Tiers.IRON) tierRank = 60;
            else if (tier == Tiers.STONE) tierRank = 40;
            else if (tier == Tiers.GOLD) tierRank = 30;
            else if (tier == Tiers.WOOD) tierRank = 20;

            int toolTypeRank = 0;
            if (item instanceof SwordItem) toolTypeRank = 10;
            else if (item instanceof PickaxeItem) toolTypeRank = 9;
            else if (item instanceof AxeItem) toolTypeRank = 8;
            else if (item instanceof ShovelItem) toolTypeRank = 7;
            else if (item instanceof HoeItem) toolTypeRank = 6;

            return -(tierRank * 10 + toolTypeRank);
        }

        if (item == Items.NETHERITE_INGOT || item == Items.ANCIENT_DEBRIS) return -100;
        if (item == Items.DIAMOND) return -90;
        if (item == Items.EMERALD) return -80;
        if (item == Items.GOLD_INGOT || item == Items.RAW_GOLD) return -70;
        if (item == Items.IRON_INGOT || item == Items.RAW_IRON) return -60;
        if (item == Items.COPPER_INGOT || item == Items.RAW_COPPER) return -50;
        if (item == Items.REDSTONE) return -40;
        if (item == Items.LAPIS_LAZULI) return -35;
        if (item == Items.COAL || item == Items.CHARCOAL) return -30;

        return 0;
    }
}
