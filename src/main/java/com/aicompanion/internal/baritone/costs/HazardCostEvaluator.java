package com.aicompanion.internal.baritone.costs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Evaluates environmental hazards and assigns movement cost penalties
 * or prohibitions. Adapted from Baritone (LGPL-3.0) hazard detection.
 */
public class HazardCostEvaluator {
    public static final double PROHIBITED_COST = 1_000_000.0;

    /**
     * Checks if a position is completely lethal or strictly prohibited.
     */
    public static boolean isLethalHazard(ServerLevel level, BlockPos standPos) {
        if (standPos.getY() < level.getMinBuildHeight() + 2) {
            return true; // The Void
        }

        BlockPos below = standPos.below();
        BlockState belowState = level.getBlockState(below);
        BlockState feetState = level.getBlockState(standPos);
        BlockState headState = level.getBlockState(standPos.above());

        // Check Lava
        if (belowState.getFluidState().is(Fluids.LAVA) ||
            feetState.getFluidState().is(Fluids.LAVA) ||
            headState.getFluidState().is(Fluids.LAVA)) {
            return true;
        }

        // Check Fire & Soul Fire
        if (feetState.is(Blocks.FIRE) || feetState.is(Blocks.SOUL_FIRE) ||
            belowState.is(Blocks.FIRE) || belowState.is(Blocks.SOUL_FIRE)) {
            return true;
        }

        return false;
    }

    /**
     * Calculates cost penalty for moving into or through standPos.
     * Returns 0.0 for normal safe blocks, higher values for risky blocks,
     * or PROHIBITED_COST for impossible/fatal moves.
     */
    public static double getHazardPenalty(ServerLevel level, BlockPos standPos, int fallDistance) {
        if (isLethalHazard(level, standPos)) {
            return PROHIBITED_COST;
        }

        double penalty = 0.0;
        BlockPos below = standPos.below();
        BlockState belowState = level.getBlockState(below);
        BlockState feetState = level.getBlockState(standPos);

        // Fall damage penalty (> 3 blocks without water)
        if (fallDistance > 3) {
            if (!feetState.getFluidState().is(Fluids.WATER)) {
                if (fallDistance > 4) {
                    return PROHIBITED_COST; // Fatal fall (> 4 blocks causes severe damage)
                }
                penalty += (fallDistance - 2) * 25.0; // Significant penalty
            }
        }

        // Adjacent exposed or flowing lava penalty
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos adj = standPos.relative(dir);
            if (level.isLoaded(adj) && level.getBlockState(adj).getFluidState().is(Fluids.LAVA)) {
                penalty += 150.0;
            }
        }

        // Cactus, Magma, Sweet Berries, Powder Snow
        if (belowState.is(Blocks.MAGMA_BLOCK)) {
            penalty += 15.0;
        }
        if (feetState.is(Blocks.CACTUS) || feetState.is(Blocks.SWEET_BERRY_BUSH)) {
            penalty += 20.0;
        }
        if (feetState.is(Blocks.POWDER_SNOW) || belowState.is(Blocks.POWDER_SNOW)) {
            penalty += 40.0;
        }
        if (feetState.is(Blocks.WITHER_ROSE)) {
            penalty += 50.0;
        }

        return penalty;
    }

    /**
     * Calculates cost penalty for breaking or tunneling into blocks that have
     * falling blocks (sand, gravel) or fluids (lava) overhead.
     */
    public static double getExcavationHazardPenalty(ServerLevel level, BlockPos pos) {
        if (pos == null || level == null) return 0.0;
        BlockPos above = pos.above();
        if (!level.isLoaded(above)) return 0.0;

        BlockState aboveState = level.getBlockState(above);
        if (aboveState.getFluidState().is(Fluids.LAVA)) {
            return PROHIBITED_COST; // Fatal overhead lava
        }
        if (aboveState.getBlock() instanceof net.minecraft.world.level.block.FallingBlock) {
            return 300.0; // Massive penalty for unstable gravel/sand ceiling
        }
        return 0.0;
    }

    /**
     * Evaluates if pos can be safely excavated without risking lava flows or ceiling cave-ins.
     */
    public static boolean isSafeToExcavate(ServerLevel level, BlockPos pos) {
        if (pos == null || level == null) return false;
        if (!level.isLoaded(pos)) return false;

        // Check overhead block for lava or falling hazard
        BlockPos above = pos.above();
        if (level.isLoaded(above)) {
            BlockState aboveState = level.getBlockState(above);
            if (aboveState.getFluidState().is(Fluids.LAVA)) {
                return false;
            }
        }

        // Check horizontal neighbors for adjacent lava
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos adj = pos.relative(dir);
            if (level.isLoaded(adj)) {
                if (level.getBlockState(adj).getFluidState().is(Fluids.LAVA)) {
                    return false;
                }
            }
        }

        return true;
    }
}
