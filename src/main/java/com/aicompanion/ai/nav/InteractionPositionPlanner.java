package com.aicompanion.ai.nav;

import com.aicompanion.internal.baritone.costs.HazardCostEvaluator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds optimal, walkable positions adjacent to a target block
 * from which the Companion can safely interact, mine, or place.
 */
public class InteractionPositionPlanner {

    public record StandOption(BlockPos standPos, double distanceToEntity, boolean directLineOfSight) {}

    /**
     * Finds the best walkable standing block from which Esclavo can interact with targetBlock.
     */
    public static BlockPos findStandPosition(ServerLevel level, BlockPos targetBlock, BlockPos entityPos, double maxReach) {
        List<StandOption> candidates = new ArrayList<>();
        double reachSq = maxReach * maxReach;

        int radius = (int) Math.ceil(maxReach);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -4; dy <= 2; dy++) {
                    BlockPos stand = targetBlock.offset(dx, dy, dz);
                    if (stand.equals(targetBlock)) continue;

                    Vec3 standEye = new Vec3(stand.getX() + 0.5, stand.getY() + 1.62, stand.getZ() + 0.5);
                    Vec3 targetCenter = Vec3.atCenterOf(targetBlock);
                    double dTargetSq = standEye.distanceToSqr(targetCenter);
                    if (dTargetSq > reachSq) continue;

                    if (isWalkableStand(level, stand)) {
                        boolean hasLOS = hasLineOfSight(level, stand, targetBlock);
                        double distEntity = stand.distSqr(entityPos);
                        candidates.add(new StandOption(stand, distEntity, hasLOS));
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            // Fallback: check 4 horizontal neighbors, probing down to find walkable ground
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos adj = targetBlock.relative(dir);
                for (int d = 0; d <= 6; d++) {
                    BlockPos candidate = adj.below(d);
                    if (isWalkableStand(level, candidate)) {
                        return candidate;
                    }
                }
            }
            return targetBlock.above();
        }

        // Prioritize candidates with direct line of sight, then closest to entity
        candidates.sort(Comparator
                .comparing((StandOption opt) -> !opt.directLineOfSight)
                .thenComparingDouble(opt -> opt.distanceToEntity));

        return candidates.get(0).standPos();
    }

    public static boolean isWalkableStand(ServerLevel level, BlockPos standPos) {
        BlockPos below = standPos.below();
        BlockState belowState = level.getBlockState(below);
        BlockState feetState = level.getBlockState(standPos);
        BlockState headState = level.getBlockState(standPos.above());

        // Floor must be solid and support standing
        if (!belowState.isFaceSturdy(level, below, Direction.UP) && !belowState.isSolid()) {
            return false;
        }

        // Feet and head must not be solid blocks
        if (feetState.blocksMotion() || headState.blocksMotion()) {
            return false;
        }

        // Must not be lethal hazard
        if (HazardCostEvaluator.isLethalHazard(level, standPos)) {
            return false;
        }

        return true;
    }

    private static boolean hasLineOfSight(ServerLevel level, BlockPos standPos, BlockPos targetBlock) {
        Vec3 eyePos = new Vec3(standPos.getX() + 0.5, standPos.getY() + 1.5, standPos.getZ() + 0.5);
        Vec3 targetCenter = Vec3.atCenterOf(targetBlock);

        BlockHitResult hit = level.clip(new ClipContext(
                eyePos,
                targetCenter,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                null
        ));

        if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(targetBlock)) {
            return true;
        }

        // Leaves do not block line of sight for tree chopping/harvesting
        BlockState hitState = level.getBlockState(hit.getBlockPos());
        return hitState.is(BlockTags.LEAVES);
    }
}
