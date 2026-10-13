package com.aicompanion.ai.tasks;

import com.aicompanion.AICompanionMod;
import com.aicompanion.ai.CompanionTask;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.inventory.CompanionInventoryHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Autonomous Healing and Nutrition Task for Esclavo (Pilar 4).
 * Injected after surviving a critical combat/flee encounter.
 * Consumes food from inventory until health is restored or food is exhausted,
 * before popping the LIFO stack to resume the civil mission.
 */
public class HealTask extends CompanionTask {

    private int eatCooldown = 0;
    private int eatAttempts = 0;

    public HealTask(CompanionEntity companion) {
        super(companion);
        this.stage = "HEALING";
        this.substate = "Consumiendo alimentos para regenerar salud...";
    }

    @Override
    public void start() {
        AICompanionMod.LOGGER.info("[ESCLAVO-SURVIVAL] HealTask started (HP: {}/{})", companion.getHealth(), companion.getMaxHealth());
        eatCooldown = 10;
        eatAttempts = 0;
    }

    @Override
    public void tick() {
        if (paused) return;

        // Health fully restored check
        if (companion.getHealth() >= companion.getMaxHealth() - 0.5f) {
            complete = true;
            stage = "COMPLETE";
            substate = "Salud completamente regenerada";
            AICompanionMod.LOGGER.info("[ESCLAVO-SURVIVAL] HealTask completed: Health restored to {} HP", companion.getHealth());
            return;
        }

        if (eatCooldown > 0) {
            eatCooldown--;
            return;
        }

        boolean hasFood = hasEdibleFood();
        if (!hasFood || eatAttempts >= 5) {
            // 1.13.9: sin comida -> descansar quieto y regenerar de forma natural (1 HP/s) hasta el 70 % o 60 s.
            if (companion.getHealth() < companion.getMaxHealth() * 0.7f && restTicks < MAX_REST_TICKS) {
                if (restTicks == 0) {
                    companion.getNavManager().stop();
                    AICompanionMod.LOGGER.info("[ESCLAVO-SURVIVAL] HealTask: sin comida, descansando para regenerar (HP {}/{})",
                            companion.getHealth(), companion.getMaxHealth());
                }
                restTicks++;
                stage = "RESTING";
                substate = "Descansando para recuperar salud (sin comida)...";
                return;
            }
            complete = true;
            stage = "COMPLETE";
            substate = hasFood ? "Recuperación completada" : "Sin comida en inventario";
            AICompanionMod.LOGGER.info("[ESCLAVO-SURVIVAL] HealTask concluded: (HasFood={}, Attempts={}, RestTicks={}, HP={})",
                    hasFood, eatAttempts, restTicks, companion.getHealth());
            return;
        }

        // Execute eating
        companion.tryEatFood();
        eatAttempts++;
        eatCooldown = 25; // Eating cycle ticks
    }

    private int restTicks = 0;
    private static final int MAX_REST_TICKS = 1200;

    private boolean hasEdibleFood() {
        CompanionInventoryHandler inv = companion.getInventoryHandler();
        for (int i = 0; i < CompanionInventoryHandler.MAIN_SLOTS; i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem().isEdible()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isStationaryWorking() {
        return true;
    }

    @Override
    public String getDescription() {
        return "Regenerar salud";
    }

    @Override
    public String getProgress() {
        return String.format("%.1f/%.1f HP (%s)", companion.getHealth(), companion.getMaxHealth(), stage);
    }

    @Override
    public String getTaskType() {
        return "HEAL";
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("EatAttempts", eatAttempts);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        this.eatAttempts = tag.getInt("EatAttempts");
    }
}
