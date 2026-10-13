package com.aicompanion.ai.tasks;

import com.aicompanion.AICompanionMod;
import com.aicompanion.ai.CompanionTask;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import com.aicompanion.internal.baritone.goals.GoalEntity;
import com.aicompanion.inventory.EquipmentManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

/**
 * Autonomous Combat Emergency Task for Esclavo (Pilar 4).
 * Injected as an interrupt when a critical threat appears and Esclavo is armed and healthy.
 * Auto-equips weapon, approaches target, attacks in sync, and executes tactical retreats
 * against swelling Creepers.
 */
public class FightTask extends CompanionTask {

    private LivingEntity target;
    private int attackCooldown = 0;
    private int repathCooldown = 0;
    private int fightTicks = 0;

    public FightTask(CompanionEntity companion, LivingEntity target) {
        super(companion);
        this.target = target;
        this.stage = "ENGAGING";
        this.substate = "Combatiendo amenaza (" + (target != null ? target.getName().getString() : "Desconocido") + ")";
    }

    @Override
    public void start() {
        companion.setCurrentState(CompanionState.FIGHTING);
        ensureBestWeaponEquipped();
        logEvent("Iniciando autodefensa contra " + (target != null ? target.getName().getString() : "enemigo"));
        AICompanionMod.LOGGER.info("[ESCLAVO-COMBAT] FightTask started against {}", target != null ? target.getName().getString() : "null");
    }

    public LivingEntity getTarget() {
        return target;
    }

    public void setTarget(LivingEntity target) {
        this.target = target;
        this.stage = "ENGAGING";
        this.substate = "Combatiendo amenaza (" + (target != null ? target.getName().getString() : "Desconocido") + ")";
    }

    @Override
    public void tick() {
        if (paused) return;
        fightTicks++;

        if (target == null || !target.isAlive() || target.isRemoved()) {
            LivingEntity nextThreat = findNextImmediateThreat();
            if (nextThreat != null) {
                logEvent("Objetivo eliminado. Enfrentando siguiente amenaza inmediata: " + nextThreat.getName().getString());
                this.target = nextThreat;
                this.stage = "ENGAGING";
                this.substate = "Combatiendo amenaza secundaria (" + nextThreat.getName().getString() + ")";
                return;
            }

            complete = true;
            stage = "COMPLETE";
            substate = "Amenaza neutralizada con éxito";
            companion.getNavManager().stop();
            companion.incrementMobsKilled(1);
            companion.addCombatXP(15);
            logEvent("Amenaza eliminada. Zona despejada.");
            AICompanionMod.LOGGER.info("[ESCLAVO-COMBAT] FightTask: All immediate threats eliminated!");
            return;
        }

        ensureBestWeaponEquipped();
        companion.setCurrentState(CompanionState.FIGHTING);
        companion.getLookControl().setLookAt(target, 60.0F, 60.0F);

        double distSq = companion.distanceToSqr(target);

        // Tactical retreat against swelling Creeper
        if (target instanceof Creeper creeper && creeper.getSwellDir() > 0) {
            Vec3 away = companion.position().subtract(creeper.position()).normalize();
            companion.setDeltaMovement(away.x * 0.45D, 0.18D, away.z * 0.45D);
            companion.hasImpulse = true;
            companion.getNavManager().moveTo(companion.blockPosition().offset((int)(away.x * 10), 0, (int)(away.z * 10)), com.aicompanion.ai.nav.MovementPolicy.sprint());
            stage = "TACTICAL_RETREAT";
            substate = "Retroceso táctico ante Creeper a punto de detonar!";
            return;
        }

        // Navigation to target with sprint
        if (distSq > 9.0) {
            stage = "APPROACHING";
            substate = "Cargando contra " + target.getName().getString();
            if (--repathCooldown <= 0 || companion.getNavManager().isDone()) {
                companion.getNavManager().navigateTo(new GoalEntity(target, 1.2, 2.5), com.aicompanion.ai.nav.MovementPolicy.sprint());
                repathCooldown = 10;
            }
        } else {
            stage = "ATTACKING";
            substate = "Golpeando a " + target.getName().getString();
            companion.getNavManager().stop();

            if (attackCooldown <= 0) {
                companion.swing(InteractionHand.MAIN_HAND, true);
                companion.doHurtTarget(target);
                attackCooldown = 12; // 0.6s attack cadence
                markProgress();
            }
        }

        if (attackCooldown > 0) {
            attackCooldown--;
        }

        // Emergency abort or tactical retreat against overpowering threat / critical health
        boolean isOverpowering = com.aicompanion.ai.perception.RiskEvaluator.isOverpoweringThreat(target, companion);
        float retreatThreshold = isOverpowering ? (companion.getMaxHealth() * 0.6F) :
                ((target != null && target.getMaxHealth() >= 40.0F) ? (companion.getMaxHealth() * 0.5F) : Math.max(8.0F, companion.getMaxHealth() * 0.4F));

        if (companion.getHealth() <= retreatThreshold || (isOverpowering && companion.getHealth() <= companion.getMaxHealth() * 0.8F)) {
            AICompanionMod.LOGGER.warn("[ESCLAVO-COMBAT] Tactical retreat triggered against {} (Health: {}/{}, Overpowering: {})",
                    target.getName().getString(), companion.getHealth(), companion.getMaxHealth(), isOverpowering);
            logEvent(isOverpowering ? "Enemigo demasiado poderoso (" + target.getName().getString() + "). ¡Retirada táctica!" : "Salud baja en combate (" + (int)companion.getHealth() + " HP). Retirada táctica...");
            Vec3 center = target.position();
            companion.getCompanionBrain().recordDangerZone(center);
            FleeTask flee = new FleeTask(companion, center, 24.0);
            flee.setMissionId(this.getMissionId());
            this.complete = true;
            this.companion.getCompanionBrain().getTaskManager().interruptWithEmergency(flee);
            return;
        }
    }

    private LivingEntity findNextImmediateThreat() {
        if (companion == null || !companion.isAlive()) return null;
        java.util.List<LivingEntity> nearby = companion.level().getEntitiesOfClass(
                LivingEntity.class,
                companion.getBoundingBox().inflate(8.0, 4.0, 8.0),
                e -> com.aicompanion.ai.perception.RiskEvaluator.isHostileThreat(e, companion)
        );
        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (LivingEntity e : nearby) {
            double d = companion.distanceToSqr(e);
            if (d < closestDist) {
                closestDist = d;
                closest = e;
            }
        }
        return closest;
    }

    private void ensureBestWeaponEquipped() {
        if (!EquipmentManager.hasWeaponEquipped(companion)) {
            int weaponSlot = EquipmentManager.findBestWeaponSlot(companion);
            if (weaponSlot != -1) {
                EquipmentManager.swapMainHandSafely(companion, weaponSlot);
            }
        }
    }

    @Override
    public void stop() {
        companion.getNavManager().stop();
    }

    @Override
    public String getDescription() {
        return "Defender contra " + (target != null ? target.getName().getString() : "enemigo");
    }

    @Override
    public String getProgress() {
        return target != null && target.isAlive()
                ? String.format("%.1f/%.1f HP (%s)", target.getHealth(), target.getMaxHealth(), stage)
                : "Neutralizado";
    }

    @Override
    public String getTaskType() {
        return "FIGHT";
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("FightTicks", fightTicks);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        this.fightTicks = tag.getInt("FightTicks");
    }
}
