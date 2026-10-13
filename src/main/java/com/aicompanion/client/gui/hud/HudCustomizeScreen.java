package com.aicompanion.client.gui.hud;

import com.aicompanion.client.gui.widgets.EsclabButton;
import com.aicompanion.client.gui.widgets.EsclabSlider;
import com.aicompanion.client.gui.widgets.EsclabToggle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Interactive visual Drag & Drop editor for the Esclavo HUD and Compass.
 * Allows moving modules freely across the screen, adjusting scales, opacities,
 * display modes, compass styles, and saving player preferences.
 */
public class HudCustomizeScreen extends Screen {
    private final Screen parentScreen;
    private final HudConfig cfg;

    // Working copy for preview/editing
    private boolean tempHudVisible;
    private float tempHudScale;
    private int tempHudX;
    private int tempHudY;
    private float tempHudOpacity;
    private HudConfig.DisplayMode tempHudMode;

    private boolean tempCompassEnabled;
    private float tempCompassScale;
    private int tempCompassX;
    private int tempCompassY;
    private float tempCompassOpacity;
    private HudConfig.CompassStyle tempCompassStyle;
    private HudConfig.CompassPreset tempCompassPreset;

    private boolean tempShowName;
    private boolean tempShowLevel;
    private boolean tempShowState;
    private boolean tempShowDistance;
    private boolean tempShowCoords;
    private boolean tempShowHealthBar;
    private boolean tempShowHealthNumber;
    private boolean tempShowOrder;
    private boolean tempShowActivity;
    private boolean tempCompassShowHeight;

    // Dragging state
    private boolean draggingHud = false;
    private boolean draggingCompass = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    private boolean showElementModal = false;

    public HudCustomizeScreen(Screen parentScreen) {
        super(Component.literal("Personalizar Interfaz - Esclavo"));
        this.parentScreen = parentScreen;
        this.cfg = HudConfig.get();
        copyFromConfig();
    }

    private void copyFromConfig() {
        this.tempHudVisible = cfg.hudVisible;
        this.tempHudScale = cfg.hudScale;
        this.tempHudX = cfg.hudPosX;
        this.tempHudY = cfg.hudPosY;
        this.tempHudOpacity = cfg.hudOpacity;
        this.tempHudMode = cfg.hudMode;

        this.tempCompassEnabled = cfg.compassEnabled;
        this.tempCompassScale = cfg.compassScale;
        this.tempCompassX = cfg.compassPosX;
        this.tempCompassY = cfg.compassPosY;
        this.tempCompassOpacity = cfg.compassOpacity;
        this.tempCompassStyle = cfg.compassStyle;
        this.tempCompassPreset = cfg.compassPreset;

        this.tempShowName = cfg.showName;
        this.tempShowLevel = cfg.showLevel;
        this.tempShowState = cfg.showState;
        this.tempShowDistance = cfg.showDistance;
        this.tempShowCoords = cfg.showCoords;
        this.tempShowHealthBar = cfg.showHealthBar;
        this.tempShowHealthNumber = cfg.showHealthNumber;
        this.tempShowOrder = cfg.showOrder;
        this.tempShowActivity = cfg.showActivity;
        this.tempCompassShowHeight = cfg.compassShowHeight;
    }

    private void saveToConfig() {
        cfg.hudVisible = this.tempHudVisible;
        cfg.hudScale = this.tempHudScale;
        cfg.hudPosX = this.tempHudX;
        cfg.hudPosY = this.tempHudY;
        cfg.hudOpacity = this.tempHudOpacity;
        cfg.hudMode = this.tempHudMode;

        cfg.compassEnabled = this.tempCompassEnabled;
        cfg.compassScale = this.tempCompassScale;
        cfg.compassPosX = this.tempCompassX;
        cfg.compassPosY = this.tempCompassY;
        cfg.compassOpacity = this.tempCompassOpacity;
        cfg.compassStyle = this.tempCompassStyle;
        cfg.compassPreset = this.tempCompassPreset;

        cfg.showName = this.tempShowName;
        cfg.showLevel = this.tempShowLevel;
        cfg.showState = this.tempShowState;
        cfg.showDistance = this.tempShowDistance;
        cfg.showCoords = this.tempShowCoords;
        cfg.showHealthBar = this.tempShowHealthBar;
        cfg.showHealthNumber = this.tempShowHealthNumber;
        cfg.showOrder = this.tempShowOrder;
        cfg.showActivity = this.tempShowActivity;
        cfg.compassShowHeight = this.tempCompassShowHeight;

        cfg.save();
    }

    @Override
    protected void init() {
        this.clearWidgets();

        if (showElementModal) {
            initModalWidgets();
            return;
        }

        int panelY = height - 96;

        // Row 1: HUD Controls
        int row1Y = panelY + 6;
        addRenderableWidget(new EsclabButton(8, row1Y, 110, 18,
                Component.literal("HUD: " + (tempHudVisible ? "§aVisible" : "§cOculto")), b -> {
            tempHudVisible = !tempHudVisible;
            b.setMessage(Component.literal("HUD: " + (tempHudVisible ? "§aVisible" : "§cOculto")));
        }));

        addRenderableWidget(new EsclabButton(122, row1Y, 120, 18,
                Component.literal("Modo: §e" + tempHudMode.getLabel()), b -> {
            tempHudMode = switch (tempHudMode) {
                case FULL -> HudConfig.DisplayMode.COMPACT;
                case COMPACT -> HudConfig.DisplayMode.MINIMAL;
                case MINIMAL -> HudConfig.DisplayMode.FULL;
            };
            b.setMessage(Component.literal("Modo: §e" + tempHudMode.getLabel()));
        }));

        addRenderableWidget(new EsclabSlider(246, row1Y, 110, 18, "Escala HUD", 0.40, 1.50, tempHudScale, true, val -> {
            tempHudScale = val.floatValue();
        }));

        addRenderableWidget(new EsclabSlider(360, row1Y, 110, 18, "Opacidad HUD", 0.0, 1.0, tempHudOpacity, true, val -> {
            tempHudOpacity = val.floatValue();
        }));

        // Row 2: Compass Controls
        int row2Y = panelY + 28;
        addRenderableWidget(new EsclabButton(8, row2Y, 110, 18,
                Component.literal("Brújula: " + (tempCompassEnabled ? "§aActivada" : "§cDesactivada")), b -> {
            tempCompassEnabled = !tempCompassEnabled;
            b.setMessage(Component.literal("Brújula: " + (tempCompassEnabled ? "§aActivada" : "§cDesactivada")));
        }));

        addRenderableWidget(new EsclabButton(122, row2Y, 120, 18,
                Component.literal("Estilo: §b" + tempCompassStyle.getLabel()), b -> {
            tempCompassStyle = switch (tempCompassStyle) {
                case CIRCULAR -> HudConfig.CompassStyle.COMPACT;
                case COMPACT -> HudConfig.CompassStyle.FULL;
                case FULL -> HudConfig.CompassStyle.DISCRETE;
                case DISCRETE -> HudConfig.CompassStyle.CIRCULAR;
            };
            b.setMessage(Component.literal("Estilo: §b" + tempCompassStyle.getLabel()));
        }));

        addRenderableWidget(new EsclabSlider(246, row2Y, 110, 18, "Escala Brújula", 0.40, 1.50, tempCompassScale, true, val -> {
            tempCompassScale = val.floatValue();
        }));

        addRenderableWidget(new EsclabSlider(360, row2Y, 110, 18, "Opacidad Brújula", 0.0, 1.0, tempCompassOpacity, true, val -> {
            tempCompassOpacity = val.floatValue();
        }));

        // Row 3: Action Buttons
        int row3Y = panelY + 50;
        addRenderableWidget(new EsclabButton(8, row3Y, 110, 20,
                Component.literal("⚙ Elementos..."), b -> {
            showElementModal = true;
            this.init();
        }));

        addRenderableWidget(new EsclabButton(122, row3Y, 120, 20,
                Component.literal("↺ Rest. HUD"), b -> {
            cfg.resetMainHudDefaults();
            copyFromConfig();
            this.init();
        }));

        addRenderableWidget(new EsclabButton(246, row3Y, 110, 20,
                Component.literal("↺ Rest. Todo"), b -> {
            cfg.resetDefaults();
            copyFromConfig();
            this.init();
        }));

        addRenderableWidget(new EsclabButton(360, row3Y, 52, 20,
                Component.literal("Cancelar"), b -> {
            this.onClose();
        }));

        EsclabButton btnSave = new EsclabButton(416, row3Y, 54, 20,
                Component.literal("✔ GUARDAR"), b -> {
            saveToConfig();
            this.onClose();
        });
        btnSave.setGoldSelected(true);
        addRenderableWidget(btnSave);
    }

    private void initModalWidgets() {
        int mX = (width - 240) / 2;
        int mY = (height - 210) / 2;

        addRenderableWidget(new EsclabToggle(mX + 10, mY + 24, 105, 14, Component.literal("Nombre"), tempShowName, v -> tempShowName = v));
        addRenderableWidget(new EsclabToggle(mX + 120, mY + 24, 105, 14, Component.literal("Nivel"), tempShowLevel, v -> tempShowLevel = v));

        addRenderableWidget(new EsclabToggle(mX + 10, mY + 42, 105, 14, Component.literal("Estado"), tempShowState, v -> tempShowState = v));
        addRenderableWidget(new EsclabToggle(mX + 120, mY + 42, 105, 14, Component.literal("Distancia"), tempShowDistance, v -> tempShowDistance = v));

        addRenderableWidget(new EsclabToggle(mX + 10, mY + 60, 105, 14, Component.literal("Coordenadas"), tempShowCoords, v -> tempShowCoords = v));
        addRenderableWidget(new EsclabToggle(mX + 120, mY + 60, 105, 14, Component.literal("Barra Vida"), tempShowHealthBar, v -> tempShowHealthBar = v));

        addRenderableWidget(new EsclabToggle(mX + 10, mY + 78, 105, 14, Component.literal("Número Vida"), tempShowHealthNumber, v -> tempShowHealthNumber = v));
        addRenderableWidget(new EsclabToggle(mX + 120, mY + 78, 105, 14, Component.literal("Orden Actual"), tempShowOrder, v -> tempShowOrder = v));

        addRenderableWidget(new EsclabToggle(mX + 10, mY + 96, 105, 14, Component.literal("Actividad"), tempShowActivity, v -> tempShowActivity = v));
        addRenderableWidget(new EsclabToggle(mX + 120, mY + 96, 105, 14, Component.literal("Altitud (▲▼)"), tempCompassShowHeight, v -> tempCompassShowHeight = v));

        addRenderableWidget(new EsclabButton(mX + 60, mY + 130, 120, 20, Component.literal("Listo"), b -> {
            showElementModal = false;
            this.init();
        }));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Dim translucent background
        g.fillGradient(0, 0, width, height, 0x990A0E17, 0xCC05080E);

        // Top banner instructions
        String title = "PERSONALIZAR INTERFAZ — ESCLAVO";
        g.drawString(font, title, (width - font.width(title)) / 2, 8, 0xFFFFD700, true);
        String sub = "Arrastra con el ratón el HUD o la Brújula para moverlos libremente";
        g.drawString(font, sub, (width - font.width(sub)) / 2, 20, 0xFFA0B0C4, false);

        // Bottom control docked background
        if (!showElementModal) {
            int panelY = height - 96;
            g.fill(0, panelY, width, height, 0xDD101520);
            g.renderOutline(0, panelY, width, 96, 0xFF35445A);
        }

        // Render preview of Main HUD
        if (tempHudVisible) {
            renderHudPreview(g, mouseX, mouseY);
        }

        // Render preview of Compass
        if (tempCompassEnabled) {
            renderCompassPreview(g, mouseX, mouseY);
        }

        // Render widgets (buttons, sliders)
        super.render(g, mouseX, mouseY, partialTick);

        // Render modal if open
        if (showElementModal) {
            renderModal(g);
        }
    }

    private void renderModal(GuiGraphics g) {
        int mX = (width - 240) / 2;
        int mY = (height - 210) / 2;
        g.fill(mX - 2, mY - 2, mX + 242, mY + 162, 0xFF0E121A);
        g.fill(mX, mY, mX + 240, mY + 160, 0xEE161D28);
        g.renderOutline(mX, mY, 240, 160, 0xFF35445A);
        g.drawString(font, "Elementos Visibles del HUD", mX + 15, mY + 8, 0xFFFFD700, false);
    }

    private void renderHudPreview(GuiGraphics g, int mouseX, int mouseY) {
        int baseW = tempHudMode == HudConfig.DisplayMode.COMPACT ? 180 : (tempHudMode == HudConfig.DisplayMode.MINIMAL ? 120 : 215);
        int baseH = tempHudMode == HudConfig.DisplayMode.COMPACT ? 34 : (tempHudMode == HudConfig.DisplayMode.MINIMAL ? 22 : 62);

        int scaledW = (int) (baseW * tempHudScale);
        int scaledH = (int) (baseH * tempHudScale);

        boolean hovered = mouseX >= tempHudX && mouseX <= tempHudX + scaledW && mouseY >= tempHudY && mouseY <= tempHudY + scaledH;
        int outlineColor = draggingHud ? 0xFFFFD700 : (hovered ? 0xFF00E5FF : 0xFF35445A);

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(tempHudX, tempHudY, 0);
        pose.scale(tempHudScale, tempHudScale, 1.0f);

        int alpha = (int) (tempHudOpacity * 255.0f) << 24;
        g.fill(0, 0, baseW, baseH, alpha | 0x161D28);
        g.renderOutline(0, 0, baseW, baseH, outlineColor);

        // Preview text
        g.drawString(font, " Esclavo (Nv.1)", 4, 4, 0xFFFFD700, false);
        g.drawString(font, "[PREVISUALIZACIÓN]", baseW - font.width("[PREVISUALIZACIÓN]") - 4, 4, 0xFF3CD058, false);

        if (tempHudMode == HudConfig.DisplayMode.FULL) {
            g.drawString(font, "12m  (-105, 65, -744)", 4, 15, 0xFFA0B0C4, false);
            g.drawString(font, "[Overworld]", baseW - font.width("[Overworld]") - 4, 15, 0xFF55FF55, false);
            g.fill(4, 26, baseW - 4, 36, 0xFF141820);
            g.fill(5, 27, baseW - 30, 35, 0xFF3CD058);
            String hpPreview = "❤ 20 / 20";
            g.drawString(font, hpPreview, 4 + (baseW - 8 - font.width(hpPreview)) / 2, 27, 0xFFFFFFFF, true);
            g.drawString(font, "Orden: Minar piedra (32)", 4, 38, 0xFFFFD700, false);
            g.drawString(font, "Trabajando activamente...", 4, 49, 0xFF8AA8D4, false);
        } else if (tempHudMode == HudConfig.DisplayMode.COMPACT) {
            g.fill(4, 16, baseW - 75, 26, 0xFF141820);
            g.fill(5, 17, baseW - 100, 25, 0xFF3CD058);
            String hpPreview = "20/20";
            g.drawString(font, hpPreview, 4 + (baseW - 75 - font.width(hpPreview)) / 2, 17, 0xFFFFFFFF, true);
            g.drawString(font, "12m", baseW - 25, 17, 0xFFA0B0C4, false);
        } else {
            g.fill(4, 13, baseW - 4, 18, 0xFF3CD058);
        }

        pose.popPose();

        if (hovered && !draggingHud && !showElementModal) {
            g.drawString(font, "Arrastrar HUD", mouseX + 8, mouseY - 10, 0xFF00E5FF, true);
        }
    }

    private void renderCompassPreview(GuiGraphics g, int mouseX, int mouseY) {
        int baseW = switch (tempCompassStyle) {
            case COMPACT -> 140;
            case FULL -> 160;
            case DISCRETE -> 60;
            case CIRCULAR -> 86;
        };
        int baseH = switch (tempCompassStyle) {
            case COMPACT -> 22;
            case FULL -> 50;
            case DISCRETE -> 24;
            case CIRCULAR -> 46;
        };

        int actualCompassX = getCompassScreenX(baseW);
        int actualCompassY = getCompassScreenY(baseH);

        int scaledW = (int) (baseW * tempCompassScale);
        int scaledH = (int) (baseH * tempCompassScale);

        boolean hovered = mouseX >= actualCompassX && mouseX <= actualCompassX + scaledW && mouseY >= actualCompassY && mouseY <= actualCompassY + scaledH;
        int outlineColor = draggingCompass ? 0xFFFFD700 : (hovered ? 0xFF00E5FF : 0xFF35445A);

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(actualCompassX, actualCompassY, 0);
        pose.scale(tempCompassScale, tempCompassScale, 1.0f);

        int alpha = (int) (tempCompassOpacity * 255.0f) << 24;
        g.fill(0, 0, baseW, baseH, alpha | 0x121824);
        g.renderOutline(0, 0, baseW, baseH, outlineColor);

        // Preview compass drawing
        switch (tempCompassStyle) {
            case CIRCULAR -> {
                int cx = 22, cy = 23, compassSize = 40;
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                float a = ((alpha >>> 24) & 0xFF) / 255.0F;
                if (a <= 0.05F) a = 1.0F;
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, a);
                g.blit(CompanionCompassOverlay.COMPASS_DIAL_TEXTURE, cx - compassSize / 2, cy - compassSize / 2, compassSize, compassSize, 0.0F, 0.0F, 256, 256, 256, 256);

                PoseStack p = g.pose();
                p.pushPose();
                p.translate(cx, cy, 0);
                p.mulPose(Axis.ZP.rotationDegrees(45.0F));
                g.blit(CompanionCompassOverlay.COMPASS_NEEDLE_TEXTURE, -compassSize / 2, -compassSize / 2, compassSize, compassSize, 0.0F, 0.0F, 256, 256, 256, 256);
                p.popPose();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

                g.drawString(font, "12m", 45, 8, 0xFFFFFFFF, true);
                g.drawString(font, "▲+2m", 45, 19, 0xFF00E5FF, false);
                g.drawString(font, "Esclavo", 45, 30, 0xFFE0E0E0, false);
            }
            case COMPACT -> {
                g.drawString(font, "↗", 6, 6, 0xFFFFD700, true);
                g.drawString(font, "Esclavo — 12m (▲+2m)", 20, 7, 0xFFFFFFFF, false);
            }
            case FULL -> {
                g.drawString(font, "↗  Esclavo", 6, 5, 0xFFFFD700, false);
                g.drawString(font, "12m  ▲+2m", baseW - 55, 5, 0xFFFFFFFF, false);
                g.drawString(font, "X: -105 Y: 65 Z: -744", 6, 18, 0xFFA0B0C4, false);
                g.drawString(font, "§aEn tiempo real (Overworld)", 6, 33, 0xFF8AA8D4, false);
            }
            case DISCRETE -> {
                g.drawString(font, "↗ 12m", 6, 8, 0xFFFFD700, true);
            }
        }

        pose.popPose();

        if (hovered && !draggingCompass && !showElementModal) {
            g.drawString(font, "Arrastrar Brújula", mouseX + 8, mouseY - 10, 0xFF00E5FF, true);
        }
    }

    private int getCompassScreenX(int baseW) {
        int scaledW = (int) (baseW * tempCompassScale);
        if (tempCompassPreset == HudConfig.CompassPreset.CUSTOM && tempCompassX >= 0) {
            return Math.max(2, Math.min(width - scaledW - 2, tempCompassX));
        }
        return switch (tempCompassPreset) {
            case TOP_LEFT, BOTTOM_LEFT -> 8;
            case TOP_CENTER, BOTTOM_CENTER -> (width - scaledW) / 2;
            case TOP_RIGHT, BOTTOM_RIGHT, CUSTOM -> width - scaledW - 8;
        };
    }

    private int getCompassScreenY(int baseH) {
        int scaledH = (int) (baseH * tempCompassScale);
        if (tempCompassPreset == HudConfig.CompassPreset.CUSTOM && tempCompassY >= 0) {
            return Math.max(2, Math.min(height - scaledH - 100, tempCompassY));
        }
        return switch (tempCompassPreset) {
            case TOP_LEFT, TOP_RIGHT, TOP_CENTER -> 8;
            case BOTTOM_LEFT, BOTTOM_RIGHT, BOTTOM_CENTER, CUSTOM -> height - scaledH - 104;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (showElementModal) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (button == 0) {
            // Check HUD clicked
            int hudBaseW = tempHudMode == HudConfig.DisplayMode.COMPACT ? 180 : (tempHudMode == HudConfig.DisplayMode.MINIMAL ? 120 : 215);
            int hudBaseH = tempHudMode == HudConfig.DisplayMode.COMPACT ? 34 : (tempHudMode == HudConfig.DisplayMode.MINIMAL ? 22 : 62);
            int hudScaledW = (int) (hudBaseW * tempHudScale);
            int hudScaledH = (int) (hudBaseH * tempHudScale);

            if (tempHudVisible && mouseX >= tempHudX && mouseX <= tempHudX + hudScaledW && mouseY >= tempHudY && mouseY <= tempHudY + hudScaledH) {
                draggingHud = true;
                dragOffsetX = (int) mouseX - tempHudX;
                dragOffsetY = (int) mouseY - tempHudY;
                return true;
            }

            // Check Compass clicked
            int compassBaseW = switch (tempCompassStyle) {
                case COMPACT -> 140;
                case FULL -> 160;
                case DISCRETE -> 60;
                case CIRCULAR -> 86;
            };
            int compassBaseH = switch (tempCompassStyle) {
                case COMPACT -> 22;
                case FULL -> 50;
                case DISCRETE -> 24;
                case CIRCULAR -> 46;
            };
            int compassActualX = getCompassScreenX(compassBaseW);
            int compassActualY = getCompassScreenY(compassBaseH);
            int compassScaledW = (int) (compassBaseW * tempCompassScale);
            int compassScaledH = (int) (compassBaseH * tempCompassScale);

            if (tempCompassEnabled && mouseX >= compassActualX && mouseX <= compassActualX + compassScaledW && mouseY >= compassActualY && mouseY <= compassActualY + compassScaledH) {
                draggingCompass = true;
                tempCompassPreset = HudConfig.CompassPreset.CUSTOM;
                tempCompassX = compassActualX;
                tempCompassY = compassActualY;
                dragOffsetX = (int) mouseX - compassActualX;
                dragOffsetY = (int) mouseY - compassActualY;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingHud) {
            int hudBaseW = tempHudMode == HudConfig.DisplayMode.COMPACT ? 180 : (tempHudMode == HudConfig.DisplayMode.MINIMAL ? 120 : 215);
            int hudBaseH = tempHudMode == HudConfig.DisplayMode.COMPACT ? 34 : (tempHudMode == HudConfig.DisplayMode.MINIMAL ? 22 : 62);
            int hudScaledW = (int) (hudBaseW * tempHudScale);
            int hudScaledH = (int) (hudBaseH * tempHudScale);

            tempHudX = Math.max(2, Math.min(width - hudScaledW - 2, (int) mouseX - dragOffsetX));
            tempHudY = Math.max(2, Math.min(height - hudScaledH - 100, (int) mouseY - dragOffsetY));
            return true;
        }

        if (draggingCompass) {
            int compassBaseW = switch (tempCompassStyle) {
                case COMPACT -> 140;
                case FULL -> 160;
                case DISCRETE -> 60;
                case CIRCULAR -> 76;
            };
            int compassBaseH = switch (tempCompassStyle) {
                case COMPACT -> 22;
                case FULL -> 50;
                case DISCRETE -> 24;
                case CIRCULAR -> 56;
            };
            int compassScaledW = (int) (compassBaseW * tempCompassScale);
            int compassScaledH = (int) (compassBaseH * tempCompassScale);

            tempCompassX = Math.max(2, Math.min(width - compassScaledW - 2, (int) mouseX - dragOffsetX));
            tempCompassY = Math.max(2, Math.min(height - compassScaledH - 100, (int) mouseY - dragOffsetY));
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingHud = false;
        draggingCompass = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parentScreen);
    }
}
