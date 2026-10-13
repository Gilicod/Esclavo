package com.aicompanion.inventory;

import com.aicompanion.entity.CompanionEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class EquipmentManager {

    public static ItemStack getMainHand(CompanionEntity companion) {
        return companion.getInventoryHandler().getStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND);
    }

    public static ItemStack getOffHand(CompanionEntity companion) {
        return companion.getInventoryHandler().getStackInSlot(CompanionInventoryHandler.SLOT_OFFHAND);
    }

    public static boolean isPickaxe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof PickaxeItem) return true;
        String desc = item.getDescriptionId().toLowerCase();
        return desc.contains("pickaxe")
                || stack.is(net.minecraft.tags.ItemTags.create(new net.minecraft.resources.ResourceLocation("forge", "tools/pickaxes")))
                || stack.is(net.minecraft.tags.ItemTags.create(new net.minecraft.resources.ResourceLocation("forge", "pickaxes")));
    }

    public static boolean isAxe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof AxeItem) return true;
        if (item instanceof PickaxeItem) return false;
        String desc = item.getDescriptionId().toLowerCase();
        if (desc.contains("pickaxe")) return false; // Prevent pickaxes from matching "axe"
        return desc.contains("axe") || desc.contains("hatchet")
                || stack.is(net.minecraft.tags.ItemTags.create(new net.minecraft.resources.ResourceLocation("forge", "tools/axes")))
                || stack.is(net.minecraft.tags.ItemTags.create(new net.minecraft.resources.ResourceLocation("forge", "axes")));
    }

    public static boolean isShovel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof ShovelItem) return true;
        if (item instanceof PickaxeItem || item instanceof AxeItem) return false;
        String desc = item.getDescriptionId().toLowerCase();
        if (desc.contains("pickaxe") || desc.contains("axe")) return false;
        return desc.contains("shovel") || desc.contains("spade")
                || stack.is(net.minecraft.tags.ItemTags.create(new net.minecraft.resources.ResourceLocation("forge", "tools/shovels")))
                || stack.is(net.minecraft.tags.ItemTags.create(new net.minecraft.resources.ResourceLocation("forge", "shovels")));
    }

    public static boolean isHoe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof HoeItem) return true;
        if (item instanceof PickaxeItem || item instanceof AxeItem || item instanceof ShovelItem) return false;
        String desc = item.getDescriptionId().toLowerCase();
        return desc.contains("hoe")
                || stack.is(net.minecraft.tags.ItemTags.create(new net.minecraft.resources.ResourceLocation("forge", "tools/hoes")))
                || stack.is(net.minecraft.tags.ItemTags.create(new net.minecraft.resources.ResourceLocation("forge", "hoes")));
    }

    public static boolean isSword(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof SwordItem) return true;
        String desc = item.getDescriptionId().toLowerCase();
        return desc.contains("sword")
                || stack.is(net.minecraft.tags.ItemTags.create(new net.minecraft.resources.ResourceLocation("forge", "tools/swords")))
                || stack.is(net.minecraft.tags.ItemTags.create(new net.minecraft.resources.ResourceLocation("forge", "swords")));
    }

    public static boolean isSwordOrWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof PickaxeItem || item instanceof ShovelItem || item instanceof HoeItem) {
            return false; // Tools must never be classified as primary weapons
        }
        String desc = item.getDescriptionId().toLowerCase();
        if (desc.contains("pickaxe") || desc.contains("shovel") || desc.contains("hoe")) {
            return false;
        }
        return item instanceof SwordItem
                || item instanceof AxeItem
                || item instanceof TridentItem
                || desc.contains("sword")
                || desc.contains("weapon");
    }

    public static float calculateWeaponDamage(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 1.0f;
        Item item = stack.getItem();
        if (item instanceof SwordItem sword) {
            return sword.getDamage() + 4.0f;
        } else if (item instanceof AxeItem axe) {
            return axe.getAttackDamage() + 3.0f;
        } else if (item instanceof TridentItem) {
            return 9.0f;
        } else if (item instanceof PickaxeItem pickaxe) {
            return pickaxe.getTier().getAttackDamageBonus() + 2.0f;
        } else if (item instanceof ShovelItem shovel) {
            return shovel.getTier().getAttackDamageBonus() + 1.5f;
        } else if (item instanceof TieredItem tiered) {
            return tiered.getTier().getAttackDamageBonus() + 1.0f;
        }
        return 1.0f;
    }

    public static boolean hasSuitablePickaxeEquipped(CompanionEntity companion, BlockState state) {
        ItemStack main = getMainHand(companion);
        if (!isPickaxe(main)) return false;
        if (!state.requiresCorrectToolForDrops()) return true;
        return main.isCorrectToolForDrops(state);
    }

    public static boolean isToolEndangered(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) {
            return false;
        }
        boolean isValuable = stack.isEnchanted() ||
                             (stack.getItem() instanceof TieredItem tiered && tiered.getTier().getLevel() >= 2);
        // Disposable tools (unenchanted wood/stone) can be used until the end or replaced via auto-crafting.
        if (!isValuable) {
            return false;
        }
        int max = stack.getMaxDamage();
        int cur = stack.getDamageValue();
        int remaining = max - cur;
        double ratio = (double) remaining / (double) max;
        return (ratio <= 0.08) || (remaining <= 10);
    }

    /**
     * 1.13.12: modo emergencia. Mientras el protocolo de rescate está activo (atrapado en agua/pozo/túnel), el
     * Esclavo usa sus herramientas aunque estén casi rotas: salir es más importante que conservar el pico.
     */
    public static boolean isEmergency(CompanionEntity companion) {
        if (companion == null) return false;
        var sd = companion.getStuckDetector();
        return sd != null && sd.getRescueEngine() != null && sd.getRescueEngine().isRescuing();
    }

    public static boolean checkAndPreserveEndangeredTools(CompanionEntity companion) {
        if (companion == null) return false;
        if (isEmergency(companion)) return false; // en emergencia se usa hasta romperse
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        ItemStack main = getMainHand(companion);

        if (isToolEndangered(main)) {
            int emptySlot = -1;
            for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
                if (inv.getStackInSlot(i).isEmpty()) {
                    emptySlot = i;
                    break;
                }
            }

            if (emptySlot != -1) {
                inv.setStackInSlot(emptySlot, main);
                inv.setStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND, ItemStack.EMPTY);
            } else {
                inv.purgeTrashItems(companion);
                for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
                    if (inv.getStackInSlot(i).isEmpty()) {
                        inv.setStackInSlot(i, main);
                        inv.setStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND, ItemStack.EMPTY);
                        break;
                    }
                }
            }

            com.aicompanion.AICompanionMod.LOGGER.warn("[ESCLAVO-PRESERVATION] Preserved endangered tool '{}' (Durability <= 8%). Unequipped to prevent destruction.", main.getHoverName().getString());
            if (companion.getOwnerPlayer() != null) {
                companion.tellOwner("¡He guardado mi " + main.getHoverName().getString() + " valioso para que no se destruya!");
            }
            // Auto-craft a replacement immediately if possible so main hand is not left empty
            if (isPickaxe(main)) {
                com.aicompanion.util.EmergencyCrafting.ensurePickaxe(companion);
                int newSlot = findBestPickaxeSlot(companion, null);
                if (newSlot != -1) {
                    swapMainHandSafely(companion, newSlot);
                }
            }
            return true;
        }
        return false;
    }

    public static int findBestPickaxeSlot(CompanionEntity companion, BlockState state) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        boolean emergency = isEmergency(companion);
        int bestSlot = -1;
        int highestTier = -1;
        boolean wantsSilkTouch = state != null && (state.is(Blocks.GLOWSTONE) || state.is(Blocks.SEA_LANTERN)
                || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE));

        // Also consider the pickaxe already in the main hand
        ItemStack held = inv.getStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND);
        if (isPickaxe(held) && (emergency || !isToolEndangered(held))) {
            bestSlot = CompanionInventoryHandler.SLOT_MAINHAND;
            highestTier = held.getItem() instanceof TieredItem t ? t.getTier().getLevel() : 0;
            if (wantsSilkTouch && net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH, held) > 0) {
                highestTier += 100;
            }
        }

        for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (isPickaxe(stack)) {
                if (!emergency && isToolEndangered(stack)) continue; // Pilar 6: Protect endangered tools!
                int tier = 0;
                if (stack.getItem() instanceof TieredItem tiered) {
                    tier = tiered.getTier().getLevel();
                }
                if (wantsSilkTouch && net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH, stack) > 0) {
                    tier += 100;
                }
                if (tier > highestTier) {
                    highestTier = tier;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    /**
     * Fallback slot finder: finds ANY pickaxe in hand or inventory regardless of endangered status.
     * Used when navigation or escape requires breaking a block and no other pickaxe exists.
     */
    public static int findAnyPickaxeSlot(CompanionEntity companion, BlockState state) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        int bestSlot = -1;
        int highestTier = -1;

        ItemStack held = inv.getStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND);
        if (isPickaxe(held)) {
            bestSlot = CompanionInventoryHandler.SLOT_MAINHAND;
            highestTier = held.getItem() instanceof TieredItem t ? t.getTier().getLevel() : 0;
        }

        for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (isPickaxe(stack)) {
                int tier = 0;
                if (stack.getItem() instanceof TieredItem tiered) {
                    tier = tiered.getTier().getLevel();
                }
                if (tier > highestTier) {
                    highestTier = tier;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    public static boolean hasAxeEquipped(CompanionEntity companion) {
        ItemStack main = getMainHand(companion);
        return isAxe(main) && !isToolEndangered(main);
    }

    public static int findBestAxeSlot(CompanionEntity companion) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        int bestSlot = -1;
        int highestTier = -1;

        ItemStack held = inv.getStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND);
        if (isAxe(held) && !isToolEndangered(held)) {
            bestSlot = CompanionInventoryHandler.SLOT_MAINHAND;
            highestTier = held.getItem() instanceof TieredItem t ? t.getTier().getLevel() : 0;
        }

        for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (isAxe(stack)) {
                if (isToolEndangered(stack)) continue; // Pilar 6: Protect endangered tools!
                int tier = 0;
                if (stack.getItem() instanceof TieredItem tiered) {
                    tier = tiered.getTier().getLevel();
                }
                if (tier > highestTier) {
                    highestTier = tier;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    public static int findAnyAxeSlot(CompanionEntity companion) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        int bestSlot = -1;
        int highestTier = -1;

        ItemStack held = inv.getStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND);
        if (isAxe(held)) {
            bestSlot = CompanionInventoryHandler.SLOT_MAINHAND;
            highestTier = held.getItem() instanceof TieredItem t ? t.getTier().getLevel() : 0;
        }

        for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (isAxe(stack)) {
                int tier = 0;
                if (stack.getItem() instanceof TieredItem tiered) {
                    tier = tiered.getTier().getLevel();
                }
                if (tier > highestTier) {
                    highestTier = tier;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    public static int findAnyShovelSlot(CompanionEntity companion) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        int bestSlot = -1;
        int highestTier = -1;

        ItemStack held = inv.getStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND);
        if (isShovel(held)) {
            bestSlot = CompanionInventoryHandler.SLOT_MAINHAND;
            highestTier = held.getItem() instanceof TieredItem t ? t.getTier().getLevel() : 0;
        }

        for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (isShovel(stack)) {
                int tier = 0;
                if (stack.getItem() instanceof TieredItem tiered) {
                    tier = tiered.getTier().getLevel();
                }
                if (tier > highestTier) {
                    highestTier = tier;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    public static int findBestShovelSlot(CompanionEntity companion) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        int bestSlot = -1;
        int highestTier = -1;

        ItemStack held = inv.getStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND);
        if (isShovel(held) && !isToolEndangered(held)) {
            bestSlot = CompanionInventoryHandler.SLOT_MAINHAND;
            highestTier = held.getItem() instanceof TieredItem t ? t.getTier().getLevel() : 0;
        }

        for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (isShovel(stack)) {
                if (isToolEndangered(stack)) continue;
                int tier = 0;
                if (stack.getItem() instanceof TieredItem tiered) {
                    tier = tiered.getTier().getLevel();
                }
                if (tier > highestTier) {
                    highestTier = tier;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    public static boolean hasWeaponEquipped(CompanionEntity companion) {
        ItemStack main = getMainHand(companion);
        return isSwordOrWeapon(main) && !isToolEndangered(main);
    }

    public static int findBestWeaponSlot(CompanionEntity companion) {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        int bestSlot = -1;
        float highestDamage = -1.0f;

        ItemStack held = getMainHand(companion);
        if (isSwordOrWeapon(held) && !isToolEndangered(held)) {
            highestDamage = calculateWeaponDamage(held);
            bestSlot = CompanionInventoryHandler.SLOT_MAINHAND;
        }

        for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (isSwordOrWeapon(stack)) {
                if (isToolEndangered(stack)) continue; // Pilar 6: Protect endangered tools!
                float dmg = calculateWeaponDamage(stack);
                if (dmg > highestDamage) {
                    highestDamage = dmg;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    /**
     * Safely swaps the specified inventory slot with Main Hand (slot 40).
     * No items are duplicated or destroyed.
     */
    public static boolean swapMainHandSafely(CompanionEntity companion, int inventorySlot) {
        if (inventorySlot == CompanionInventoryHandler.SLOT_MAINHAND) {
            return true;
        }
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        if (inventorySlot < 0 || inventorySlot >= CompanionInventoryHandler.MAIN_SLOTS) {
            return false;
        }

        ItemStack currentMain = inv.getStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND);
        ItemStack targetTool = inv.getStackInSlot(inventorySlot);

        inv.setStackInSlot(CompanionInventoryHandler.SLOT_MAINHAND, targetTool);
        inv.setStackInSlot(inventorySlot, currentMain);
        companion.setItemSlot(EquipmentSlot.MAINHAND, targetTool);
        return true;
    }

    /**
     * Safely equips an armor item from an inventory slot into the correct armor slot.
     */
    public static boolean equipArmorSafely(CompanionEntity companion, int inventorySlot, EquipmentSlot targetSlot) {
        if (!targetSlot.isArmor()) return false;
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        if (inventorySlot < 0 || inventorySlot >= CompanionInventoryHandler.MAIN_SLOTS) return false;

        int armorSlotIdx = switch (targetSlot) {
            case HEAD -> CompanionInventoryHandler.SLOT_HELMET;
            case CHEST -> CompanionInventoryHandler.SLOT_CHEST;
            case LEGS -> CompanionInventoryHandler.SLOT_LEGS;
            case FEET -> CompanionInventoryHandler.SLOT_BOOTS;
            default -> -1;
        };
        if (armorSlotIdx == -1) return false;

        ItemStack currentArmor = inv.getStackInSlot(armorSlotIdx);
        ItemStack newArmor = inv.getStackInSlot(inventorySlot);

        inv.setStackInSlot(armorSlotIdx, newArmor);
        inv.setStackInSlot(inventorySlot, currentArmor);
        return true;
    }

    /**
     * Auto-equips the best armor pieces and best weapon from inventory.
     * Overloaded method defaulting to automated (non-manual) execution.
     */
    public static void autoEquipBestArmorAndWeapons(CompanionEntity companion) {
        autoEquipBestArmorAndWeapons(companion, false);
    }

    /**
     * Auto-equips the best armor pieces and best weapon from inventory.
     *
     * @param companion     The companion
     * @param manualTrigger True if invoked explicitly by player via button or command
     */
    public static void autoEquipBestArmorAndWeapons(CompanionEntity companion, boolean manualTrigger) {
        if (companion == null) return;
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        boolean changed = false;

        // 1. Armor evaluation
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            int currentArmorSlot = switch (slot) {
                case HEAD -> CompanionInventoryHandler.SLOT_HELMET;
                case CHEST -> CompanionInventoryHandler.SLOT_CHEST;
                case LEGS -> CompanionInventoryHandler.SLOT_LEGS;
                case FEET -> CompanionInventoryHandler.SLOT_BOOTS;
                default -> -1;
            };
            if (currentArmorSlot == -1) continue;

            ItemStack equipped = inv.getStackInSlot(currentArmorSlot);
            int currentDef = (equipped.getItem() instanceof ArmorItem a) ? a.getDefense() : -1;

            int bestSlot = -1;
            int bestDef = currentDef;
            for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
                ItemStack candidate = inv.getStackInSlot(i);
                if (candidate.getItem() instanceof ArmorItem armorItem && armorItem.getEquipmentSlot() == slot) {
                    if (armorItem.getDefense() > bestDef) {
                        bestDef = armorItem.getDefense();
                        bestSlot = i;
                    }
                }
            }

            if (bestSlot != -1) {
                if (equipArmorSafely(companion, bestSlot, slot)) {
                    changed = true;
                }
            }
        }

        // 2. Weapon evaluation (Do not override active tools if locked by active mining/chopping/building task!)
        if (manualTrigger || !com.aicompanion.ai.equipment.ToolSelectionPolicy.isMainHandLocked(companion)) {
            ItemStack currentWeapon = getMainHand(companion);
            boolean inCombat = companion.getTarget() != null && companion.getTarget().isAlive();
            boolean isToolInHand = isPickaxe(currentWeapon) || isShovel(currentWeapon);

            // Do not replace active pickaxes/shovels with a weapon unless manually triggered, in combat, or hand is empty
            if (manualTrigger || inCombat || !isToolInHand) {
                float currentDmg = 0.0f;
                if (currentWeapon.getItem() instanceof SwordItem s) {
                    currentDmg = s.getDamage() + 4.0f;
                } else if (currentWeapon.getItem() instanceof AxeItem a) {
                    currentDmg = a.getAttackDamage() + 3.0f;
                }

                int bestWeaponSlot = findBestWeaponSlot(companion);
                if (bestWeaponSlot != -1) {
                    ItemStack candidateWeapon = inv.getStackInSlot(bestWeaponSlot);
                    float candidateDmg = 0.0f;
                    if (candidateWeapon.getItem() instanceof SwordItem s) {
                        candidateDmg = s.getDamage() + 4.0f;
                    } else if (candidateWeapon.getItem() instanceof AxeItem a) {
                        candidateDmg = a.getAttackDamage() + 3.0f;
                    }

                    if (!isSwordOrWeapon(currentWeapon) || candidateDmg > currentDmg) {
                        if (swapMainHandSafely(companion, bestWeaponSlot)) {
                            changed = true;
                        }
                    }
                }
            }
        }

        // 3. User feedback
        if (manualTrigger) {
            if (changed) {
                com.aicompanion.util.CompanionNotificationManager.notifyImmediate(
                        companion,
                        Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                .withStyle(ChatFormatting.GOLD)
                                .append(Component.literal("Equipo actualizado con las mejores armaduras y armas.").withStyle(ChatFormatting.GREEN))
                );
            } else {
                com.aicompanion.util.CompanionNotificationManager.notifyImmediate(
                        companion,
                        Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                .withStyle(ChatFormatting.GOLD)
                                .append(Component.literal("Ya tiene equipado el mejor equipo disponible.").withStyle(ChatFormatting.WHITE))
                );
            }
        } else if (changed && com.aicompanion.AICompanionConfig.COMMON != null && com.aicompanion.AICompanionConfig.COMMON.notifyAutoEquip.get()) {
            com.aicompanion.util.CompanionNotificationManager.notifyOwner(
                    companion,
                    "auto_equip",
                    Component.literal("[" + companion.getCustomCompanionName() + "] ")
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal("Auto-equipamiento completado con mejor equipamiento.").withStyle(ChatFormatting.WHITE)),
                    200 // Max once every 10 seconds
            );
        }
    }
}
