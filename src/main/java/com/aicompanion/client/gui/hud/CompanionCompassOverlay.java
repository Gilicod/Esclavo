package com.aicompanion.client.gui.hud;

import com.aicompanion.AICompanionMod;
import com.aicompanion.client.gui.CompanionHudOverlay;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.network.packets.S2C_SyncCompanionDataPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Independent directional compass overlay for Esclavo.
 * Dynamically computes relative yaw angle, horizontal distance, height difference,
 * dimension verification, and multi-style rendering (Circular, Compact, Full, Discrete).
 */
public class CompanionCompassOverlay implements IGuiOverlay {
    public static final CompanionCompassOverlay INSTANCE = new CompanionCompassOverlay();

    public static final ResourceLocation COMPASS_DIAL_TEXTURE = new ResourceLocation(AICompanionMod.MOD_ID, "textures/gui/compass_dial.png");
    public static final ResourceLocation COMPASS_NEEDLE_TEXTURE = new ResourceLocation(AICompanionMod.MOD_ID, "textures/gui/compass_needle.png");

    private float smoothedAngle = 0.0f;

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        HudConfig cfg = HudConfig.get();
        if (!cfg.compassEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (cfg.autoHideF1 && mc.options.hideGui) return;
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        // 1. Locate tracked companion entity or packet data
        CompanionEntity trackedEntity = null;
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof CompanionEntity ce && player.getUUID().equals(ce.getOwnerUUID())) {
                if (cfg.selectedCompanionUuid.isEmpty() || ce.getStringUUID().equalsIgnoreCase(cfg.selectedCompanionUuid)) {
                    trackedEntity = ce;
                    break;
                }
            }
        }

        S2C_SyncCompanionDataPacket lastData = CompanionHudOverlay.getLastSyncedData();
        if (trackedEntity == null && (lastData == null || "DESINVOCADO".equalsIgnoreCase(lastData.currentState))) {
            if (cfg.autoHideNoCompanion) return;
        }

        // 2. Extract position & dimension
        String name = trackedEntity != null ? trackedEntity.getCustomCompanionName() : (lastData != null ? lastData.customName : "Esclavo");
        String compState = trackedEntity != null ? trackedEntity.getCurrentState().name() : (lastData != null ? lastData.currentState : "DESCONOCIDO");
        double compX = trackedEntity != null ? trackedEntity.getX() : (lastData != null ? lastData.posX + 0.5 : player.getX());
        double compY = trackedEntity != null ? trackedEntity.getY() : (lastData != null ? lastData.posY : player.getY());
        double compZ = trackedEntity != null ? trackedEntity.getZ() : (lastData != null ? lastData.posZ + 0.5 : player.getZ());
        String compDim = trackedEntity != null ? trackedEntity.level().dimension().location().toString() : (lastData != null ? lastData.dimension : "minecraft:overworld");
        String playerDim = player.level().dimension().location().toString();
        boolean sameDim = compDim.equalsIgnoreCase(playerDim);

        boolean isDead = (trackedEntity != null && trackedEntity.getHealth() <= 0.0f)
                || (lastData != null && lastData.health <= 0.0f)
                || "MUERTO".equalsIgnoreCase(compState)
                || "FALLECIDO".equalsIgnoreCase(compState)
                || "DEAD_WAITING_RESPAWN".equalsIgnoreCase(compState)
                || "RESPAWNING".equalsIgnoreCase(compState);
        boolean isDowned = "DOWNED".equalsIgnoreCase(compState) || "HERIDO".equalsIgnoreCase(compState);

        // 3. Directional math
        double dx = compX - player.getX();
        double dz = compZ - player.getZ();
        double dy = compY - player.getY();
        double dist = Math.sqrt(dx * dx + dz * dz);

        double targetAngleDeg = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double relativeAngle = (targetAngleDeg - player.getYRot()) % 360.0;
        if (relativeAngle < -180.0) relativeAngle += 360.0;
        if (relativeAngle > 180.0) relativeAngle -= 360.0;

        smoothedAngle = Mth.rotLerp(0.30f, smoothedAngle, (float) relativeAngle);

        String arrow = getDirectionGlyph(smoothedAngle);
        String heightInd = Math.abs(dy) >= 2.0 ? (dy > 0 ? " ▲+" + (int) dy : " ▼" + (int) dy) : "";

        // 4. Dimension name formatting
        String dimDisplayName = compDim.contains(":") ? compDim.split(":")[1] : compDim;
        dimDisplayName = Character.toUpperCase(dimDisplayName.charAt(0)) + dimDisplayName.substring(1);

        // 5. Layout dimensions based on style
        int baseW;
        int baseH;
        switch (cfg.compassStyle) {
            case COMPACT -> {
                baseW = 140;
                baseH = 22;
            }
            case FULL -> {
                baseW = 160;
                baseH = 50;
            }
            case DISCRETE -> {
                baseW = 60;
                baseH = 24;
            }
            case CIRCULAR -> {
                baseW = 86;
                baseH = 46;
            }
            default -> {
                baseW = 80;
                baseH = 40;
            }
        }

        float scale = cfg.compassScale;
        int scaledW = (int) (baseW * scale);
        int scaledH = (int) (baseH * scale);

        int renderX = calculateRenderX(cfg, screenWidth, scaledW);
        int renderY = calculateRenderY(cfg, screenHeight, scaledH);

        // 6. Draw with PoseStack scale
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(renderX, renderY, 0);
        pose.scale(scale, scale, 1.0f);

        int alpha = (int) (cfg.compassOpacity * 255.0f) << 24;
        Font font = mc.font;

        switch (cfg.compassStyle) {
            case CIRCULAR -> renderCircularCompass(guiGraphics, font, name, dist, sameDim, dimDisplayName, heightInd, isDead, isDowned, alpha, trackedEntity != null);
            case COMPACT -> renderCompactCompass(guiGraphics, font, name, arrow, dist, sameDim, dimDisplayName, heightInd, isDead, isDowned, alpha);
            case FULL -> renderFullLocator(guiGraphics, font, name, arrow, dist, sameDim, dimDisplayName, heightInd, compX, compY, compZ, compState, isDead, isDowned, alpha, trackedEntity != null);
            case DISCRETE -> renderDiscreteCompass(guiGraphics, font, arrow, dist, sameDim, alpha);
        }

        pose.popPose();
    }

    private void renderCircularCompass(GuiGraphics g, Font font, String name, double dist, boolean sameDim,
                                       String dimName, String heightInd, boolean isDead, boolean isDowned, int alpha, boolean isLive) {
        int w = 86;
        int h = 46;
        g.fill(0, 0, w, h, alpha | 0x121824);
        g.renderOutline(0, 0, w, h, 0xFF35445A);

        int cx = 22;
        int cy = 23;
        int compassSize = 40;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float a = ((alpha >>> 24) & 0xFF) / 255.0F;
        if (a <= 0.05F) a = 1.0F;
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, a);

        // 1. Render pixel-art obsidian/gold dial background
        g.blit(COMPASS_DIAL_TEXTURE, cx - compassSize / 2, cy - compassSize / 2, compassSize, compassSize, 0.0F, 0.0F, 256, 256, 256, 256);

        if (sameDim) {
            // 2. Rotating glowing diamond crystal needle towards companion
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(cx, cy, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(smoothedAngle));

            if (isDead) {
                RenderSystem.setShaderColor(1.0F, 0.3F, 0.3F, a);
            } else if (isDowned) {
                RenderSystem.setShaderColor(1.0F, 0.6F, 0.2F, a);
            } else {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, a);
            }

            g.blit(COMPASS_NEEDLE_TEXTURE, -compassSize / 2, -compassSize / 2, compassSize, compassSize, 0.0F, 0.0F, 256, 256, 256, 256);
            pose.popPose();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            // 3. Information on the right side
            String dStr = (int) dist + "m";
            g.drawString(font, dStr, 45, 8, 0xFFFFFFFF, true);
            if (HudConfig.get().compassShowHeight && !heightInd.isEmpty()) {
                g.drawString(font, heightInd.trim(), 45, 19, 0xFF00E5FF, false);
            }
            if (HudConfig.get().compassShowName) {
                String label = isDead ? "§c[Caído]" : (isDowned ? "§6[Herido]" : (!isLive ? "§7(Últ. pos)" : name));
                String sub = font.plainSubstrByWidth(label, w - 46);
                g.drawString(font, sub, 45, 30, 0xFFE0E0E0, false);
            }
        } else {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            // Cross-dimension portal icon over dial center
            g.drawString(font, "🌐", cx - 4, cy - 4, 0xFFA055FF, true);

            g.drawString(font, font.plainSubstrByWidth(dimName, w - 46), 45, 12, 0xFFA055FF, false);
            if (HudConfig.get().compassShowName) {
                String sub = font.plainSubstrByWidth(name, w - 46);
                g.drawString(font, sub, 45, 26, 0xFFE0E0E0, false);
            }
        }
    }

    private void renderCompactCompass(GuiGraphics g, Font font, String name, String arrow, double dist,
                                      boolean sameDim, String dimName, String heightInd, boolean isDead, boolean isDowned, int alpha) {
        int w = 140;
        int h = 22;
        g.fill(0, 0, w, h, alpha | 0x121824);
        g.renderOutline(0, 0, w, h, 0xFF35445A);

        int arrowColor = isDead ? 0xFFFF4444 : (isDowned ? 0xFFFF8800 : 0xFFFFD700);
        if (sameDim) {
            g.drawString(font, arrow, 6, 6, arrowColor, true);
            String info = name + " — " + (int) dist + "m" + (HudConfig.get().compassShowHeight ? heightInd : "");
            g.drawString(font, font.plainSubstrByWidth(info, w - 24), 20, 7, 0xFFFFFFFF, false);
        } else {
            g.drawString(font, "🌐", 6, 6, 0xFFA055FF, false);
            g.drawString(font, name + " — [" + dimName + "]", 20, 7, 0xFFA055FF, false);
        }
    }

    private void renderFullLocator(GuiGraphics g, Font font, String name, String arrow, double dist,
                                   boolean sameDim, String dimName, String heightInd,
                                   double x, double y, double z, String state, boolean isDead, boolean isDowned, int alpha, boolean isLive) {
        int w = 160;
        int h = 50;
        g.fill(0, 0, w, h, alpha | 0x121824);
        g.renderOutline(0, 0, w, h, 0xFF35445A);

        int arrowColor = isDead ? 0xFFFF4444 : (isDowned ? 0xFFFF8800 : 0xFFFFD700);

        // Line 1: Arrow + Name + Dimension
        if (sameDim) {
            g.drawString(font, arrow, 6, 5, arrowColor, true);
            g.drawString(font, " " + name, 16, 5, 0xFFFFD700, false);
            g.drawString(font, (int) dist + "m" + (HudConfig.get().compassShowHeight ? heightInd : ""), w - font.width((int) dist + "m" + heightInd) - 6, 5, 0xFFFFFFFF, false);
        } else {
            g.drawString(font, "🌐", 6, 5, 0xFFA055FF, false);
            g.drawString(font, " " + name, 16, 5, 0xFFFFD700, false);
            g.drawString(font, "[" + dimName + "]", w - font.width("[" + dimName + "]") - 6, 5, 0xFFA055FF, false);
        }

        // Line 2: Coordinates
        if (HudConfig.get().compassShowCoords) {
            String coordStr = "X: " + (int) x + "  Y: " + (int) y + "  Z: " + (int) z;
            g.drawString(font, coordStr, 6, 18, 0xFFA0B0C4, false);
        }

        // Line 3: Tracking state / Last known location
        String status;
        if (!sameDim) {
            status = "§5En otra dimensión (" + dimName + ")";
        } else if (isDead) {
            status = "§cCaído en combate";
        } else if (isDowned) {
            status = "§6Incapacitado / Requiere auxilio";
        } else if (!isLive) {
            status = "§7Última ubicación conocida";
        } else {
            status = "§aEn tiempo real (" + state + ")";
        }
        g.drawString(font, font.plainSubstrByWidth(status, w - 12), 6, 33, 0xFF8AA8D4, false);
    }

    private void renderDiscreteCompass(GuiGraphics g, Font font, String arrow, double dist, boolean sameDim, int alpha) {
        int w = 60;
        int h = 24;
        g.fill(0, 0, w, h, alpha | 0x121824);
        g.renderOutline(0, 0, w, h, 0xFF35445A);

        if (sameDim) {
            g.drawString(font, arrow, 6, 7, 0xFFFFD700, true);
            g.drawString(font, (int) dist + "m", 20, 8, 0xFFFFFFFF, false);
        } else {
            g.drawString(font, "🌐", 6, 7, 0xFFA055FF, false);
            g.drawString(font, "DIM", 24, 8, 0xFFA055FF, false);
        }
    }

    public static String getDirectionGlyph(double relative) {
        if (relative >= -22.5 && relative <= 22.5) return "▲";
        if (relative > 22.5 && relative <= 67.5) return "↗";
        if (relative > 67.5 && relative <= 112.5) return "▶";
        if (relative > 112.5 && relative <= 157.5) return "↘";
        if (relative < -22.5 && relative >= -67.5) return "↖";
        if (relative < -67.5 && relative >= -112.5) return "◀";
        if (relative < -112.5 && relative >= -157.5) return "↙";
        return "▼";
    }

    private int calculateRenderX(HudConfig cfg, int screenW, int itemW) {
        if (cfg.compassPreset == HudConfig.CompassPreset.CUSTOM && cfg.compassPosX >= 0) {
            return Math.max(2, Math.min(screenW - itemW - 2, cfg.compassPosX));
        }
        return switch (cfg.compassPreset) {
            case TOP_LEFT, BOTTOM_LEFT -> 8;
            case TOP_CENTER, BOTTOM_CENTER -> (screenW - itemW) / 2;
            case TOP_RIGHT, BOTTOM_RIGHT, CUSTOM -> screenW - itemW - 8;
        };
    }

    private int calculateRenderY(HudConfig cfg, int screenH, int itemH) {
        if (cfg.compassPreset == HudConfig.CompassPreset.CUSTOM && cfg.compassPosY >= 0) {
            return Math.max(2, Math.min(screenH - itemH - 2, cfg.compassPosY));
        }
        return switch (cfg.compassPreset) {
            case TOP_LEFT, TOP_RIGHT, TOP_CENTER -> 8;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenH - itemH - 8;
            case BOTTOM_CENTER -> screenH - itemH - 45;
            case CUSTOM -> 8;
        };
    }
}
