package com.aicompanion.entity;

public enum DefaultBehavior {
    FOLLOW("Seguir"),
    STAY("Esperar");

    private final String displayName;

    DefaultBehavior(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
