package com.aicompanion.ai.planning;

public class PlanScorer {
    public static double score(double distance, double risk, int estimatedCost) {
        return 100.0 - (distance * 0.05 + risk * 20.0 + estimatedCost * 0.1);
    }
}
