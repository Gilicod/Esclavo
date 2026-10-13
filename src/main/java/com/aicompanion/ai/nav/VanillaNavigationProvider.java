package com.aicompanion.ai.nav;

import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.internal.baritone.goals.Goal;
import com.aicompanion.internal.baritone.goals.GoalBlock;
import com.aicompanion.internal.baritone.goals.GoalEntity;
import com.aicompanion.internal.baritone.goals.GoalExploreRegion;
import com.aicompanion.internal.baritone.goals.GoalInteractBlock;
import com.aicompanion.internal.baritone.goals.GoalNear;
import com.aicompanion.internal.baritone.goals.GoalXZ;
import com.aicompanion.internal.baritone.path.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

public class VanillaNavigationProvider implements NavigationProvider {
    private final CompanionEntity companion;
    private Goal currentGoal = null;
    private NavigationRequestResult status = NavigationRequestResult.ALREADY_REACHED;

    public VanillaNavigationProvider(CompanionEntity companion) {
        this.companion = companion;
    }

    @Override
    public boolean moveTo(BlockPos pos, double speedModifier) {
        this.currentGoal = new GoalBlock(pos);
        boolean success = companion.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, speedModifier);
        this.status = success ? NavigationRequestResult.STARTED : NavigationRequestResult.NO_PATH;
        if (success && MovementPolicy.sprintingAllowed()) {
            companion.setSprinting(true);
        }
        return success;
    }

    @Override
    public boolean moveTo(Entity target, double speedModifier) {
        this.currentGoal = new GoalEntity(target, 2.0, 3.5);
        boolean success = companion.getNavigation().moveTo(target, speedModifier);
        this.status = success ? NavigationRequestResult.STARTED : NavigationRequestResult.NO_PATH;
        if (success && MovementPolicy.sprintingAllowed()) {
            companion.setSprinting(true);
        }
        return success;
    }

    @Override
    public boolean navigateTo(Goal goal, double speedModifier) {
        if (goal == null || companion == null) {
            this.status = NavigationRequestResult.INVALID_TARGET;
            return false;
        }

        this.currentGoal = goal;
        BlockPos current = companion.blockPosition();
        if (goal.isInGoal(current.getX(), current.getY(), current.getZ())) {
            this.status = NavigationRequestResult.ALREADY_REACHED;
            companion.getNavigation().stop();
            companion.setSprinting(false);
            return true;
        }

        if (goal instanceof GoalBlock gb) {
            return moveTo(gb.getTarget(), speedModifier);
        } else if (goal instanceof GoalEntity ge && ge.getTarget() != null) {
            return moveTo(ge.getTarget(), speedModifier);
        } else if (goal instanceof GoalNear gn) {
            return moveTo(gn.getCenter(), speedModifier);
        } else if (goal instanceof GoalInteractBlock gib) {
            return moveTo(gib.getTargetBlock(), speedModifier);
        } else if (goal instanceof GoalExploreRegion ger) {
            double angle = companion.getRandom().nextDouble() * Math.PI * 2.0;
            int tx = companion.getBlockX() + (int)(Math.cos(angle) * 16.0);
            int tz = companion.getBlockZ() + (int)(Math.sin(angle) * 16.0);
            return moveTo(new BlockPos(tx, companion.getBlockY(), tz), speedModifier);
        } else if (goal instanceof GoalXZ gxz) {
            return moveTo(new BlockPos(gxz.getX(), companion.getBlockY(), gxz.getZ()), speedModifier);
        } else {
            // Generic goal: find closest point to target or heuristic
            BlockPos targetPos = companion.blockPosition();
            return moveTo(targetPos, speedModifier);
        }
    }

    @Override
    public void tick() {
        if (currentGoal != null && companion != null) {
            BlockPos pos = companion.blockPosition();
            if (currentGoal.isInGoal(pos.getX(), pos.getY(), pos.getZ())) {
                stop();
            } else if (companion.getNavigation().isDone() && companion.isSprinting()) {
                companion.setSprinting(false);
            }
        }
    }

    @Override
    public void stop() {
        this.currentGoal = null;
        this.status = NavigationRequestResult.ALREADY_REACHED;
        if (companion != null) {
            companion.getNavigation().stop();
            if (companion.isSprinting()) {
                companion.setSprinting(false);
            }
        }
    }

    @Override
    public boolean isDone() {
        if (companion == null) return true;
        if (currentGoal != null) {
            BlockPos pos = companion.blockPosition();
            if (currentGoal.isInGoal(pos.getX(), pos.getY(), pos.getZ())) return true;
        }
        return companion.getNavigation().isDone();
    }

    @Override
    public boolean isStuck() {
        return companion != null && companion.getNavigation().isStuck();
    }

    @Override
    public boolean isMakingProgress() {
        return companion != null && companion.getNavigation().isInProgress() && !companion.getNavigation().isStuck();
    }

    @Override
    public NavigationRequestResult getStatus() {
        return status;
    }

    @Override
    public Path getCurrentPath() {
        return null; // Vanilla path conversion not needed for internal debug
    }

    @Override
    public String getName() {
        return "Vanilla Safe";
    }
}
