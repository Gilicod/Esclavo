package com.aicompanion.ai.building;

import com.aicompanion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class BuildSiteValidator {

    /**
     * Determines a safe origin in front of the builder (or player) so the construction
     * does not trap the entity or get placed directly inside the entity's feet.
     */
    public static BlockPos calculateSafeBuildOrigin(CompanionEntity companion, BlockPos requestedOrigin, Blueprint blueprint) {
        ServerLevel level = (ServerLevel) companion.level();
        Player owner = companion.getOwnerPlayer();

        // 1. If owner player is present, build near the owner!
        if (owner != null) {
            Direction facing = owner.getDirection();
            BlockPos inFrontOfPlayer = owner.blockPosition().relative(facing, 6);
            return findSolidGround(level, inFrontOfPlayer);
        }

        // 2. If explicit requested origin provided
        if (requestedOrigin != null && !requestedOrigin.equals(companion.blockPosition())) {
            return findSolidGround(level, requestedOrigin);
        }

        // 3. Fallback: in front of companion
        Direction facing = companion.getDirection();
        BlockPos inFrontOfComp = companion.blockPosition().relative(facing, 4);
        return findSolidGround(level, inFrontOfComp);
    }

    public static BlockPos findSolidGround(ServerLevel level, BlockPos pos) {
        int maxY = Math.min(level.getMaxBuildHeight() - 5, pos.getY() + 5);
        int minY = Math.max(level.getMinBuildHeight() + 1, pos.getY() - 15);
        for (int y = maxY; y >= minY; y--) {
            BlockPos check = new BlockPos(pos.getX(), y, pos.getZ());
            BlockState state = level.getBlockState(check);
            BlockState above = level.getBlockState(check.above());
            if (state.isSolid() && (above.isAir() || above.canBeReplaced())) {
                return check; // Ground layer (Y=0 of the blueprint)
            }
        }
        return pos;
    }

    public static boolean isSiteValid(ServerLevel level, BlockPos origin, Blueprint blueprint, CompanionEntity companion) {
        if (blueprint == null || blueprint.getBlocks().isEmpty()) return false;

        // Verify the companion itself is not inside the build box
        BlockPos feet = companion.blockPosition();
        BlockPos head = feet.above();

        for (Blueprint.BlockEntry entry : blueprint.getBlocks()) {
            BlockPos worldPos = origin.offset(entry.offset());
            if (worldPos.equals(feet) || worldPos.equals(head)) {
                return false; // Traps the companion!
            }
        }
        return true;
    }
}
