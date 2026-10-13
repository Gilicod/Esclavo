package com.aicompanion.entity;

public enum CompanionState {
    IDLE("Inactivo"),
    FOLLOWING("Siguiendo"),
    STAYING("Esperando"),
    NAVIGATING("Navegando"),
    SEARCHING("Buscando"),
    MINING("Minando"),
    GATHERING("Recolectando"),
    CHOPPING("Talando"),
    FIGHTING("Combatiendo"),
    BUILDING("Construyendo"),
    CRAFTING("Fabricando"),
    SMELTING("Fundiendo"),
    DELIVERING("Entregando"),
    RETURNING("Regresando"),
    DEPOSITING("Depositando"),
    WITHDRAWING("Retirando"),
    EXPLORING("Explorando"),
    TRAVELLING_DIMENSION("Viajando dimensión"),
    WAITING("Esperando"),
    PAUSED("Pausado"),
    INVENTORY_FULL("Inventario Lleno"),
    NO_TOOL("Sin Herramienta"),
    NO_FOOD("Sin Comida"),
    DANGER("Peligro"),
    STUCK("Atascado"),
    DOWNED("Herido"),
    FARMING("Cosechando"),
    GUARDING("Vigilando"),
    DEAD_WAITING_RESPAWN("Muerto (esperando reaparición)"),
    RESPAWNING("Reapareciendo");

    private final String displayName;

    CompanionState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
