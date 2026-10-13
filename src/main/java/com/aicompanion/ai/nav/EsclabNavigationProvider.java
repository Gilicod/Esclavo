package com.aicompanion.ai.nav;

import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.internal.baritone.goals.Goal;
import com.aicompanion.internal.baritone.path.BaritonePathAdapter;
import com.aicompanion.internal.baritone.path.Path;
import com.aicompanion.internal.baritone.path.PathResult;
import com.aicompanion.internal.baritone.planner.InternalBaritonePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Queue;

/**
 * Native Esclavo Navigation Provider.
 * High-reliability deterministic A* waypoint navigator.
 * Disallows block breaking and block placement to preserve world terrain.
 */
public class EsclabNavigationProvider implements NavigationProvider {

    private final CompanionEntity companion;
    private final CompanionActionExecutor executor;
    private Goal currentGoal = null;
    private Path currentPath = null;
    private double currentSpeed = 1.0;
    private NavigationRequestResult status = NavigationRequestResult.ALREADY_REACHED;

    public EsclabNavigationProvider(CompanionEntity companion) {
        this.companion = companion;
        this.executor = new CompanionActionExecutor(companion);
    }

    @Override
    public boolean navigateTo(Goal goal, double speedModifier) {
        if (goal == null || companion == null) {
            this.status = NavigationRequestResult.INVALID_TARGET;
            return false;
        }

        this.currentGoal = goal;
        this.currentSpeed = speedModifier;

        BlockPos pos = companion.blockPosition();
        if (goal.isInGoal(pos.getX(), pos.getY(), pos.getZ())) {
            this.status = NavigationRequestResult.ALREADY_REACHED;
            this.executor.clear();
            return true;
        }

        if (!(companion.level() instanceof ServerLevel serverLevel)) {
            this.status = NavigationRequestResult.FAILED;
            return false;
        }

        // Run non-destructive A* planner
        InternalBaritonePlanner planner = new InternalBaritonePlanner(serverLevel, false, false, 1200, 12);
        PathResult result = planner.plan(pos, goal);

        if (result.hasPath()) {
            this.currentPath = result.getPath();
            Queue<NavigationAction> actions = BaritonePathAdapter.adaptToActions(this.currentPath, speedModifier);
            this.executor.setActions(actions);
            this.status = NavigationRequestResult.STARTED;
            return true;
        } else {
            this.status = NavigationRequestResult.NO_PATH;
            return false;
        }
    }

    @Override
    public void tick() {
        if (currentGoal == null || companion == null) {
            return;
        }

        BlockPos pos = companion.blockPosition();
        if (currentGoal.isInGoal(pos.getX(), pos.getY(), pos.getZ())) {
            this.status = NavigationRequestResult.ALREADY_REACHED;
            this.executor.clear();
            return;
        }

        executor.tick();
    }

    @Override
    public void stop() {
        this.currentGoal = null;
        this.currentPath = null;
        this.executor.clear();
        this.status = NavigationRequestResult.ALREADY_REACHED;
    }

    @Override
    public boolean isDone() {
        if (currentGoal == null) return true;
        BlockPos pos = companion.blockPosition();
        return currentGoal.isInGoal(pos.getX(), pos.getY(), pos.getZ()) || (!executor.hasActions() && status == NavigationRequestResult.ALREADY_REACHED);
    }

    @Override
    public boolean isStuck() {
        return executor.isStuck();
    }

    @Override
    public boolean isMakingProgress() {
        return executor != null && executor.isMakingProgress();
    }

    @Override
    public NavigationRequestResult getStatus() {
        return status;
    }

    @Override
    public Path getCurrentPath() {
        return currentPath;
    }

    @Override
    public String getName() {
        return "Esclavo Deterministic A*";
    }
}
