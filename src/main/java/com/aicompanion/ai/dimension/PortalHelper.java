package com.aicompanion.ai.dimension;

import com.aicompanion.AICompanionMod;
import com.aicompanion.ai.chunk.CompanionChunkManager;
import com.aicompanion.ai.nav.MovementPolicy;
import com.aicompanion.data.CompanionManager;
import com.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/**
 * Enterprise Portal Navigation, Contact Verification & Dimensional Transition Helper.
 * Strictly verifies physical bounding-box intersection with portal blocks (no distance shortcuts),
 * steers NPC physical movement smoothly into portal volume, executes Forge dimension transitions,
 * and rigorously validates the resulting entity.
 */
public class PortalHelper {

    /**
     * Checks whether the companion entity's physical collision bounding box or foot/head position
     * actually intersects at least one active Nether Portal or End Portal block.
     * Uses slight inflation to prevent 1-tick boundary fluctuation glitches.
     */
    public static boolean isEntityInPortal(CompanionEntity companion) {
        if (companion == null || companion.level() == null) return false;
        Level level = companion.level();

        // 1. Direct block check at companion's feet and eye position
        BlockPos feetPos = companion.blockPosition();
        if (isPortalBlock(level, feetPos) || isPortalBlock(level, feetPos.above())) {
            return true;
        }

        // 2. Slightly inflated bounding box check (0.12 blocks grace zone)
        AABB bb = companion.getBoundingBox().inflate(0.12D);
        int minX = Mth.floor(bb.minX);
        int maxX = Mth.floor(bb.maxX);
        int minY = Mth.floor(bb.minY);
        int maxY = Mth.floor(bb.maxY);
        int minZ = Mth.floor(bb.minZ);
        int maxZ = Mth.floor(bb.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (isPortalBlock(level, new BlockPos(x, y, z))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isPortalBlock(Level level, BlockPos pos) {
        if (level == null || pos == null) return false;
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL);
    }

    /**
     * Finds the active portal block the companion is touching or closest to.
     */
    public static BlockPos findCurrentPortalBlock(CompanionEntity companion, BlockPos fallbackPos) {
        if (companion == null || companion.level() == null) return fallbackPos;
        Level level = companion.level();
        BlockPos feet = companion.blockPosition();
        if (isPortalBlock(level, feet)) return feet;
        if (isPortalBlock(level, feet.above())) return feet.above();

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = feet.offset(dx, dy, dz);
                    if (isPortalBlock(level, p)) {
                        double d = companion.distanceToSqr(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D);
                        if (d < bestDist) {
                            bestDist = d;
                            best = p;
                        }
                    }
                }
            }
        }
        return best != null ? best : fallbackPos;
    }

    /**
     * Locates the best available active portal for traveling towards targetDim.
     * Searches spatial memory, episodic POIs, PortalForcer, and immediate loaded chunks.
     */
    public static BlockPos findPortal(CompanionEntity companion, ResourceKey<Level> targetDim) {
        if (companion == null || !(companion.level() instanceof ServerLevel sl)) return null;

        // 1. Direct memory for current or target dimension
        BlockPos directMem = companion.getMemory().getWorldModel().getDimensionalPortal(sl.dimension().location().toString());
        if (isValidPortal(sl, directMem)) return directMem;

        BlockPos retMem = companion.getMemory().getWorldModel().getLocation("return_portal");
        if (isValidPortal(sl, retMem)) return retMem;

        BlockPos depMem = companion.getMemory().getWorldModel().getLocation("departure_portal");
        if (isValidPortal(sl, depMem)) return depMem;

        BlockPos nethMem = companion.getMemory().getWorldModel().getLocation("nether_portal");
        if (isValidPortal(sl, nethMem)) return nethMem;

        // 2. Episodic memory
        var remembered = companion.getMemory().getWorldModel().findClosestPOI(
                sl, companion.blockPosition(),
                targetDim.equals(Level.END) ? Blocks.END_PORTAL : Blocks.NETHER_PORTAL,
                128.0
        );
        if (remembered.isPresent() && isValidPortal(sl, remembered.get())) {
            return remembered.get();
        }

        // 3. PortalForcer search
        try {
            boolean toNether = targetDim.equals(Level.NETHER);
            var found = sl.getPortalForcer().findPortalAround(companion.blockPosition(), toNether, sl.getWorldBorder());
            if (found.isPresent() && isValidPortal(sl, found.get().minCorner)) {
                BlockPos p = found.get().minCorner;
                companion.getMemory().getWorldModel().rememberPOI(p, Blocks.NETHER_PORTAL, sl.getGameTime());
                companion.getMemory().getWorldModel().rememberLocation("return_portal", p);
                return p;
            }
        } catch (Exception ignored) {}

        // 4. Physical scan around companion (radius 32, Y between 30 and 115)
        BlockPos center = companion.blockPosition();
        int minY = Math.max(sl.getMinBuildHeight() + 1, 30);
        int maxY = Math.min(sl.getMaxBuildHeight() - 1, 115);
        for (int dx = -32; dx <= 32; dx += 2) {
            for (int dz = -32; dz <= 32; dz += 2) {
                for (int y = minY; y <= maxY; y += 2) {
                    BlockPos p = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                    if (isValidPortal(sl, p)) {
                        companion.getMemory().getWorldModel().rememberPOI(p, Blocks.NETHER_PORTAL, sl.getGameTime());
                        companion.getMemory().getWorldModel().rememberLocation("return_portal", p);
                        return p;
                    }
                }
            }
        }

        return null;
    }

    private static boolean isValidPortal(ServerLevel sl, BlockPos pos) {
        if (pos == null || !sl.isLoaded(pos)) return false;
        BlockState st = sl.getBlockState(pos);
        return st.is(Blocks.NETHER_PORTAL) || st.is(Blocks.END_PORTAL);
    }

    /**
     * Directs the companion's movement smoothly into the portal opening.
     * When > 3 blocks away: utilizes pathfinding with sprint.
     * When <= 3 blocks away: uses continuous physical steering into portal center, jumping thresholds if necessary.
     */
    public static void guideIntoPortal(CompanionEntity companion, BlockPos portalPos) {
        if (companion == null || portalPos == null) return;
        double targetX = portalPos.getX() + 0.5D;
        double targetY = portalPos.getY();
        double targetZ = portalPos.getZ() + 0.5D;

        double distSq = companion.distanceToSqr(targetX, targetY, targetZ);
        if (distSq > 9.0D) {
            companion.setSprinting(MovementPolicy.sprintingAllowed());
            companion.getNavManager().moveTo(portalPos, MovementPolicy.sprint());
        } else {
            // Close-range physical steering directly into portal opening
            companion.setSprinting(false);
            companion.getLookControl().setLookAt(targetX, targetY + 1.0D, targetZ, 30.0F, 30.0F);
            companion.getMoveControl().setWantedPosition(targetX, targetY, targetZ, 0.75D);

            // Step/jump up if portal frame has an elevated threshold
            if (portalPos.getY() >= companion.getBlockY() && (companion.horizontalCollision || companion.getY() < portalPos.getY() + 0.05)) {
                companion.getJumpControl().jump();
            }
        }
    }

    /**
     * Safely centers the companion within the portal volume and dampens excessive momentum.
     * Prevents the companion from "siguiendo de largo" (overshooting through the portal frame)
     * and falling into lava or hazards on the opposite side of the frame while waiting for transfer.
     */
    public static void holdInPortalCenter(CompanionEntity companion, BlockPos portalPos) {
        if (companion == null) return;
        companion.getNavManager().stop();
        companion.getNavigation().stop();
        companion.setSprinting(false);

        BlockPos centerPos = findCurrentPortalBlock(companion, portalPos);
        if (centerPos == null) centerPos = companion.blockPosition();

        double targetX = centerPos.getX() + 0.5D;
        double targetY = companion.getY();
        double targetZ = centerPos.getZ() + 0.5D;

        // Dampen velocity to prevent sliding through
        net.minecraft.world.phys.Vec3 delta = companion.getDeltaMovement();
        companion.setDeltaMovement(delta.x * 0.15D, delta.y < 0 ? delta.y : 0.0D, delta.z * 0.15D);

        // Gentle pull towards portal block center
        double distFromCenterSq = (companion.getX() - targetX) * (companion.getX() - targetX) + (companion.getZ() - targetZ) * (companion.getZ() - targetZ);
        if (distFromCenterSq > 0.04D) {
            companion.getMoveControl().setWantedPosition(targetX, targetY, targetZ, 0.35D);
        } else {
            companion.getMoveControl().setWantedPosition(targetX, targetY, targetZ, 0.0D);
        }
    }

    /**
     * Executes the dimension transition via Forge's changeDimension API,
     * validates the resulting entity, and registers it with CompanionManager.
     * Returns the validated CompanionEntity in the destination dimension, or null on failure.
     */
    public static CompanionEntity executeTransition(CompanionEntity companion, ServerLevel targetLevel, BlockPos portalPos) {
        if (companion == null || targetLevel == null) return null;
        UUID expectedUUID = companion.getUUID();
        ResourceKey<Level> targetDim = targetLevel.dimension();

        AICompanionMod.LOGGER.info("[ESCLAVO-DIM] Iniciando cambio dimensional estricto para UUID {} hacia {}", expectedUUID, targetDim.location());

        // Reset source portal cooldown so Forge doesn't reject transfer
        companion.resetPortalCooldown();

        // Release old chunk tickets in source dimension before transferring
        CompanionChunkManager.releaseTickets(companion);

        CompanionNetherTeleporter teleporter = new CompanionNetherTeleporter(portalPos, targetDim);
        Entity newEnt = companion.changeDimension(targetLevel, teleporter);

        if (newEnt instanceof CompanionEntity newCompanion
                && !newCompanion.isRemoved()
                && newCompanion.level().dimension().equals(targetDim)) {

            AICompanionMod.LOGGER.info("[ESCLAVO-DIM] Entidad transferida exitosamente a {}: id={}, UUID={}",
                    targetDim.location(), newCompanion.getId(), newCompanion.getUUID());

            // 3 seconds portal cooldown on arrival to prevent bouncing back
            newCompanion.setPortalCooldownTicks(60);

            // Ensure registration in CompanionManager and chunk tickets
            CompanionManager.registerCompanion(newCompanion);
            CompanionChunkManager.updateChunkTickets(newCompanion);

            return newCompanion;
        } else {
            AICompanionMod.LOGGER.error("[ESCLAVO-DIM] ERROR CRÍTICO: La transferencia dimensional a {} no produjo una entidad CompanionEntity válida.", targetDim.location());
            return null;
        }
    }
}
