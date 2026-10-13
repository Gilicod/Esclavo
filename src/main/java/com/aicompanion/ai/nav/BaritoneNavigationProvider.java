package com.aicompanion.ai.nav;

import com.aicompanion.entity.CompanionEntity;

/**
 * Compatibility alias for InternalBaritoneNavigationProvider.
 */
public class BaritoneNavigationProvider extends InternalBaritoneNavigationProvider {
    public BaritoneNavigationProvider(CompanionEntity companion) {
        super(companion);
    }

    public static boolean isBaritoneAvailable() {
        return true; // Internal Baritone engine is always natively embedded
    }
}
