package com.aicompanion.client.gui;

import com.aicompanion.client.gui.widgets.EsclabButton;
import com.aicompanion.client.gui.widgets.EsclabKeybindButton;
import com.aicompanion.client.gui.widgets.EsclabToggle;
import com.aicompanion.client.key.ModKeyBindings;
import com.aicompanion.network.CompanionNetworking;
import com.aicompanion.network.packets.C2S_ActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class EsclabConfigScreen extends Screen {
    private final Screen lastScreen;

    public EsclabConfigScreen(Screen lastScreen) {
        super(Component.translatable("gui.aicompanion.config_title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        super.init();

        int panelW = Math.min(400, this.width - 20);
        int panelH = Math.min(330, this.height - 20);
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        Minecraft mc = Minecraft.getInstance();
        boolean inWorld = mc.player != null && mc.level != null;

        int rowY = top + 36;
        int rowH = 21;

        // 1. ATAJOS DE TECLADO
        addKeybindRow(left + 15, rowY, panelW - 30, Component.literal("Abrir Menú de Esclavo"), ModKeyBindings.KEY_OPEN_GUI);
        rowY += rowH;
        addKeybindRow(left + 15, rowY, panelW - 30, Component.literal("Llamar a Esclavo"), ModKeyBindings.KEY_RECALL);
        rowY += rowH;
        addKeybindRow(left + 15, rowY, panelW - 30, Component.literal("Alternar HUD"), ModKeyBindings.KEY_TOGGLE_HUD);
        rowY += rowH + 6;

        // 2. HUD & Compass Customization
        this.addRenderableWidget(new EsclabButton(
                left + 15, rowY, panelW - 30, 20,
                Component.literal("🎨 PERSONALIZAR HUD Y BRÚJULA..."),
                b -> Minecraft.getInstance().setScreen(new com.aicompanion.client.gui.hud.HudCustomizeScreen(this))
        ));
        rowY += rowH + 4;

        // 3. Server/Companion Options (If in-world)
        if (inWorld) {
            int halfW = (panelW - 36) / 2;
            this.addRenderableWidget(new EsclabToggle(
                    left + 15, rowY, halfW, 18,
                    Component.literal("Auto-Seguir"),
                    true,
                    val -> sendAction(C2S_ActionPacket.Action.TOGGLE_CONFIG, "auto_follow")
            ));
            this.addRenderableWidget(new EsclabToggle(
                    left + 21 + halfW, rowY, halfW, 18,
                    Component.literal("Auto-Equipar"),
                    true,
                    val -> sendAction(C2S_ActionPacket.Action.TOGGLE_CONFIG, "auto_equip")
            ));
            rowY += rowH;

            this.addRenderableWidget(new EsclabToggle(
                    left + 15, rowY, halfW, 18,
                    Component.literal("Auto-Comer"),
                    true,
                    val -> sendAction(C2S_ActionPacket.Action.TOGGLE_CONFIG, "auto_eat")
            ));
            this.addRenderableWidget(new EsclabToggle(
                    left + 21 + halfW, rowY, halfW, 18,
                    Component.literal("Romper Bloques"),
                    true,
                    val -> sendAction(C2S_ActionPacket.Action.TOGGLE_CONFIG, "allow_block_breaking")
            ));
            rowY += rowH;

            this.addRenderableWidget(new EsclabToggle(
                    left + 15, rowY, halfW, 18,
                    Component.literal("Combate Hostil"),
                    true,
                    val -> sendAction(C2S_ActionPacket.Action.TOGGLE_CONFIG, "allow_combat")
            ));
            this.addRenderableWidget(new EsclabToggle(
                    left + 21 + halfW, rowY, halfW, 18,
                    Component.literal("Carga de Chunks"),
                    true,
                    val -> sendAction(C2S_ActionPacket.Action.TOGGLE_CONFIG, "chunk_loading")
            ));
            rowY += rowH + 4;

            // Direct button to open main companion GUI
            this.addRenderableWidget(new EsclabButton(
                    left + 15, rowY, panelW - 30, 18,
                    Component.literal("Abrir Menú Principal de Esclavo"),
                    btn -> {
                        this.onClose();
                        if (mc.player != null) {
                            mc.player.connection.sendCommand("companion gui");
                        }
                    }
            ));
            rowY += rowH;
        }

        // Bottom Done / Close Button
        this.addRenderableWidget(new EsclabButton(
                left + (panelW - 140) / 2, top + panelH - 26, 140, 20,
                Component.translatable("gui.aicompanion.config.save_and_close"),
                btn -> this.onClose()
        ));
    }

    private void addKeybindRow(int x, int y, int totalW, Component label, net.minecraft.client.KeyMapping keyMapping) {
        int resetW = 20;
        int btnW = totalW - resetW - 4;
        EsclabKeybindButton keyBtn = new EsclabKeybindButton(x, y, btnW, 18, label, keyMapping);
        this.addRenderableWidget(keyBtn);
        this.addRenderableWidget(new EsclabButton(x + btnW + 4, y, resetW, 18, Component.literal("R"), b -> keyBtn.resetToDefault()));
    }

    private void sendAction(C2S_ActionPacket.Action action, String payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            CompanionNetworking.CHANNEL.sendToServer(new C2S_ActionPacket(action, -1, payload));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int panelW = Math.min(400, this.width - 20);
        int panelH = Math.min(330, this.height - 20);
        int left = (this.width - panelW) / 2;
        int top = (this.height - panelH) / 2;

        // Dark slate metallic background
        guiGraphics.fill(left, top, left + panelW, top + panelH, 0xEE1E2430);
        guiGraphics.renderOutline(left, top, panelW, panelH, 0xFF0D1117);
        guiGraphics.renderOutline(left + 1, top + 1, panelW - 2, panelH - 2, 0xFF404E67);

        // Header bar
        guiGraphics.fill(left + 2, top + 2, left + panelW - 2, top + 26, 0xFF141923);
        guiGraphics.fill(left + 2, top + 26, left + panelW - 2, top + 27, 0xFFE5B838);

        // Gold title
        Component title = Component.literal("ESCLAVO - CONFIGURACIÓN Y ATAJOS");
        int titleX = left + (panelW - this.font.width(title)) / 2;
        guiGraphics.drawString(this.font, title, titleX, top + 8, 0xFFFFD700, true);

        // If not in world, draw helpful note
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            Component note = Component.translatable("gui.aicompanion.config.no_world_note");
            int noteX = left + (panelW - this.font.width(note)) / 2;
            guiGraphics.drawString(this.font, note, noteX, top + 130, 0xFFA0B0C0, false);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.lastScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
