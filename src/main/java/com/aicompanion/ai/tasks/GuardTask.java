package com.aicompanion.ai.tasks;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import com.aicompanion.inventory.EquipmentManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;

/**
 * Autonomous perimeter sentry and guard daemon for Esclavo.
 * Patrols within a specified radius of a designated center or home base,
 * engaging hostile monsters that trespass the perimeter, and returning
 * to post once threats are eliminated.
 */
public class GuardTask extends CompanionTask {
    private BlockPos guardCenter;
    private int radius = 16;
    private LivingEntity targetEnemy = null;
    private int attackCooldown = 0;
    private int scanCooldown = 0;
    private int monstersDefeated = 0;

    public GuardTask(CompanionEntity companion) {
        this(companion, 16);
    }

    public GuardTask(CompanionEntity companion, int radius) {
        super(companion);
        this.radius = Math.max(6, Math.min(48, radius));
        this.guardCenter = companion.blockPosition();
    }

    @Override
    public void start() {
        if (guardCenter == null) {
            guardCenter = companion.getHomePos() != null ? companion.getHomePos() : companion.blockPosition();
        }
        companion.setCurrentState(CompanionState.GUARDING);
    }

    @Override
    public void tick() {
        if (paused || complete) return;
        if (!(companion.level() instanceof ServerLevel sl)) return;

        companion.setCurrentState(CompanionState.GUARDING);

        // 1. If currently engaging an enemy
        if (targetEnemy != null) {
            if (!targetEnemy.isAlive() || targetEnemy.isDeadOrDying() || targetEnemy.isRemoved()) {
                targetEnemy = null;
                monstersDefeated++;
                return;
            }

            // Check if enemy fled beyond boundary
            double distFromCenterSq = targetEnemy.blockPosition().distSqr(guardCenter);
            if (distFromCenterSq > (radius * 1.5) * (radius * 1.5)) {
                targetEnemy = null; // Disengage, out of patrol bounds
                return;
            }

            // Auto-equip best weapon and armor
            EquipmentManager.autoEquipBestArmorAndWeapons(companion, true);

            double distToEnemySq = companion.distanceToSqr(targetEnemy);
            if (distToEnemySq <= 10.5) {
                companion.getLookControl().setLookAt(targetEnemy, 60.0F, 60.0F);
                if (--attackCooldown <= 0) {
                    attackCooldown = 12;
                    companion.swing(InteractionHand.MAIN_HAND);
                    companion.doHurtTarget(targetEnemy);
                }
            }
            if (distToEnemySq > 3.24) {
                companion.getNavManager().navigateTo(new com.aicompanion.internal.baritone.goals.GoalEntity(targetEnemy, 0.8, 2.0), com.aicompanion.ai.nav.MovementPolicy.sprint());
            } else if (distToEnemySq <= 1.5) {
                companion.getNavManager().stop();
            }
            return;
        }

        // 2. Periodic scan for trespassing hostiles
        if (--scanCooldown <= 0) {
            scanCooldown = 20; // scan every second
            AABB searchBox = new AABB(guardCenter).inflate(radius, 8, radius);
            List<Monster> hostiles = sl.getEntitiesOfClass(Monster.class, searchBox,
                    m -> m.isAlive() && !m.isDeadOrDying() && !(m instanceof net.minecraft.world.entity.monster.Creeper c && c.getSwellDir() > 0));

            if (!hostiles.isEmpty() && companion.getSettings().isAllowCombat()) {
                hostiles.sort(Comparator.comparingDouble(m -> companion.distanceToSqr(m)));
                targetEnemy = hostiles.get(0);
                return;
            }

            // 3. If idle and far from guard post, return to center
            if (companion.blockPosition().distSqr(guardCenter) > 25.0) {
                companion.getNavManager().moveTo(guardCenter, 1.0);
            }
        }
    }

    @Override
    public String getDescription() {
        return "Vigilar y patrullar perímetro (Radio: " + radius + "m)";
    }

    @Override
    public String getProgress() {
        return targetEnemy != null ? "Combatiendo amenaza: " + targetEnemy.getName().getString()
                : "Perímetro despejado (Bajas: " + monstersDefeated + ")";
    }

    @Override
    public String getTaskType() {
        return "GUARD";
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (guardCenter != null) tag.put("GuardCenter", NbtUtils.writeBlockPos(guardCenter));
        tag.putInt("Radius", radius);
        tag.putInt("MonstersDefeated", monstersDefeated);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        if (tag.contains("GuardCenter")) this.guardCenter = NbtUtils.readBlockPos(tag.getCompound("GuardCenter"));
        if (tag.contains("Radius")) this.radius = tag.getInt("Radius");
        if (tag.contains("MonstersDefeated")) this.monstersDefeated = tag.getInt("MonstersDefeated");
    }
}
