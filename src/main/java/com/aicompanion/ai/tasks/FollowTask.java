package com.aicompanion.ai.tasks;

import com.aicompanion.AICompanionMod;
import com.aicompanion.ai.CompanionTask;
import com.aicompanion.ai.chunk.CompanionChunkManager;
import com.aicompanion.ai.dimension.PortalHelper;
import com.aicompanion.ai.nav.MovementPolicy;
import com.aicompanion.data.CompanionManager;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import com.aicompanion.internal.baritone.goals.GoalEntity;
import com.aicompanion.util.SafeSpawnFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;

public class FollowTask extends CompanionTask {
    private int repathCooldown = 0;
    private BlockPos portalPos = null;
    private int portalWaitTicks = 0;
    private int portalSearchCooldown = 0;
    private int strandedTicks = 0;

    public FollowTask(CompanionEntity companion) {
        super(companion);
        this.stage = "FOLLOWING";
        this.substate = "Siguiendo al propietario";
    }

    @Override
    public void start() {
        companion.setCurrentState(CompanionState.FOLLOWING);
        markProgress();
        this.portalPos = null;
        this.portalWaitTicks = 0;
        this.portalSearchCooldown = 0;
        this.strandedTicks = 0;
    }

    @Override
    public void tick() {
        if (paused) return;
        Player owner = companion.getOwnerPlayer();
        if (owner == null || !owner.isAlive() || owner.isDeadOrDying()) {
            companion.getNavManager().stop();
            companion.setCurrentState(CompanionState.WAITING);
            this.substate = "Esperando al propietario";
            return;
        }

        // Cross-dimension following when owner is in a different dimension
        if (!companion.level().dimension().equals(owner.level().dimension())) {
            tickCrossDimensionFollow(owner);
            return;
        }

        // Reset dimensional follow state once reunited in same dimension
        strandedTicks = 0;
        portalPos = null;
        portalWaitTicks = 0;

        // Always face and look at owner
        companion.getLookControl().setLookAt(owner, 15.0F, (float) companion.getMaxHeadXRot());

        double distSq = companion.distanceToSqr(owner);

        // Catch-up teleport if falling too far behind (> 36 blocks)
        if (distSq > 36.0 * 36.0 && companion.level() instanceof ServerLevel sl) {
            SafeSpawnFinder.SafeSpawnResult res = SafeSpawnFinder.findSafeSpawnNearEntity(sl, owner, companion);
            if (res.isSuccess()) {
                Vec3 safe = res.getPosition();
                companion.teleportTo(safe.x, safe.y, safe.z);
                SafeSpawnFinder.faceEntity(companion, owner);
                companion.getNavManager().stop();
                repathCooldown = 0;
                return;
            }
        }

        companion.setCurrentState(CompanionState.FOLLOWING);
        this.substate = "Siguiendo al propietario a pie";

        // Strictly navigate on foot when in range
        if (distSq > 9.0) { // More than 3 blocks away: run to owner
            double speed = MovementPolicy.sprint();
            companion.setSprinting(MovementPolicy.sprintingAllowed());
            if (--repathCooldown <= 0) {
                repathCooldown = 15;
                companion.getNavManager().navigateTo(new GoalEntity(owner, 2.5, 4.5), speed);
            }
        } else if (distSq <= 6.25) { // Within 2.5 blocks: comfortably reached owner
            companion.setSprinting(false);
            companion.getNavManager().stop();
            repathCooldown = 0;
        }
    }

    private void tickCrossDimensionFollow(Player owner) {
        companion.setCurrentState(CompanionState.TRAVELLING_DIMENSION);
        if (!(companion.level() instanceof ServerLevel sl)) return;

        strandedTicks++;

        // 1. Search for portal every 20 ticks if null or invalid
        if (portalPos == null || --portalSearchCooldown <= 0) {
            portalSearchCooldown = 20;
            portalPos = PortalHelper.findPortal(companion, owner.level().dimension());
        }

        // 2. Fallback: if stranded in other dimension for > 20s without an accessible portal, teleport to owner
        if (strandedTicks > 20 * 20 && owner.level() instanceof ServerLevel targetLevel) {
            AICompanionMod.LOGGER.info("[ESCLAVO-FOLLOW] Interdimensional catch-up teleport to owner in {}", targetLevel.dimension().location());
            SafeSpawnFinder.SafeSpawnResult res = SafeSpawnFinder.findSafeSpawnNearEntity(targetLevel, owner, companion);
            if (res.isSuccess()) {
                Vec3 safe = res.getPosition();
                net.minecraftforge.common.util.ITeleporter directTeleporter = new net.minecraftforge.common.util.ITeleporter() {
                    @Override
                    public PortalInfo getPortalInfo(net.minecraft.world.entity.Entity entity, ServerLevel destWorld, java.util.function.Function<ServerLevel, PortalInfo> defaultPortalInfo) {
                        return new PortalInfo(safe, Vec3.ZERO, owner.getYRot(), 0.0F);
                    }
                };
                CompanionEntity transferred = (CompanionEntity) companion.changeDimension(targetLevel, directTeleporter);
                if (transferred != null) {
                    CompanionManager.registerCompanion(transferred);
                    CompanionChunkManager.updateChunkTickets(transferred);
                    transferred.setCurrentState(CompanionState.FOLLOWING);
                    transferred.getNavManager().stop();
                    transferred.resetPortalCooldown();
                    return;
                }
            }
        }

        if (portalPos == null) {
            companion.getNavManager().stop();
            companion.setCurrentState(CompanionState.WAITING);
            this.substate = "Buscando portal para seguir al propietario...";
            return;
        }

        boolean inside = PortalHelper.isEntityInPortal(companion);
        if (!inside) {
            portalWaitTicks = Math.max(0, portalWaitTicks - 1);
            double distSq = companion.blockPosition().distSqr(portalPos);
            this.substate = "Caminando al portal para seguir al propietario (" + (int) Math.sqrt(distSq) + "m)";

            if (distSq <= 9.0D) {
                // Must steer every tick for smooth fluid entry into the portal
                repathCooldown = 0;
                PortalHelper.guideIntoPortal(companion, portalPos);
            } else {
                if (--repathCooldown <= 0) {
                    repathCooldown = 15;
                    PortalHelper.guideIntoPortal(companion, portalPos);
                }
            }
        } else {
            portalWaitTicks++;
            PortalHelper.holdInPortalCenter(companion, portalPos);
            this.substate = "Cruzando el portal para seguir al propietario... (" + portalWaitTicks + "/10)";

            if (portalWaitTicks >= 10) {
                if (owner.level() instanceof ServerLevel targetLevel) {
                    AICompanionMod.LOGGER.info("[ESCLAVO-FOLLOW] Crossing portal to follow owner in {}", targetLevel.dimension().location());
                    CompanionEntity newCompanion = PortalHelper.executeTransition(companion, targetLevel, portalPos);
                    if (newCompanion != null) {
                        portalPos = null;
                        portalWaitTicks = 0;
                        strandedTicks = 0;
                    } else {
                        portalWaitTicks = 0;
                    }
                }
            }
        }
    }

    @Override
    public void stop() {
        companion.setSprinting(false);
        companion.getNavManager().stop();
    }

    @Override
    public String getDescription() { return "Seguir al propietario"; }
    @Override
    public String getProgress() { return "Siguiendo"; }
    @Override
    public String getTaskType() { return "FOLLOW"; }
    @Override
    public CompoundTag save() { return new CompoundTag(); }
    @Override
    public void load(CompoundTag tag) {}
}
