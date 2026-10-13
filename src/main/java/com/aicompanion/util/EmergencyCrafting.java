package com.aicompanion.util;

import com.aicompanion.AICompanionMod;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.inventory.CompanionInventoryHandler;
import com.aicompanion.inventory.EquipmentManager;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeHooks;

import java.util.function.Predicate;

/**
 * 1.13.12: fabricación de emergencia durante un rescate (atrapado en agua / pozo / túnel).
 * Usa las recetas reales del servidor y consume ingredientes reales del inventario (nada gratis):
 * troncos -> tablones, tablones -> palos, mesa de trabajo, y un pico (piedra si hay roca, si no madera).
 */
public final class EmergencyCrafting {

    private EmergencyCrafting() {}

    /** Crafts one batch of the first recipe whose result matches {@code result} and whose ingredients are available. */
    public static boolean craftOnce(CompanionEntity companion, Predicate<ItemStack> result) {
        if (companion == null || companion.level() == null) return false;
        for (CraftingRecipe recipe : companion.level().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack out = recipe.getResultItem(companion.level().registryAccess());
            if (out.isEmpty() || !result.test(out)) continue;
            if (tryApply(companion, recipe)) {
                AICompanionMod.LOGGER.info("[ESCLAVO-RESCUE] Fabricación de emergencia: {} x{}", out.getHoverName().getString(), out.getCount());
                return true;
            }
        }
        return false;
    }

    private static final java.util.Map<java.util.UUID, Long> LAST_AXE_CRAFT_TICK = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Long> LAST_PICKAXE_CRAFT_TICK = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Long> LAST_SHOVEL_CRAFT_TICK = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long TOOL_CRAFT_COOLDOWN_TICKS = 100L; // 5 segundos

    private static boolean tryPickupNearbyTool(CompanionEntity companion, Predicate<ItemStack> toolPredicate) {
        if (companion == null || companion.level() == null || companion.level().isClientSide()) return false;
        java.util.List<ItemEntity> items = companion.level().getEntitiesOfClass(ItemEntity.class, companion.getBoundingBox().inflate(3.5),
                e -> e.isAlive() && toolPredicate.test(e.getItem()));
        if (items.isEmpty()) return false;

        ItemEntity nearest = items.get(0);
        ItemStack stack = nearest.getItem();
        CompanionInventoryHandler inv = companion.getInventoryHandler();

        ItemStack main = inv.getStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND);
        if (main.isEmpty()) {
            inv.setStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND, stack);
            nearest.discard();
            companion.level().playSound(null, companion.blockPosition(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.2F, (companion.getRandom().nextFloat() - companion.getRandom().nextFloat()) * 0.2F + 1.0F);
            companion.syncToOwner();
            return true;
        }

        ItemStack left = inv.addItem(stack);
        if (left.isEmpty()) {
            nearest.discard();
            companion.level().playSound(null, companion.blockPosition(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.2F, (companion.getRandom().nextFloat() - companion.getRandom().nextFloat()) * 0.2F + 1.0F);
            companion.syncToOwner();
            return true;
        } else {
            nearest.setItem(left);
        }
        return false;
    }

    public static boolean craftOnce(CompanionEntity companion, Item item) {
        return craftOnce(companion, s -> s.is(item));
    }

    private static boolean tryApply(CompanionEntity companion, CraftingRecipe recipe) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        int n = CompanionInventoryHandler.MAIN_SLOTS;
        int[] use = new int[n];
        for (Ingredient ing : recipe.getIngredients()) {
            if (ing.isEmpty()) continue;
            boolean matched = false;
            for (int i = 0; i < n; i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (!s.isEmpty() && s.getCount() - use[i] > 0 && ing.test(s)) {
                    use[i]++;
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        ItemStack out = recipe.getResultItem(companion.level().registryAccess()).copy();
        if (out.isEmpty()) return false;

        // 1.13.59: Verificación estricta de espacio de inventario para evitar crear objetos que caigan al suelo en bucle
        boolean canFit = inv.hasSpaceFor(out);
        if (!canFit) {
            for (int i = 0; i < n; i++) {
                if (use[i] > 0 && inv.getStackInSlot(i).getCount() == use[i]) {
                    canFit = true;
                    break;
                }
            }
        }
        if (!canFit && (EquipmentManager.isAxe(out) || EquipmentManager.isPickaxe(out) || EquipmentManager.isShovel(out))) {
            ItemStack main = inv.getStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND);
            if (main.isEmpty() || (!EquipmentManager.isAxe(main) && !EquipmentManager.isPickaxe(main) && !EquipmentManager.isShovel(main) && !EquipmentManager.isSword(main))) {
                canFit = true;
            }
        }
        if (!canFit) {
            inv.freeSpaceForMission(companion, null);
            canFit = inv.hasSpaceFor(out);
            if (!canFit) {
                AICompanionMod.LOGGER.warn("[ESCLAVO-CRAFT] Fabricación abortada: inventario lleno y sin espacio para recibir {}", out.getHoverName().getString());
                return false;
            }
        }

        for (int i = 0; i < n; i++) {
            if (use[i] <= 0) continue;
            ItemStack s = inv.getStackInSlot(i);
            ItemStack remainder = ForgeHooks.getCraftingRemainingItem(s);
            s.shrink(use[i]);
            if (s.isEmpty()) inv.setStackInSlot(i, ItemStack.EMPTY);
            if (!remainder.isEmpty()) giveOrDrop(companion, remainder);
        }
        giveOrDrop(companion, out);
        return true;
    }

    private static void giveOrDrop(CompanionEntity companion, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        CompanionInventoryHandler inv = companion.getInventoryHandler();

        // 1.13.59: Si es una herramienta y la mano principal está vacía, equipar directamente sin riesgo de tirarla
        if (EquipmentManager.isAxe(stack) || EquipmentManager.isPickaxe(stack) || EquipmentManager.isShovel(stack)) {
            ItemStack main = inv.getStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND);
            if (main.isEmpty()) {
                inv.setStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND, stack);
                companion.syncToOwner();
                return;
            }
        }

        ItemStack left = inv.addItem(stack);
        if (left != null && !left.isEmpty()) {
            AICompanionMod.LOGGER.warn("[ESCLAVO-CRAFT] Desbordamiento de inventario al fabricar: {} x{} tirado al suelo.", left.getHoverName().getString(), left.getCount());
            companion.level().addFreshEntity(new ItemEntity(companion.level(), companion.getX(), companion.getY() + 0.5, companion.getZ(), left));
        }
        companion.syncToOwner();
    }

    private static int count(CompanionEntity companion, Predicate<ItemStack> p) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        int total = 0;
        for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && p.test(s)) total += s.getCount();
        }
        return total;
    }

    /** Turns one log into planks (real recipe). */
    public static boolean planksFromLog(CompanionEntity companion) {
        if (count(companion, s -> s.is(ItemTags.LOGS)) <= 0) return false;
        return craftOnce(companion, s -> s.is(ItemTags.PLANKS));
    }

    private static boolean ensurePlanks(CompanionEntity companion, int needed) {
        int guard = 0;
        while (count(companion, s -> s.is(ItemTags.PLANKS)) < needed && guard++ < 4) {
            if (!planksFromLog(companion)) return false;
        }
        return count(companion, s -> s.is(ItemTags.PLANKS)) >= needed;
    }

    private static boolean ensureSticks(CompanionEntity companion, int needed) {
        int guard = 0;
        while (count(companion, s -> s.is(Items.STICK)) < needed && guard++ < 3) {
            if (!ensurePlanks(companion, 2)) return false;
            if (!craftOnce(companion, Items.STICK)) return false;
        }
        return count(companion, s -> s.is(Items.STICK)) >= needed;
    }

    private static boolean hasCraftingTable(CompanionEntity companion) {
        if (count(companion, s -> s.is(Items.CRAFTING_TABLE)) > 0) return true;
        BlockPos c = companion.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-4, -2, -4), c.offset(4, 2, 4))) {
            if (companion.level().getBlockState(p).is(Blocks.CRAFTING_TABLE)) return true;
        }
        return false;
    }

    public static boolean isStoneMaterial(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        return s.is(ItemTags.STONE_TOOL_MATERIALS)
                || s.is(Items.COBBLESTONE)
                || s.is(Items.COBBLED_DEEPSLATE)
                || s.is(Items.BLACKSTONE)
                || s.is(Items.STONE)
                || s.is(Items.DEEPSLATE);
    }

    public static boolean hasUsablePickaxe(CompanionEntity companion) {
        if (companion == null) return false;
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        ItemStack held = inv.getStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND);
        if (EquipmentManager.isPickaxe(held) && !EquipmentManager.isToolEndangered(held)) return true;
        for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (EquipmentManager.isPickaxe(s) && !EquipmentManager.isToolEndangered(s)) return true;
        }
        return false;
    }

    public static boolean hasUsableAxe(CompanionEntity companion) {
        if (companion == null) return false;
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        ItemStack held = inv.getStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND);
        if (EquipmentManager.isAxe(held) && !EquipmentManager.isToolEndangered(held)) return true;
        for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (EquipmentManager.isAxe(s) && !EquipmentManager.isToolEndangered(s)) return true;
        }
        return false;
    }

    public static boolean hasUsableShovel(CompanionEntity companion) {
        if (companion == null) return false;
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        ItemStack held = inv.getStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND);
        if (EquipmentManager.isShovel(held) && !EquipmentManager.isToolEndangered(held)) return true;
        for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (EquipmentManager.isShovel(s) && !EquipmentManager.isToolEndangered(s)) return true;
        }
        return false;
    }

    /**
     * Makes sure there is a usable pickaxe: crafts an iron, stone, or wooden pickaxe
     * depending on available materials, crafting planks / sticks / crafting table as needed.
     * Returns true if a usable pickaxe exists.
     */
    public static boolean ensurePickaxe(CompanionEntity companion) {
        if (companion == null) return false;
        if (hasUsablePickaxe(companion)) return true;

        if (tryPickupNearbyTool(companion, EquipmentManager::isPickaxe)) {
            if (hasUsablePickaxe(companion)) return true;
        }

        long gameTime = companion.level() != null ? companion.level().getGameTime() : 0;
        java.util.UUID uuid = companion.getUUID();
        Long lastCraft = LAST_PICKAXE_CRAFT_TICK.get(uuid);
        if (lastCraft != null && gameTime - lastCraft < TOOL_CRAFT_COOLDOWN_TICKS) {
            return hasUsablePickaxe(companion);
        }
        LAST_PICKAXE_CRAFT_TICK.put(uuid, gameTime);

        Predicate<ItemStack> rock = EmergencyCrafting::isStoneMaterial;
        boolean iron = count(companion, s -> s.is(Items.IRON_INGOT)) >= 3;
        boolean stone = !iron && count(companion, rock) >= 3;
        int planksForHead = (iron || stone) ? 0 : 3;
        int planksForTable = hasCraftingTable(companion) ? 0 : 4;
        int planksForSticks = count(companion, s -> s.is(Items.STICK)) >= 2 ? 0 : 2;
        int totalPlanks = planksForHead + planksForTable + planksForSticks;
        int planksAvailable = count(companion, s -> s.is(ItemTags.PLANKS)) + 4 * count(companion, s -> s.is(ItemTags.LOGS));
        if (planksAvailable < totalPlanks) {
            AICompanionMod.LOGGER.info("[ESCLAVO-CRAFT] No hay materiales para un pico (tablones posibles={}, necesarios={})", planksAvailable, totalPlanks);
            return false;
        }
        if (!ensureSticks(companion, 2)) return false;
        if (!hasCraftingTable(companion)) {
            if (!ensurePlanks(companion, 4) || !craftOnce(companion, Items.CRAFTING_TABLE)) return false;
        }
        if (iron && craftOnce(companion, Items.IRON_PICKAXE)) {
            if (hasUsablePickaxe(companion)) {
                companion.tellOwner("Me fabriqué un pico de hierro con los materiales de mi inventario.");
                return true;
            }
        }
        if (stone && craftOnce(companion, Items.STONE_PICKAXE)) {
            if (hasUsablePickaxe(companion)) {
                companion.tellOwner("Me fabriqué un pico de piedra para seguir picando.");
                return true;
            }
        }
        if (ensurePlanks(companion, 3) && craftOnce(companion, Items.WOODEN_PICKAXE)) {
            if (hasUsablePickaxe(companion)) {
                companion.tellOwner("Me fabriqué un pico de madera con la madera de mi inventario.");
                return true;
            }
        }
        return hasUsablePickaxe(companion);
    }

    /**
     * Makes sure there is a usable axe: crafts an iron, stone, or wooden axe if needed.
     */
    public static boolean ensureAxe(CompanionEntity companion) {
        if (companion == null) return false;
        if (hasUsableAxe(companion)) return true;

        if (tryPickupNearbyTool(companion, EquipmentManager::isAxe)) {
            if (hasUsableAxe(companion)) return true;
        }

        long gameTime = companion.level() != null ? companion.level().getGameTime() : 0;
        java.util.UUID uuid = companion.getUUID();
        Long lastCraft = LAST_AXE_CRAFT_TICK.get(uuid);
        if (lastCraft != null && gameTime - lastCraft < TOOL_CRAFT_COOLDOWN_TICKS) {
            return hasUsableAxe(companion);
        }
        LAST_AXE_CRAFT_TICK.put(uuid, gameTime);

        Predicate<ItemStack> rock = EmergencyCrafting::isStoneMaterial;
        boolean iron = count(companion, s -> s.is(Items.IRON_INGOT)) >= 3;
        boolean stone = !iron && count(companion, rock) >= 3;
        int planksForHead = (iron || stone) ? 0 : 3;
        int planksForTable = hasCraftingTable(companion) ? 0 : 4;
        int planksForSticks = count(companion, s -> s.is(Items.STICK)) >= 2 ? 0 : 2;
        int totalPlanks = planksForHead + planksForTable + planksForSticks;
        int planksAvailable = count(companion, s -> s.is(ItemTags.PLANKS)) + 4 * count(companion, s -> s.is(ItemTags.LOGS));
        if (planksAvailable < totalPlanks) return false;

        if (!ensureSticks(companion, 2)) return false;
        if (!hasCraftingTable(companion)) {
            if (!ensurePlanks(companion, 4) || !craftOnce(companion, Items.CRAFTING_TABLE)) return false;
        }
        if (iron && craftOnce(companion, Items.IRON_AXE)) {
            if (hasUsableAxe(companion)) {
                companion.tellOwner("Me fabriqué un hacha de hierro.");
                return true;
            }
        }
        if (stone && craftOnce(companion, Items.STONE_AXE)) {
            if (hasUsableAxe(companion)) {
                companion.tellOwner("Me fabriqué un hacha de piedra.");
                return true;
            }
        }
        if (ensurePlanks(companion, 3) && craftOnce(companion, Items.WOODEN_AXE)) {
            if (hasUsableAxe(companion)) {
                companion.tellOwner("Me fabriqué un hacha de madera.");
                return true;
            }
        }
        return hasUsableAxe(companion);
    }

    /**
     * Makes sure there is a usable shovel: crafts an iron, stone, or wooden shovel if needed.
     */
    public static boolean ensureShovel(CompanionEntity companion) {
        if (companion == null) return false;
        if (hasUsableShovel(companion)) return true;

        if (tryPickupNearbyTool(companion, EquipmentManager::isShovel)) {
            if (hasUsableShovel(companion)) return true;
        }

        long gameTime = companion.level() != null ? companion.level().getGameTime() : 0;
        java.util.UUID uuid = companion.getUUID();
        Long lastCraft = LAST_SHOVEL_CRAFT_TICK.get(uuid);
        if (lastCraft != null && gameTime - lastCraft < TOOL_CRAFT_COOLDOWN_TICKS) {
            return hasUsableShovel(companion);
        }
        LAST_SHOVEL_CRAFT_TICK.put(uuid, gameTime);

        Predicate<ItemStack> rock = EmergencyCrafting::isStoneMaterial;
        boolean iron = count(companion, s -> s.is(Items.IRON_INGOT)) >= 1;
        boolean stone = !iron && count(companion, rock) >= 1;
        int planksForHead = (iron || stone) ? 0 : 1;
        int planksForTable = hasCraftingTable(companion) ? 0 : 4;
        int planksForSticks = count(companion, s -> s.is(Items.STICK)) >= 2 ? 0 : 2;
        int totalPlanks = planksForHead + planksForTable + planksForSticks;
        int planksAvailable = count(companion, s -> s.is(ItemTags.PLANKS)) + 4 * count(companion, s -> s.is(ItemTags.LOGS));
        if (planksAvailable < totalPlanks) return false;

        if (!ensureSticks(companion, 2)) return false;
        if (!hasCraftingTable(companion)) {
            if (!ensurePlanks(companion, 4) || !craftOnce(companion, Items.CRAFTING_TABLE)) return false;
        }
        if (iron && craftOnce(companion, Items.IRON_SHOVEL)) {
            if (hasUsableShovel(companion)) {
                companion.tellOwner("Me fabriqué una pala de hierro.");
                return true;
            }
        }
        if (stone && craftOnce(companion, Items.STONE_SHOVEL)) {
            if (hasUsableShovel(companion)) {
                companion.tellOwner("Me fabriqué una pala de piedra.");
                return true;
            }
        }
        if (ensurePlanks(companion, 1) && craftOnce(companion, Items.WOODEN_SHOVEL)) {
            if (hasUsableShovel(companion)) {
                companion.tellOwner("Me fabriqué una pala de madera.");
                return true;
            }
        }
        return hasUsableShovel(companion);
    }

    /**
     * Makes sure there is a chest available: crafts a chest if materials (8 planks or equivalent logs) exist.
     */
    public static boolean ensureChest(CompanionEntity companion) {
        if (count(companion, s -> s.is(Items.CHEST)) > 0) return true;
        int planksForTable = hasCraftingTable(companion) ? 0 : 4;
        int totalPlanks = 8 + planksForTable;
        int planksAvailable = count(companion, s -> s.is(ItemTags.PLANKS)) + 4 * count(companion, s -> s.is(ItemTags.LOGS));
        if (planksAvailable < totalPlanks) return false;

        if (!hasCraftingTable(companion)) {
            if (!ensurePlanks(companion, 4) || !craftOnce(companion, Items.CRAFTING_TABLE)) return false;
        }
        if (!ensurePlanks(companion, 8)) return false;
        return craftOnce(companion, Items.CHEST);
    }
}
