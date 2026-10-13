package com.aicompanion.client.gui.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * Persistent configuration engine for Esclavo HUD and Compass modules.
 * Stores player preferences in config/esclavo_hud.json so visual customizations
 * survive client restarts and dimension transitions.
 */
public class HudConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static HudConfig INSTANCE = null;
    private static final String CONFIG_FILE_NAME = "esclavo_hud.json";

    public enum DisplayMode {
        FULL("Completo"),
        COMPACT("Compacto"),
        MINIMAL("Mínimo");

        private final String label;
        DisplayMode(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum CompassStyle {
        CIRCULAR("Brújula Circular"),
        COMPACT("Flecha Compacta"),
        FULL("Localizador Completo"),
        DISCRETE("Icono Discreto");

        private final String label;
        CompassStyle(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum CompassPreset {
        TOP_RIGHT("Superior Derecha"),
        TOP_LEFT("Superior Izquierda"),
        BOTTOM_RIGHT("Inferior Derecha"),
        BOTTOM_LEFT("Inferior Izquierda"),
        TOP_CENTER("Centro Superior"),
        BOTTOM_CENTER("Centro Inferior"),
        CUSTOM("Personalizado");

        private final String label;
        CompassPreset(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    // Main HUD
    public boolean hudVisible = true;
    public float hudScale = 1.0f; // 0.40 to 1.50
    public int hudPosX = 8;
    public int hudPosY = 8;
    public float hudOpacity = 0.8f; // 0.0 to 1.0
    public DisplayMode hudMode = DisplayMode.FULL;

    // Element toggles
    public boolean showName = true;
    public boolean showLevel = true;
    public boolean showState = true;
    public boolean showDistance = true;
    public boolean showCoords = true;
    public boolean showHealthBar = true;
    public boolean showHealthNumber = true;
    public boolean showOrder = true;
    public boolean showActivity = true;
    public boolean showIcons = true;
    public boolean autoHideF1 = true;
    public boolean autoHideNoCompanion = false;

    // Compass
    public boolean compassEnabled = true;
    public float compassScale = 1.0f; // 0.40 to 1.50
    public int compassPosX = -1; // -1 indicates default placement by preset
    public int compassPosY = -1;
    public CompassPreset compassPreset = CompassPreset.TOP_RIGHT;
    public float compassOpacity = 0.85f;
    public CompassStyle compassStyle = CompassStyle.CIRCULAR;
    public boolean compassShowDistance = true;
    public boolean compassShowName = true;
    public boolean compassShowCoords = true;
    public boolean compassShowHeight = true;

    // Multi-companion selection
    public String selectedCompanionUuid = "";

    public static HudConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        File file = getConfigFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                INSTANCE = GSON.fromJson(reader, HudConfig.class);
                if (INSTANCE != null) {
                    INSTANCE.validate();
                    // Keep AICompanionConfig.CLIENT in sync with master single source of truth
                    if (com.aicompanion.AICompanionConfig.CLIENT != null) {
                        com.aicompanion.AICompanionConfig.CLIENT.showHudOverlay.set(INSTANCE.hudVisible);
                    }
                    return;
                }
            } catch (Exception e) {
                com.aicompanion.AICompanionMod.LOGGER.error("Failed to load esclavo_hud.json, using defaults", e);
            }
        }
        INSTANCE = new HudConfig();
        INSTANCE.save();
    }

    public void setHudVisible(boolean visible) {
        this.hudVisible = visible;
        if (com.aicompanion.AICompanionConfig.CLIENT != null) {
            com.aicompanion.AICompanionConfig.CLIENT.showHudOverlay.set(visible);
        }
        com.aicompanion.client.gui.CompanionHudOverlay.isVisible = visible;
        save();
    }

    public void resetMainHudDefaults() {
        this.hudVisible = true;
        this.hudScale = 1.0f;
        this.hudPosX = 8;
        this.hudPosY = 8;
        this.hudOpacity = 0.8f;
        this.hudMode = DisplayMode.FULL;

        this.showName = true;
        this.showLevel = true;
        this.showState = true;
        this.showDistance = true;
        this.showCoords = true;
        this.showHealthBar = true;
        this.showHealthNumber = true;
        this.showOrder = true;
        this.showActivity = true;
        this.showIcons = true;
        this.autoHideF1 = true;
        this.autoHideNoCompanion = false;

        if (com.aicompanion.AICompanionConfig.CLIENT != null) {
            com.aicompanion.AICompanionConfig.CLIENT.showHudOverlay.set(true);
        }
        com.aicompanion.client.gui.CompanionHudOverlay.isVisible = true;
        save();
    }

    public void save() {
        File file = getConfigFile();
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            com.aicompanion.AICompanionMod.LOGGER.error("Failed to save esclavo_hud.json", e);
        }
    }

    public void resetDefaults() {
        hudVisible = true;
        hudScale = 1.0f;
        hudPosX = 8;
        hudPosY = 8;
        hudOpacity = 0.8f;
        hudMode = DisplayMode.FULL;

        showName = true;
        showLevel = true;
        showState = true;
        showDistance = true;
        showCoords = true;
        showHealthBar = true;
        showHealthNumber = true;
        showOrder = true;
        showActivity = true;
        showIcons = true;
        autoHideF1 = true;
        autoHideNoCompanion = true;

        compassEnabled = true;
        compassScale = 1.0f;
        compassPosX = -1;
        compassPosY = -1;
        compassPreset = CompassPreset.TOP_RIGHT;
        compassOpacity = 0.85f;
        compassStyle = CompassStyle.CIRCULAR;
        compassShowDistance = true;
        compassShowName = true;
        compassShowCoords = true;
        compassShowHeight = true;
        selectedCompanionUuid = "";

        save();
    }

    public void validate() {
        hudScale = Math.max(0.4f, Math.min(1.5f, hudScale));
        hudOpacity = Math.max(0.0f, Math.min(1.0f, hudOpacity));
        if (hudMode == null) hudMode = DisplayMode.FULL;

        compassScale = Math.max(0.4f, Math.min(1.5f, compassScale));
        compassOpacity = Math.max(0.0f, Math.min(1.0f, compassOpacity));
        if (compassPreset == null) compassPreset = CompassPreset.TOP_RIGHT;
        if (compassStyle == null) compassStyle = CompassStyle.CIRCULAR;
        if (selectedCompanionUuid == null) selectedCompanionUuid = "";
    }

    private static File getConfigFile() {
        File configDir = new File(Minecraft.getInstance().gameDirectory, "config");
        return new File(configDir, CONFIG_FILE_NAME);
    }
}
