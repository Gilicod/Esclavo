package com.aicompanion.ai.nav;

import com.aicompanion.internal.baritone.goals.Goal;
import com.aicompanion.internal.baritone.goals.GoalBlock;
import com.aicompanion.internal.baritone.goals.GoalEntity;
import com.aicompanion.internal.baritone.path.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

public interface NavigationProvider {

    default boolean moveTo(BlockPos pos, double speedModifier) {
        return navigateTo(new GoalBlock(pos), speedModifier);
    }

    default boolean moveTo(Entity target, double speedModifier) {
        return navigateTo(new GoalEntity(target, 2.0, 3.5), speedModifier);
    }

    boolean navigateTo(Goal goal, double speedModifier);

    void tick();

    void stop();

    boolean isDone();

    boolean isStuck();

    default boolean isMakingProgress() {
        return false;
    }

    NavigationRequestResult getStatus();

    Path getCurrentPath();

    String getName();
}
