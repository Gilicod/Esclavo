package com.aicompanion.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SafeSpawnFinder {

    public static class SafeSpawnResult {
        private final boolean success;
        private final Vec3 position;
        private final String reason;

        private SafeSpawnResult(boolean success, Vec3 position, String reason) {
            this.success = success;
            this.position = position;
            this.reason = reason;
        }

        public static SafeSpawnResult success(Vec3 position) {
            return new SafeSpawnResult(true, position, "Posición segura encontrada.");
        }

        public static SafeSpawnResult failure(String reason) {
            return new SafeSpawnResult(false, null, reason);
        }

        public boolean isSuccess() {
            return success;
        }

        public Vec3 getPosition() {
            return position;
        }

        public String getReason() {
            return reason;
        }

        public Optional<Vec3> toOptional() {
            return success && position != null ? Optional.of(position) : Optional.empty();
        }
    }

    /**
     * Finds a safe spawn position around a reference entity (such as a Player) at exactly 1-2 blocks away.
     * Never returns the center position occupied by referenceEntity, completely preventing entity collisions and pushing.
     * Prefers positions directly in front of the reference entity in its current look direction.
     */
    public static SafeSpawnResult findSafeSpawnNearEntity(ServerLevel level, LivingEntity referenceEntity, Entity entityToSpawn) {
        if (level == null || referenceEntity == null) {
            return SafeSpawnResult.failure("Nivel o entidad de referencia nulos.");
        }

        BlockPos center = referenceEntity.blockPosition();
        Vec3 look = referenceEntity.getLookAngle();
        double lookX = look.x;
        double lookZ = look.z;
        double lookLen = Math.sqrt(lookX * lookX + lookZ * lookZ);
        if (lookLen > 0.001) {
            lookX /= lookLen;
            lookZ /= lookLen;
        } else {
            lookX = 0;
            lookZ = 1;
        }

        // Ring 1: 8 perimeter blocks at distance 1 around player
        List<int[]> candidates = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue; // STRICTLY EXCLUDE player's exact block!
                candidates.add(new int[]{dx, dz});
            }
        }

        final double lx = lookX;
        final double lz = lookZ;
        // Sort so the block the player is facing is checked first
        candidates.sort((a, b) -> {
            double dotA = a[0] * lx + a[1] * lz;
            double dotB = b[0] * lx + b[1] * lz;
            return Double.compare(dotB, dotA);
        });

        int[] dyOffsets = {0, 1, -1, 2, -2};
        AABB refBoundingBox = referenceEntity.getBoundingBox();

        // 1. Try preferred ring 1 positions (1 block away)
        for (int[] offset : candidates) {
            for (int dy : dyOffsets) {
                BlockPos feet = center.offset(offset[0], dy, offset[1]);
                if (isLocationSafe(level, feet, entityToSpawn)) {
                    AABB spawnBox = new AABB(feet.getX() + 0.1, feet.getY(), feet.getZ() + 0.1,
                                             feet.getX() + 0.9, feet.getY() + 1.8, feet.getZ() + 0.9);
                    if (!refBoundingBox.intersects(spawnBox)) {
                        return SafeSpawnResult.success(new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5));
                    }
                }
            }
        }

        // 2. Try ring 2 (2 blocks away)
        List<int[]> ring2Candidates = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) != 2 && Math.abs(dz) != 2) continue;
                ring2Candidates.add(new int[]{dx, dz});
            }
        }
        ring2Candidates.sort((a, b) -> {
            double dotA = a[0] * lx + a[1] * lz;
            double dotB = b[0] * lx + b[1] * lz;
            return Double.compare(dotB, dotA);
        });

        for (int[] offset : ring2Candidates) {
            for (int dy : dyOffsets) {
                BlockPos feet = center.offset(offset[0], dy, offset[1]);
                if (isLocationSafe(level, feet, entityToSpawn)) {
                    AABB spawnBox = new AABB(feet.getX() + 0.1, feet.getY(), feet.getZ() + 0.1,
                                             feet.getX() + 0.9, feet.getY() + 1.8, feet.getZ() + 0.9);
                    if (!refBoundingBox.intersects(spawnBox)) {
                        return SafeSpawnResult.success(new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5));
                    }
                }
            }
        }

        // 3. Fallback to general perimeter search (rings 3 to 12)
        return findSafeSpawnResult(level, center, entityToSpawn, false);
    }

    /**
     * Orients an entity to face directly toward a target living entity.
     */
    public static void faceEntity(Entity entity, LivingEntity target) {
        if (entity == null || target == null) return;
        double diffX = target.getX() - entity.getX();
        double diffZ = target.getZ() - entity.getZ();
        float yaw = (float) (net.minecraft.util.Mth.atan2(diffZ, diffX) * (180.0D / Math.PI)) - 90.0F;
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
        if (entity instanceof LivingEntity living) {
            living.setYBodyRot(yaw);
        }
    }

    /**
     * Searches progressively for a verified safe spawn location near center.
     * Returns a SafeSpawnResult indicating success or failure. Never forces an unsafe fallback.
     */
    public static SafeSpawnResult findSafeSpawnResult(ServerLevel level, BlockPos center, Entity entity) {
        return findSafeSpawnResult(level, center, entity, false);
    }

    public static SafeSpawnResult findSafeSpawnResult(ServerLevel level, BlockPos center, Entity entity, boolean allowCenter) {
        if (level == null || center == null) {
            return SafeSpawnResult.failure("Nivel o posición de búsqueda nulos.");
        }

        // 1. Only check center position if explicitly allowed (e.g. non-player targets)
        if (allowCenter && isLocationSafe(level, center, entity)) {
            return SafeSpawnResult.success(new Vec3(center.getX() + 0.5, center.getY(), center.getZ() + 0.5));
        }

        // 2. Progressive perimeter search: rings 1 to 6
        int[] dyOffsets = {0, 1, -1, 2, -2, 3, -3, 4, -4};
        for (int r = 1; r <= 6; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue; // Check ring perimeter

                    for (int dy : dyOffsets) {
                        BlockPos feet = center.offset(dx, dy, dz);
                        if (isLocationSafe(level, feet, entity)) {
                            return SafeSpawnResult.success(new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5));
                        }
                    }
                }
            }
        }

        // 3. Extended search: rings 7 to 12
        int[] dyOffsetsWide = {0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5};
        for (int r = 7; r <= 12; r++) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (Math.abs(dx) < r - 1 && Math.abs(dz) < r - 1) continue;

                    for (int dy : dyOffsetsWide) {
                        BlockPos feet = center.offset(dx, dy, dz);
                        if (isLocationSafe(level, feet, entity)) {
                            return SafeSpawnResult.success(new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5));
                        }
                    }
                }
            }
        }

        // 4. Surface check via Heightmap around center
        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 2) {
                BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(dx, 0, dz));
                if (isLocationSafe(level, surface, entity)) {
                    return SafeSpawnResult.success(new Vec3(surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5));
                }
            }
        }

        // NO UNSAFE FALLBACK: return failure so caller can inform user without suffocating NPC
        return SafeSpawnResult.failure("No se encontró ninguna posición segura libre de peligros o colisiones alrededor de " + center.toShortString());
    }

    public static Vec3 findSafeSpawn(ServerLevel level, BlockPos center, Entity entity) {
        SafeSpawnResult res = findSafeSpawnResult(level, center, entity);
        return res.isSuccess() ? res.getPosition() : null;
    }

    public static Vec3 findSafeSpawn(ServerLevel level, Vec3 center, Entity entity) {
        return findSafeSpawn(level, BlockPos.containing(center), entity);
    }

    public static boolean isLocationSafe(ServerLevel level, BlockPos feetPos, Entity entity) {
        if (!level.getWorldBorder().isWithinBounds(feetPos)) return false;
        if (feetPos.getY() <= level.getMinBuildHeight() + 1 || feetPos.getY() >= level.getMaxBuildHeight() - 2) return false;

        BlockPos headPos = feetPos.above();
        BlockPos groundPos = feetPos.below();

        BlockState feetState = level.getBlockState(feetPos);
        BlockState headState = level.getBlockState(headPos);
        BlockState groundState = level.getBlockState(groundPos);

        // 1. Ground below must be sturdy/solid render UP and safe
        if (!groundState.isFaceSturdy(level, groundPos, Direction.UP) && !groundState.isSolidRender(level, groundPos)) {
            return false;
        }
        if (isHazardous(groundState) || isHazardous(feetState) || isHazardous(headState)) {
            return false;
        }

        // 2. Feet and head must not be solid blocks
        if (!feetState.getCollisionShape(level, feetPos).isEmpty()) {
            return false;
        }
        if (!headState.getCollisionShape(level, headPos).isEmpty()) {
            return false;
        }

        // 3. Fluid checks (no lava, no fire, no powder snow)
        if (feetState.getFluidState().is(Fluids.LAVA) || feetState.getFluidState().is(Fluids.FLOWING_LAVA) ||
            headState.getFluidState().is(Fluids.LAVA) || headState.getFluidState().is(Fluids.FLOWING_LAVA)) {
            return false;
        }

        // 4. Entity AABB collision check
        if (entity != null) {
            AABB boundingBox = entity.getType().getAABB(feetPos.getX() + 0.5, feetPos.getY(), feetPos.getZ() + 0.5);
            if (!level.noCollision(entity, boundingBox)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isHazardous(BlockState state) {
        if (state.isAir()) return false;
        if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) return true;
        if (state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)) return true;
        if (state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.WITHER_ROSE)) return true;
        if (state.is(Blocks.POWDER_SNOW)) return true;
        if (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)) return true;
        return false;
    }
}
