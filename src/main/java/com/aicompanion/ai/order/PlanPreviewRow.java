package com.aicompanion.ai.order;

public record PlanPreviewRow(
        String text,
        PlanRowType type,
        String iconResource,
        String status
) {
    public enum PlanRowType {
        GOAL,
        ACTION,
        REQUIREMENT,
        MISSING,
        RETURN,
        WARNING
    }
}
