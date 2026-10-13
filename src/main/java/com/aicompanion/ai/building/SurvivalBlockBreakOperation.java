package com.aicompanion.ai.building;

import com.aicompanion.ai.equipment.ToolSelectionPolicy;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import com.aicompanion.util.BlockBreakHelper;
import com.aicompanion.util.TerrainPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Universal survival-fidelity block break controller for building operations.
 * Enforces real break times, proper tool equipping, tool durability consumption,
 * arm swing animation, block cracking particle/sound effects, and Forge protection events.
 * Prevents any instant creative-mode block removal during building, clearing, or cleanup.
 */
public class SurvivalBlockBreakOperation {

    public enum Status {
        IN_PROGRESS,
        SUCCESS,
        BLOCKED,
        CANCELLED
    }

    public enum BreakReason {
        TERRAIN_LEVELING,
        OBSTACLE_CLEARING,
        SCAFFOLD_CLEANUP
    }

    private BlockPos currentTarget = null;
    private int breakTicks = 0;
    private int requiredTicks = 0;
    private BreakReason currentReason = null;
    private int lastToolCheckTick = -1;

    public SurvivalBlockBreakOperation() {}

    /**
     * Ticks the survival breaking operation against the specified block pos.
     * Must be called once per companion tick until returning SUCCESS, BLOCKED, or CANCELLED.
     */
    public Status tick(CompanionEntity companion, ServerLevel level, BlockPos pos, BreakReason reason) {
        if (companion == null || level == null || pos == null) {
            reset(companion, level);
            return Status.BLOCKED;
        }

        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) {
            reset(companion, level);
            return Status.SUCCESS;
        }

        // Distance & Reach verification (reach radius: 4.5 blocks -> 20.25 sq)
        Vec3 eye = companion.getEyePosition();
        Vec3 center = Vec3.atCenterOf(pos);
        if (eye.distanceToSqr(center) > 22.0) {
            // Out of reach: cannot physically break from here
            return Status.IN_PROGRESS;
        }

        // Unbreakable check (bedrock, end portal frame, etc.)
        float destroySpeed = state.getDestroySpeed(level, pos);
        if (destroySpeed < 0.0f) {
            reset(companion, level);
            return Status.BLOCKED;
        }

        // Target switch detection
        if (currentTarget == null || !currentTarget.equals(pos) || currentReason != reason) {
            if (currentTarget != null) {
                BlockBreakHelper.cancelBreaking(companion, level, currentTarget);
            }
            currentTarget = pos.immutable();
            currentReason = reason;
            breakTicks = 0;
            requiredTicks = 0;
            lastToolCheckTick = -1;
        }

        // Protect player structures & containers if applicable
        if (reason == BreakReason.TERRAIN_LEVELING || reason == BreakReason.OBSTACLE_CLEARING) {
            if (!TerrainPolicy.mayClearForBuilding(level, pos, state, companion.getTemporaryBlocks())) {
                reset(companion, level);
                return Status.BLOCKED;
            }
            if (state.getBlock() instanceof ChestBlock) {
                reset(companion, level);
                return Status.BLOCKED;
            }
        }

        // Tool Selection: equip appropriate tool (pickaxe, axe, shovel) or craft emergency tool
        if (companion.tickCount - lastToolCheckTick >= 10 || lastToolCheckTick == -1) {
            ToolSelectionPolicy.ensureToolForBlock(companion, state);
            lastToolCheckTick = companion.tickCount;
        }

        // Calculate break time in ticks if not yet computed
        if (requiredTicks <= 0) {
            requiredTicks = BlockBreakHelper.calculateBreakTicks(companion, pos, state);
            if (requiredTicks < 0) {
                reset(companion, level);
                return Status.BLOCKED;
            }
            // Enforce minimum physical time for realism
            requiredTicks = Math.max(2, requiredTicks);
            breakTicks = 0;
        }

        // Look at block and perform physical breaking action
        companion.setCurrentState(CompanionState.BUILDING);
        companion.getLookControl().setLookAt(center.x, center.y, center.z);

        breakTicks++;

        // Visual cracking, particle effects, hit sounds, arm swings
        BlockBreakHelper.tickBreaking(companion, level, pos, breakTicks, requiredTicks);

        // Check completion
        if (breakTicks >= requiredTicks) {
            boolean broke = BlockBreakHelper.finishBreaking(companion, level, pos);
            reset(companion, level);
            return broke ? Status.SUCCESS : Status.BLOCKED;
        }

        return Status.IN_PROGRESS;
    }

    /**
     * Resets the breaker state and cancels any active visual block breaking progress.
     */
    public void reset(CompanionEntity companion, ServerLevel level) {
        if (currentTarget != null && companion != null && level != null) {
            BlockBreakHelper.cancelBreaking(companion, level, currentTarget);
        }
        currentTarget = null;
        breakTicks = 0;
        requiredTicks = 0;
        currentReason = null;
        lastToolCheckTick = -1;
    }

    public boolean isActive() {
        return currentTarget != null;
    }

    public BlockPos getCurrentTarget() {
        return currentTarget;
    }

    public int getBreakTicks() {
        return breakTicks;
    }

    public int getRequiredTicks() {
        return requiredTicks;
    }
}
