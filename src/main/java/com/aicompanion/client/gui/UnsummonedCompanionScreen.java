package com.aicompanion.client.gui;

import com.aicompanion.client.gui.hud.HudConfig;
import com.aicompanion.client.gui.widgets.EsclabButton;
import com.aicompanion.network.CompanionNetworking;
import com.aicompanion.network.packets.C2S_ActionPacket;
import com.aicompanion.network.packets.S2C_SyncCompanionDataPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class UnsummonedCompanionScreen extends Screen {

    private final boolean hasSavedData;
    private final String companionName;
    private final boolean isDeadWaitingRespawn;
    private int respawnSecondsRemaining;
    private final int totalRespawnSeconds;
    private final int panelWidth = 395;
    private final int panelHeight = 220;
    private EsclabButton btnSummon;

    public UnsummonedCompanionScreen(boolean hasSavedData, String companionName) {
        this(hasSavedData, companionName, false, 0, 60);
    }

    public UnsummonedCompanionScreen(boolean hasSavedData, String companionName, boolean isDeadWaitingRespawn, int respawnSecondsRemaining, int totalRespawnSeconds) {
        super(Component.literal("ESCLAVO"));
        this.hasSavedData = hasSavedData;
        this.companionName = companionName != null ? companionName : "Esclavo";
        this.isDeadWaitingRespawn = isDeadWaitingRespawn;
        this.respawnSecondsRemaining = Math.max(0, respawnSecondsRemaining);
        this.totalRespawnSeconds = Math.max(1, totalRespawnSeconds);
    }

    @Override
    protected void init() {
        super.init();
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;

        boolean isOp = this.minecraft != null && this.minecraft.player != null && this.minecraft.player.hasPermissions(2);
        boolean locked = isDeadWaitingRespawn && respawnSecondsRemaining > 0 && !isOp;

        String timeStr = String.format("%02d:%02d", respawnSecondsRemaining / 60, respawnSecondsRemaining % 60);
        Component summonLabel;
        if (isDeadWaitingRespawn && respawnSecondsRemaining > 0) {
            summonLabel = isOp ?
                    Component.literal("⚡ FORZAR REAPARICIÓN (ADMIN OP)") :
                    Component.literal("⏳ RECUPERÁNDOSE (" + timeStr + ")");
        } else {
            summonLabel = Component.literal("▶  INVOCAR ESCLAVO A MI POSICIÓN");
        }

        // Summon / Fast Revive button
        int btnW = 320;
        this.btnSummon = new EsclabButton(
                left + (panelWidth - btnW) / 2, top + 145, btnW, 24,
                summonLabel,
                b -> {
                    // Ensure HUD is made visible when invoking or reviving
                    HudConfig cfg = HudConfig.get();
                    if (!cfg.hudVisible) {
                        cfg.hudVisible = true;
                        cfg.save();
                        CompanionHudOverlay.isVisible = true;
                    }
                    String payload = isOp ? "ADMIN_FORCE" : "";
                    CompanionNetworking.CHANNEL.sendToServer(new C2S_ActionPacket(C2S_ActionPacket.Action.SUMMON, 0, null, payload));
                    this.onClose();
                }
        );
        this.btnSummon.active = !locked;
        this.addRenderableWidget(btnSummon);

        // Close button
        Component closeLabel = isDeadWaitingRespawn ?
                Component.literal("CERRAR (ESPERAR REAPARICIÓN)") :
                Component.literal("CERRAR");

        int closeW = 190;
        this.addRenderableWidget(new EsclabButton(
                left + (panelWidth - closeW) / 2, top + 178, closeW, 20,
                closeLabel,
                b -> this.onClose()
        ));
    }

    @Override
    public void tick() {
        super.tick();
        // Live update from server sync packets
        S2C_SyncCompanionDataPacket lastData = CompanionHudOverlay.getLastSyncedData();
        if (lastData != null) {
            if (isDeadWaitingRespawn) {
                if (lastData.respawnSecondsRemaining >= 0) {
                    this.respawnSecondsRemaining = lastData.respawnSecondsRemaining;
                }
                // If companion has respawned, automatically close this screen
                if (!"DEAD_WAITING_RESPAWN".equalsIgnoreCase(lastData.currentState) && lastData.health > 0) {
                    this.onClose();
                }
            }
        }

        if (btnSummon != null && isDeadWaitingRespawn) {
            boolean isOp = this.minecraft != null && this.minecraft.player != null && this.minecraft.player.hasPermissions(2);
            boolean locked = respawnSecondsRemaining > 0 && !isOp;
            btnSummon.active = !locked;
            if (respawnSecondsRemaining > 0) {
                String timeStr = String.format("%02d:%02d", respawnSecondsRemaining / 60, respawnSecondsRemaining % 60);
                btnSummon.setMessage(isOp ?
                        Component.literal("⚡ FORZAR REAPARICIÓN (ADMIN OP)") :
                        Component.literal("⏳ RECUPERÁNDOSE (" + timeStr + ")"));
            } else {
                btnSummon.setMessage(Component.literal("▶  REVIVIR ESCLAVO AHORA"));
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        int left = (this.width - panelWidth) / 2;
        int top = (this.height - panelHeight) / 2;

        // 1. Outer Frame with Rivets
        drawPlatePanel(guiGraphics, left, top, panelWidth, panelHeight);

        // 2. Gold Title Header
        String title = isDeadWaitingRespawn ?
                "§6§lESCLAVO — RECUPERACIÓN TRAS COMBATE" :
                "§6§lESCLAVO — ASISTENTE PERSONAL";
        guiGraphics.drawCenteredString(this.font, title, left + panelWidth / 2, top + 16, 0xFFFFD700);

        // 3. Subtitle / Status
        String status;
        if (isDeadWaitingRespawn) {
            status = "Tu Esclavo (" + companionName + ") cayó en combate y está en recuperación.";
        } else if (hasSavedData) {
            status = "Tu Esclavo (" + companionName + ") está descansando o en espera.";
        } else {
            status = "Aún no tienes ningún Esclavo activo en este mundo.";
        }
        guiGraphics.drawCenteredString(this.font, status, left + panelWidth / 2, top + 38, 0xFFE0E8F2);

        // 4. Inset Information Box
        int insetX = left + 20;
        int insetY = top + 56;
        int insetW = panelWidth - 40;
        int insetH = 75;
        drawInsetBox(guiGraphics, insetX, insetY, insetW, insetH);

        if (isDeadWaitingRespawn) {
            String timeStr = String.format("%02d:%02d", respawnSecondsRemaining / 60, respawnSecondsRemaining % 60);
            guiGraphics.drawString(this.font, "• Reaparición automática en: §e§l" + timeStr + " §7(a tu lado)", insetX + 10, insetY + 9, 0xFFFFFFFF, false);
            guiGraphics.drawString(this.font, "• Inventario y herramientas: §a100% CONSERVADOS (sin pérdidas)", insetX + 10, insetY + 23, 0xFF55FF55, false);
            guiGraphics.drawString(this.font, "• Reaparecerá solo automáticamente al terminar el contador.", insetX + 10, insetY + 37, 0xFF8AA8D4, false);
            boolean isOp = this.minecraft != null && this.minecraft.player != null && this.minecraft.player.hasPermissions(2);
            if (isOp) {
                guiGraphics.drawString(this.font, "• Modo OP: puedes forzar la reaparición inmediata abajo.", insetX + 10, insetY + 51, 0xFFFFAA00, false);
            } else {
                guiGraphics.drawString(this.font, "• Espera a que termine el temporizador para recuperarse.", insetX + 10, insetY + 51, 0xFFFFAA00, false);
            }
        } else {
            guiGraphics.drawString(this.font, "• Esclavo es tu compañero autónomo de minería, tala y combate.", insetX + 10, insetY + 12, 0xFF8AA8D4, false);
            guiGraphics.drawString(this.font, "• Cuenta con navegación 3D inteligente y gestión de inventario.", insetX + 10, insetY + 28, 0xFF8AA8D4, false);
            guiGraphics.drawString(this.font, "• Al invocarlo, reaparecerá a tu lado con todo su progreso.", insetX + 10, insetY + 44, 0xFF3CD058, false);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawPlatePanel(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        guiGraphics.fill(x, y, x + w, y + h, 0xFF141923);
        guiGraphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF1B222D);
        guiGraphics.renderOutline(x, y, w, h, 0xFFC59B27);
        guiGraphics.renderOutline(x + 2, y + 2, w - 4, h - 4, 0xFF2A3444);

        // Corner rivets
        drawRivet(guiGraphics, x + 4, y + 4);
        drawRivet(guiGraphics, x + w - 7, y + 4);
        drawRivet(guiGraphics, x + 4, y + h - 7);
        drawRivet(guiGraphics, x + w - 7, y + h - 7);
    }

    private void drawInsetBox(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        guiGraphics.fill(x, y, x + w, y + h, 0xFF0F131A);
        guiGraphics.renderOutline(x, y, w, h, 0xFF283446);
    }

    private void drawRivet(GuiGraphics guiGraphics, int rx, int ry) {
        guiGraphics.fill(rx, ry, rx + 3, ry + 3, 0xFFB8860B);
        guiGraphics.fill(rx + 1, ry + 1, rx + 2, ry + 2, 0xFFFFE57F);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
