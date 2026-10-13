package com.aicompanion.ai.equipment;

import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import com.aicompanion.inventory.CompanionInventoryHandler;
import com.aicompanion.inventory.EquipmentManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Strict tool selection policy for Esclavo.
 * Enforces appropriate tool usage based on active task context (Axe for Chop,
 * Pickaxe for Mine, Shovel for Digging, Weapon for Combat).
 * Locks the main hand during active operations to prevent auto-equip from switching tools mid-task.
 */
public class ToolSelectionPolicy {

    /**
     * Determines whether the companion's main hand item is currently locked
     * by an active task (mining, chopping, building) or active navigation actions.
     */
    public static boolean isMainHandLocked(CompanionEntity companion) {
        if (companion == null) return false;
        CompanionState state = companion.getCurrentState();
        if (state == CompanionState.MINING
                || state == CompanionState.CHOPPING
                || state == CompanionState.BUILDING
                || state == CompanionState.CRAFTING) {
            return true;
        }
        if (companion.getNavManager() != null) {
            var prov = companion.getNavManager().getActiveProvider();
            if (prov instanceof com.aicompanion.ai.nav.InternalBaritoneNavigationProvider b && b.getExecutor() != null && b.getExecutor().hasActions()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ensures an Axe is equipped in main hand for wood chopping.
     * Auto-crafts an axe if materials are available.
     */
    public static boolean ensureAxeEquipped(CompanionEntity companion) {
        if (companion == null) return false;
        if (EquipmentManager.hasAxeEquipped(companion)) return true;

        int bestSlot = EquipmentManager.findBestAxeSlot(companion);
        if (bestSlot != -1) {
            return EquipmentManager.swapMainHandSafely(companion, bestSlot);
        }

        if (com.aicompanion.util.EmergencyCrafting.ensureAxe(companion)) {
            bestSlot = EquipmentManager.findBestAxeSlot(companion);
            if (bestSlot != -1) {
                return EquipmentManager.swapMainHandSafely(companion, bestSlot);
            }
        }

        int anySlot = EquipmentManager.findAnyAxeSlot(companion);
        if (anySlot != -1) {
            return EquipmentManager.swapMainHandSafely(companion, anySlot);
        }

        return false;
    }

    /**
     * Ensures a suitable Pickaxe is equipped in main hand for the given block.
     * If none found in inventory, auto-crafts a replacement using inventory materials.
     * Fallback to any pickaxe in inventory to prevent navigation paralysis or using an axe on stone.
     */
    public static boolean ensurePickaxeEquipped(CompanionEntity companion, BlockState state) {
        if (companion == null) return false;
        if (EquipmentManager.hasSuitablePickaxeEquipped(companion, state)) return true;

        int bestSlot = EquipmentManager.findBestPickaxeSlot(companion, state);
        if (bestSlot != -1) {
            return EquipmentManager.swapMainHandSafely(companion, bestSlot);
        }

        // Auto-craft pickaxe if materials are available
        if (com.aicompanion.util.EmergencyCrafting.ensurePickaxe(companion)) {
            bestSlot = EquipmentManager.findBestPickaxeSlot(companion, state);
            if (bestSlot != -1) {
                return EquipmentManager.swapMainHandSafely(companion, bestSlot);
            }
        }

        // Fallback: equip ANY pickaxe in hand or inventory to avoid getting stuck or using an axe
        int anySlot = EquipmentManager.findAnyPickaxeSlot(companion, state);
        if (anySlot != -1) {
            return EquipmentManager.swapMainHandSafely(companion, anySlot);
        }

        return false;
    }

    /**
     * Finds and equips the best shovel for digging soft blocks.
     * Auto-crafts a shovel if materials are available.
     */
    public static boolean ensureShovelEquipped(CompanionEntity companion) {
        if (companion == null) return false;
        ItemStack main = EquipmentManager.getMainHand(companion);
        if (EquipmentManager.isShovel(main) && !EquipmentManager.isToolEndangered(main)) return true;

        int bestSlot = EquipmentManager.findBestShovelSlot(companion);
        if (bestSlot != -1) {
            return EquipmentManager.swapMainHandSafely(companion, bestSlot);
        }

        if (com.aicompanion.util.EmergencyCrafting.ensureShovel(companion)) {
            bestSlot = EquipmentManager.findBestShovelSlot(companion);
            if (bestSlot != -1) {
                return EquipmentManager.swapMainHandSafely(companion, bestSlot);
            }
        }

        int anySlot = EquipmentManager.findAnyShovelSlot(companion);
        if (anySlot != -1) {
            return EquipmentManager.swapMainHandSafely(companion, anySlot);
        }

        return false;
    }

    /**
     * Ensures the best combat weapon is equipped.
     */
    public static boolean ensureWeaponEquipped(CompanionEntity companion) {
        if (companion == null) return false;
        if (EquipmentManager.hasWeaponEquipped(companion)) return true;

        int bestSlot = EquipmentManager.findBestWeaponSlot(companion);
        if (bestSlot != -1) {
            return EquipmentManager.swapMainHandSafely(companion, bestSlot);
        }
        return false;
    }

    public static boolean isPickaxePreferred(BlockState state) {
        if (state == null || state.isAir()) return false;
        String id = state.getBlock().getDescriptionId().toLowerCase();
        if (id.contains("stone") || id.contains("ore") || id.contains("deepslate") || id.contains("cobble")
                || id.contains("granite") || id.contains("diorite") || id.contains("andesite")
                || id.contains("basalt") || id.contains("blackstone") || id.contains("netherrack")
                || id.contains("terracotta") || id.contains("brick") || id.contains("iron") || id.contains("gold")
                || id.contains("diamond") || id.contains("copper") || id.contains("lapis") || id.contains("redstone")
                || id.contains("emerald") || id.contains("quartz") || id.contains("obsidian") || id.contains("debris")) {
            return true;
        }
        if (state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)) return true;
        if (state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || state.is(net.minecraft.tags.BlockTags.BASE_STONE_NETHER)) return true;
        if (state.requiresCorrectToolForDrops()) {
            if (!state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE) && !state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAxePreferred(BlockState state) {
        if (state == null || state.isAir()) return false;
        String id = state.getBlock().getDescriptionId().toLowerCase();
        if (id.contains("log") || id.contains("stem") || id.contains("wood") || id.contains("plank")
                || id.contains("fence") || id.contains("gate") || id.contains("door") || id.contains("trapdoor")
                || id.contains("chest") || id.contains("bookshelf") || id.contains("barrel") || id.contains("crafting_table")) {
            return true;
        }
        return state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE)
                || state.is(net.minecraft.tags.BlockTags.LOGS)
                || state.is(net.minecraft.tags.BlockTags.PLANKS);
    }

    public static boolean isShovelPreferred(BlockState state) {
        if (state == null || state.isAir()) return false;
        String id = state.getBlock().getDescriptionId().toLowerCase();
        if (id.contains("dirt") || id.contains("sand") || id.contains("gravel") || id.contains("clay")
                || id.contains("grass") || id.contains("soul_sand") || id.contains("soul_soil") || id.contains("mud")
                || id.contains("snow") || id.contains("podzol") || id.contains("mycelium")) {
            return true;
        }
        return state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL)
                || state.is(net.minecraft.tags.BlockTags.DIRT)
                || state.is(net.minecraft.tags.BlockTags.SAND);
    }

    /**
     * Dynamically equips the right tool for the target block (pickaxe, axe, or shovel).
     */
    public static boolean ensureToolForBlock(CompanionEntity companion, BlockState state) {
        if (companion == null || state == null || state.isAir()) return true;
        if (isPickaxePreferred(state)) {
            return ensurePickaxeEquipped(companion, state);
        } else if (isAxePreferred(state)) {
            return ensureAxeEquipped(companion);
        } else if (isShovelPreferred(state)) {
            return ensureShovelEquipped(companion);
        }
        return true;
    }
}
