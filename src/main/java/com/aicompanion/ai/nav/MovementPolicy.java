package com.aicompanion.ai.nav;

import com.aicompanion.AICompanionConfig;

/**
 * Central movement-speed policy.
 *
 * Mob terminal ground speed grows roughly with the square of the speed modifier
 * (and sprinting adds another +30% to the attribute), so values such as 1.25 + sprint
 * made Esclavo cross the map at ~9 blocks/s ("velocidad de la luz").
 * Every navigation request is now clamped through this class.
 */
public final class MovementPolicy {

    public static final double DEFAULT_WALK = 1.0;
    public static final double DEFAULT_MAX = 1.35;

    private MovementPolicy() {}

    /** Normal walking modifier (configurable). */
    public static double walk() {
        try {
            if (AICompanionConfig.COMMON != null && AICompanionConfig.COMMON.walkSpeedModifier != null) {
                return AICompanionConfig.COMMON.walkSpeedModifier.get();
            }
        } catch (Exception ignored) {}
        return DEFAULT_WALK;
    }

    /** Upper bound for any requested modifier (configurable). */
    public static double max() {
        try {
            if (AICompanionConfig.COMMON != null && AICompanionConfig.COMMON.maxSpeedModifier != null) {
                return AICompanionConfig.COMMON.maxSpeedModifier.get();
            }
        } catch (Exception ignored) {}
        return DEFAULT_MAX;
    }

    /** "Hurry" / sprint speed: matching player Ctrl sprint. */
    public static double hurry() {
        return Math.min(max(), 1.25);
    }

    /** Standard running sprint speed (matching player Ctrl sprint speed). */
    public static double sprint() {
        return Math.min(max(), 1.25);
    }

    /**
     * Maps the legacy modifiers used across tasks (1.0 .. 1.35) onto the natural range
     * [walk .. max].
     */
    public static double clamp(double requested) {
        if (Double.isNaN(requested) || requested <= 0) {
            return walk();
        }
        double walk = walk();
        double max = Math.max(walk, max());
        if (requested <= 1.0) {
            return Math.max(0.5, Math.min(requested * walk, max));
        }
        // Map 1.0..1.35 smoothly to walk..max
        double t = Math.min(1.0, (requested - 1.0) / 0.35);
        return walk + (max - walk) * t;
    }

    public static boolean sprintingAllowed() {
        try {
            if (AICompanionConfig.COMMON != null && AICompanionConfig.COMMON.allowSprinting != null) {
                return AICompanionConfig.COMMON.allowSprinting.get();
            }
        } catch (Exception ignored) {}
        return true;
    }

    public static int maxRescueAttempts() {
        try {
            if (AICompanionConfig.COMMON != null && AICompanionConfig.COMMON.maxRescueAttempts != null) {
                return AICompanionConfig.COMMON.maxRescueAttempts.get();
            }
        } catch (Exception ignored) {}
        return 6;
    }

    public static double deliveryDistance() {
        try {
            if (AICompanionConfig.COMMON != null && AICompanionConfig.COMMON.deliveryDistance != null) {
                return AICompanionConfig.COMMON.deliveryDistance.get();
            }
        } catch (Exception ignored) {}
        return 3.0;
    }

    public static double pickupWalkDistance() {
        try {
            if (AICompanionConfig.COMMON != null && AICompanionConfig.COMMON.pickupWalkDistance != null) {
                return AICompanionConfig.COMMON.pickupWalkDistance.get();
            }
        } catch (Exception ignored) {}
        return 1.6;
    }
}
