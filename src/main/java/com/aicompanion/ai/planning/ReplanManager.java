package com.aicompanion.ai.planning;

import com.aicompanion.entity.CompanionEntity;

public class ReplanManager {
    private final CompanionEntity companion;
    private int replanCount = 0;

    public ReplanManager(CompanionEntity companion) {
        this.companion = companion;
    }

    public boolean canReplan() {
        int max = 3;
        try {
            if (com.aicompanion.AICompanionConfig.COMMON != null && com.aicompanion.AICompanionConfig.COMMON.maxReplansPerTask != null) {
                max = com.aicompanion.AICompanionConfig.COMMON.maxReplansPerTask.get();
            }
        } catch (Exception ignored) {}
        return replanCount < Math.max(1, max);
    }

    public void incrementReplan() {
        replanCount++;
    }

    public void reset() {
        replanCount = 0;
    }
}
