package com.aicompanion.ai.tasks;

public enum DropCollectionResult {
    NO_DROPS_FOUND,
    TARGET_ACQUIRED,
    MOVING_TO_DROP,
    PICKED_UP,
    PARTIAL_PICKUP,
    INVENTORY_FULL,
    DROP_LOST,
    DROP_UNREACHABLE,
    TIMEOUT_RETRY,
    COMPLETE
}
