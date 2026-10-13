package com.aicompanion.ai.mining;

import com.aicompanion.ai.perception.RiskEvaluator;
import com.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class SearchRegionManager {
    private final Map<String, Set<BlockPos>> knownVeins = new HashMap<>();
    private final Set<BlockPos> blacklistedPositions = new HashSet<>();
    private final Map<BlockPos, Long> blockBlacklistExpires = new HashMap<>();
    private final List<BlacklistedArea> blacklistedAreas = new ArrayList<>();
    private int currentRadiusIndex = 0;
    private static final int[] RADII = {12, 24, 36, 48};
    private boolean inhibited = false;

    private static final int MAX_BLOCKS_PER_TICK = 600;

    private static final class BlacklistedArea {
        final BlockPos center;
        final double radiusSq;
        final long expiresAt;

        BlacklistedArea(BlockPos center, double radius, long expiresAt) {
            this.center = center.immutable();
            this.radiusSq = radius * radius;
            this.expiresAt = expiresAt;
        }
    }

    public void clearCache() {
        knownVeins.clear();
        blacklistedPositions.clear();
        blockBlacklistExpires.clear();
        blacklistedAreas.clear();
        currentRadiusIndex = 0;
        inhibited = false;
    }

    public void setInhibited(boolean inhibited) {
        this.inhibited = inhibited;
        if (inhibited) {
            com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-SPATIAL] SearchRegionManager INHIBITED. Global scanning silenced.");
        }
    }

    public boolean isInhibited() {
        return inhibited;
    }

    public void blacklistBlock(BlockPos pos) {
        blacklistBlock(pos, 600L, 0L);
    }

    public void blacklistBlock(BlockPos pos, long durationTicks, long gameTime) {
        if (pos != null) {
            BlockPos imm = pos.immutable();
            blacklistedPositions.add(imm);
            if (gameTime > 0L) {
                blockBlacklistExpires.put(imm, gameTime + durationTicks);
            }
            if (blacklistedPositions.size() > 500) {
                Iterator<BlockPos> it = blacklistedPositions.iterator();
                for (int i = 0; i < 100 && it.hasNext(); i++) {
                    BlockPos rem = it.next();
                    it.remove();
                    blockBlacklistExpires.remove(rem);
                }
            }
            for (Set<BlockPos> set : knownVeins.values()) {
                set.remove(pos);
            }
        }
    }

    public void blacklistArea(BlockPos center, double radius, long gameTime, long durationTicks) {
        if (center == null) return;
        blacklistedAreas.removeIf(a -> a.expiresAt < gameTime);
        blacklistedAreas.add(new BlacklistedArea(center, radius, gameTime + durationTicks));
        double rSq = radius * radius;
        for (Set<BlockPos> set : knownVeins.values()) {
            set.removeIf(p -> p.distSqr(center) <= rSq);
        }
    }

    public void blacklistArea(Vec3 center, double radius, long gameTime, long durationTicks) {
        if (center != null) {
            blacklistArea(BlockPos.containing(center), radius, gameTime, durationTicks);
        }
    }

    public boolean isBlacklisted(BlockPos pos) {
        return isBlacklisted(pos, 0L);
    }

    public boolean isBlacklisted(BlockPos pos, long gameTime) {
        if (pos == null) return false;
        Long exp = blockBlacklistExpires.get(pos);
        if (exp != null) {
            if (gameTime > 0L && gameTime > exp) {
                blockBlacklistExpires.remove(pos);
                blacklistedPositions.remove(pos);
            } else {
                return true;
            }
        } else if (blacklistedPositions.contains(pos)) {
            return true;
        }
        for (BlacklistedArea a : blacklistedAreas) {
            if ((gameTime == 0L || gameTime <= a.expiresAt) && pos.distSqr(a.center) <= a.radiusSq) {
                return true;
            }
        }
        return false;
    }

    public static boolean isTargetTooDeep(BlockPos origin, MiningTarget target) {
        if (target == null) return false;
        return origin.getY() > (target.getMaxRecommendedY() + 16);
    }

    public static boolean isExposedToAir(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            BlockPos rel = pos.relative(dir);
            if (level.isLoaded(rel) && level.getBlockState(rel).isAir()) return true;
        }
        return false;
    }

    public static boolean hasHostileThreatsNear(ServerLevel level, BlockPos p, CompanionEntity companion, double radius) {
        if (level == null || p == null) return false;
        AABB box = new AABB(p).inflate(radius);
        List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, box, e ->
                e.isAlive() && (RiskEvaluator.isHostileThreat(e, companion) || RiskEvaluator.isInherentlyHostile(e))
        );
        return !list.isEmpty();
    }

    public static boolean isPhysicallyAccessible(ServerLevel level, BlockPos p, BlockPos origin) {
        if (p == null || origin == null || level == null) return false;
        int dy = p.getY() - origin.getY();
        // At or below origin, or within normal step/jump reach (<= 3 blocks above origin)
        if (dy <= 3) return true;
        // If target is inside solid rock (not exposed to air), companion can dig a tunnel to it
        if (!isExposedToAir(level, p)) return true;
        // If target is exposed to air and high up (dy > 3):
        // It MUST have a walkable solid surface beneath/around it within interaction reach (1 to 4 blocks down).
        for (int yOff = 1; yOff <= 4; yOff++) {
            BlockPos below = p.below(yOff);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos checkPos = below.offset(dx, 0, dz);
                    if (!level.isLoaded(checkPos)) continue;
                    BlockState state = level.getBlockState(checkPos);
                    if (state.isSolidRender(level, checkPos) && !state.is(net.minecraft.world.level.block.Blocks.LAVA)) {
                        BlockState head1 = level.getBlockState(checkPos.above(1));
                        BlockState head2 = level.getBlockState(checkPos.above(2));
                        if (!head1.isSolidRender(level, checkPos.above(1)) && !head2.isSolidRender(level, checkPos.above(2))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public BlockPos findTargetBlock(ServerLevel level, BlockPos origin, ResourceLocation blockId) {
        return findTargetBlock(level, origin, MiningTarget.of(blockId.toString()), null);
    }

    public BlockPos findTargetBlock(ServerLevel level, BlockPos origin, MiningTarget target) {
        return findTargetBlock(level, origin, target, null);
    }

    public BlockPos findTargetBlock(ServerLevel level, BlockPos origin, MiningTarget target, CompanionEntity companion) {
        if (target == null || inhibited || level == null || origin == null) return null;

        long gameTime = level.getGameTime();
        blacklistedAreas.removeIf(a -> a.expiresAt < gameTime);

        // 1. Check known cached positions first
        Set<BlockPos> cached = knownVeins.get(target.getName());
        if (cached != null && !cached.isEmpty()) {
            Iterator<BlockPos> it = cached.iterator();
            while (it.hasNext()) {
                BlockPos p = it.next();
                if (isBlacklisted(p, gameTime)) {
                    it.remove();
                    continue;
                }
                if (companion != null && companion.getCompanionBrain() != null) {
                    if (companion.getCompanionBrain().isNearDangerZone(p, 24.0, 1)
                            || companion.getCompanionBrain().isPathNearDangerZone(origin, p, 18.0, 1)) {
                        it.remove();
                        continue;
                    }
                }
                if (level.isLoaded(p)) {
                    BlockState state = level.getBlockState(p);
                    if (target.matches(state)) {
                        if (isPhysicallyAccessible(level, p, origin)) {
                            if (!isExposedToAir(level, p) || !hasHostileThreatsNear(level, p, companion, 14.0)) {
                                return p;
                            }
                        }
                        it.remove();
                    } else {
                        it.remove();
                    }
                }
            }
        }

        // 2. Perform radius-based search around origin
        int radius = RADII[currentRadiusIndex];
        // Constrain vertical scan: do not plunge blindly into deep dungeons if companion is on surface
        int minY = Math.max(level.getMinBuildHeight(), Math.max(target.getMinRecommendedY(), origin.getY() - 32));
        int maxY = Math.min(level.getMaxBuildHeight(), Math.min(target.getMaxRecommendedY(), origin.getY() + 24));
        if (minY > maxY) {
            minY = Math.max(level.getMinBuildHeight(), origin.getY() - 24);
            maxY = Math.min(level.getMaxBuildHeight(), origin.getY() + 24);
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int blocksChecked = 0;

        // Radial outward scan
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue; // Boundary only for this radius

                    // First scan from origin downward
                    for (int y = origin.getY(); y >= minY; y--) {
                        BlockPos p = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
                        if (!level.isLoaded(p) || isBlacklisted(p, gameTime)) continue;
                        blocksChecked++;

                        BlockState state = level.getBlockState(p);
                        if (target.matches(state)) {
                            if (!isCandidateSafeAndAccessible(level, p, origin, companion)) continue;

                            double score = calculateScore(level, p, origin);
                            if (score < bestScore) {
                                bestScore = score;
                                best = p;
                            }
                            knownVeins.computeIfAbsent(target.getName(), k -> new HashSet<>()).add(p.immutable());
                        }
                        if (blocksChecked >= MAX_BLOCKS_PER_TICK && best != null) {
                            return best;
                        }
                    }

                    // Then scan from origin upward
                    for (int y = origin.getY() + 1; y <= maxY; y++) {
                        BlockPos p = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
                        if (!level.isLoaded(p) || isBlacklisted(p, gameTime)) continue;
                        blocksChecked++;

                        BlockState state = level.getBlockState(p);
                        if (target.matches(state)) {
                            if (!isCandidateSafeAndAccessible(level, p, origin, companion)) continue;

                            double score = calculateScore(level, p, origin);
                            if (score < bestScore) {
                                bestScore = score;
                                best = p;
                            }
                            knownVeins.computeIfAbsent(target.getName(), k -> new HashSet<>()).add(p.immutable());
                        }
                        if (blocksChecked >= MAX_BLOCKS_PER_TICK && best != null) {
                            return best;
                        }
                    }

                    if (blocksChecked >= MAX_BLOCKS_PER_TICK * 2 && best != null) {
                        return best;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }

        // Cycle through radii
        currentRadiusIndex = (currentRadiusIndex + 1) % RADII.length;
        return best;
    }

    private boolean isCandidateSafeAndAccessible(ServerLevel level, BlockPos p, BlockPos origin, CompanionEntity companion) {
        if (!isPhysicallyAccessible(level, p, origin)) return false;

        if (companion != null && companion.getCompanionBrain() != null) {
            // Verify neither the block nor the travel corridor intersects an active danger zone
            if (companion.getCompanionBrain().isNearDangerZone(p, 24.0, 1)) return false;
            if (companion.getCompanionBrain().isPathNearDangerZone(origin, p, 18.0, 1)) return false;
        }

        // If exposed to air, ensure hostile mobs aren't guarding the block
        if (isExposedToAir(level, p) && hasHostileThreatsNear(level, p, companion, 14.0)) {
            return false;
        }

        return true;
    }

    private double calculateScore(ServerLevel level, BlockPos p, BlockPos origin) {
        double dx = p.getX() - origin.getX();
        double dy = p.getY() - origin.getY();
        double dz = p.getZ() - origin.getZ();
        double hDistSq = dx * dx + dz * dz;

        // Vertical distance is significantly harder and riskier to navigate
        double vPenalty = Math.abs(dy) * Math.abs(dy) * 2.2;
        double score = hDistSq + vPenalty;

        if (isExposedToAir(level, p)) {
            int light = level.getMaxLocalRawBrightness(p);
            if (dy < -8 && light <= 7) {
                // Deep dark cave: penalize heavily to prevent walking into monster spawns
                score *= 1.6;
            } else if (dy >= -6 && dy <= 6) {
                // Safe shallow or surface exposure: slight bonus
                score *= 0.85;
            }
        }
        return score;
    }

    public int getCurrentRadius() {
        return RADII[currentRadiusIndex];
    }
}
