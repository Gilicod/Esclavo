package com.aicompanion.ai.nav;

/**
 * Result returned when a Task or system requests navigation.
 * Allows Tasks to respond explicitly to navigation states.
 */
public enum NavigationRequestResult {
    STARTED("Navegación iniciada correctamente"),
    ALREADY_REACHED("Objetivo ya alcanzado en la posición actual"),
    NO_PATH("No se encontró ninguna ruta transitable hacia el objetivo"),
    INVALID_TARGET("Posición u objetivo inválido o fuera del mundo"),
    BLOCKED("Ruta bloqueada por obstáculos infranqueables"),
    NEEDS_BREAK("Requiere romper bloques para continuar"),
    NEEDS_PLACE("Requiere colocar bloques/puente para continuar"),
    CHUNK_UNAVAILABLE("El terreno hacia el objetivo no está cargado"),
    FAILED("Error interno durante la planificación de ruta");

    private final String description;

    NavigationRequestResult(String description) {
        this.description = description;
    }

    public boolean isSuccessful() {
        return this == STARTED || this == ALREADY_REACHED;
    }

    public String getDescription() {
        return description;
    }
}
