package com.aicompanion.ai.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class PathToBlockPlanner {

    public static class ApproachPlan {
        public final BlockPos standPos;
        public final List<BlockPos> blocksToClear;

        public ApproachPlan(BlockPos standPos, List<BlockPos> blocksToClear) {
            this.standPos = standPos;
            this.blocksToClear = blocksToClear;
        }
    }

    /**
     * Finds an adjacent stand position from which the companion can interact with targetPos.
     */
    public static ApproachPlan planApproach(ServerLevel level, BlockPos companionPos, BlockPos targetPos, boolean allowBreaking) {
        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};

        // 1. First priority: an already walkable adjacent block
        for (Direction dir : directions) {
            BlockPos candidate = targetPos.relative(dir);
            if (isWalkable(level, candidate)) {
                return new ApproachPlan(candidate, new ArrayList<>());
            }
        }

        // 2. Second priority: if allowed to dig, find an adjacent position blocked by minable stone/dirt
        if (allowBreaking) {
            for (Direction dir : directions) {
                if (dir == Direction.DOWN) continue; // Don't stand under the ore in a shaft
                BlockPos stand = targetPos.relative(dir);
                BlockPos ground = stand.below();

                // Ground below must be safe
                if (level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) {
                    List<BlockPos> toClear = new ArrayList<>();
                    BlockState feetState = level.getBlockState(stand);
                    BlockState headState = level.getBlockState(stand.above());

                    if (isSafeToMine(level, stand, companionPos) && isSafeToMine(level, stand.above(), companionPos)) {
                        if (!feetState.isAir()) toClear.add(stand);
                        if (!headState.isAir()) toClear.add(stand.above());
                        return new ApproachPlan(stand, toClear);
                    }
                }
            }
        }

        return null;
    }

    private static boolean isWalkable(ServerLevel level, BlockPos pos) {
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState ground = level.getBlockState(pos.below());

        return (feet.isAir() || feet.getCollisionShape(level, pos).isEmpty()) &&
               (head.isAir() || head.getCollisionShape(level, pos.above()).isEmpty()) &&
               (ground.isFaceSturdy(level, pos.below(), Direction.UP) || ground.isSolidRender(level, pos.below())) &&
               !feet.is(Blocks.LAVA) && !ground.is(Blocks.LAVA) && !ground.is(Blocks.MAGMA_BLOCK);
    }

    public static boolean isSafeToMine(ServerLevel level, BlockPos pos, BlockPos companionFeet) {
        if (pos.equals(companionFeet.below())) {
            // Never mine the block right under the companion's feet!
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;

        // Never mine block entities (chests, furnaces, machines)
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) return false;

        // Never mine bedrock or barriers
        if (state.getDestroySpeed(level, pos) < 0) return false;

        // Check for lava directly adjacent or above that would flow into the companion
        for (Direction dir : Direction.values()) {
            if (dir == Direction.DOWN) continue;
            BlockState adj = level.getBlockState(pos.relative(dir));
            if (adj.is(Blocks.LAVA) || adj.getFluidState().isSource()) {
                return false;
            }
        }

        return true;
    }
}
