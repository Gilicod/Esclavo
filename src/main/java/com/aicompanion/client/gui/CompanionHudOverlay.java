package com.aicompanion.client.gui;

import com.aicompanion.AICompanionConfig;
import com.aicompanion.client.gui.hud.CompanionCompassOverlay;
import com.aicompanion.client.gui.hud.HudConfig;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.network.packets.S2C_SyncCompanionDataPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class CompanionHudOverlay implements IGuiOverlay {
    public static boolean isVisible = true;
    private static S2C_SyncCompanionDataPacket lastSyncedData = null;

    public static void updateCompanionData(S2C_SyncCompanionDataPacket data) {
        lastSyncedData = data;
        if (data != null && !"DESINVOCADO".equalsIgnoreCase(data.currentState)) {
            HudConfig cfg = HudConfig.get();
            if (!cfg.hudVisible) {
                cfg.hudVisible = true;
                cfg.save();
                isVisible = true;
            }
        }
    }

    public static S2C_SyncCompanionDataPacket getLastSyncedData() {
        return lastSyncedData;
    }

    public static void clearCompanionData() {
        lastSyncedData = null;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        HudConfig cfg = HudConfig.get();
        if (!cfg.hudVisible) return;
        // Keep Forge config in sync so neither desynchronizes
        if (AICompanionConfig.CLIENT != null && !AICompanionConfig.CLIENT.showHudOverlay.get()) {
            AICompanionConfig.CLIENT.showHudOverlay.set(true);
        }

        Minecraft mc = Minecraft.getInstance();
        if (cfg.autoHideF1 && mc.options.hideGui) return;
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        // 1. Look for nearby CompanionEntity in rendering range
        CompanionEntity nearbyComp = null;
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof CompanionEntity ce && player.getUUID().equals(ce.getOwnerUUID())) {
                if (cfg.selectedCompanionUuid.isEmpty() || ce.getStringUUID().equalsIgnoreCase(cfg.selectedCompanionUuid)) {
                    nearbyComp = ce;
                    break;
                }
            }
        }

        // If no companion entity and no sync data, or dismissed, nothing to show
        if (nearbyComp == null && (lastSyncedData == null || "DESINVOCADO".equalsIgnoreCase(lastSyncedData.currentState))) {
            if (cfg.autoHideNoCompanion) return;
        }

        // 2. Extract companion attributes (entity takes precedence, fallback to packet)
        String name = nearbyComp != null ? nearbyComp.getCustomCompanionName() : (lastSyncedData != null ? lastSyncedData.customName : "Esclavo");
        int level = nearbyComp != null ? nearbyComp.getCompanionLevel() : (lastSyncedData != null ? lastSyncedData.companionLevel : 1);
        float hp = nearbyComp != null ? nearbyComp.getHealth() : (lastSyncedData != null ? lastSyncedData.health : 20.0f);
        float maxHp = nearbyComp != null ? nearbyComp.getMaxHealth() : (lastSyncedData != null ? lastSyncedData.maxHealth : 20.0f);
        String state = nearbyComp != null ? nearbyComp.getCurrentState().name() : (lastSyncedData != null ? lastSyncedData.currentState : "IDLE");
        String substate = nearbyComp != null ? (nearbyComp.getBrain() != null && nearbyComp.getCompanionBrain().getTaskManager().getCurrentTask() != null ? nearbyComp.getCompanionBrain().getTaskManager().getCurrentTask().getSubstate() : "") : (lastSyncedData != null ? lastSyncedData.currentTaskSubstate : "");

        double compX = nearbyComp != null ? nearbyComp.getX() : (lastSyncedData != null ? lastSyncedData.posX + 0.5 : player.getX());
        double compY = nearbyComp != null ? nearbyComp.getY() : (lastSyncedData != null ? lastSyncedData.posY : player.getY());
        double compZ = nearbyComp != null ? nearbyComp.getZ() : (lastSyncedData != null ? lastSyncedData.posZ + 0.5 : player.getZ());
        String compDim = nearbyComp != null ? nearbyComp.level().dimension().location().toString() : (lastSyncedData != null ? lastSyncedData.dimension : "minecraft:overworld");
        String playerDim = player.level().dimension().location().toString();
        boolean sameDim = compDim.equalsIgnoreCase(playerDim);

        double dx = compX - player.getX();
        double dz = compZ - player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        String arrow = getDirectionArrow(dx, dz, player.getYRot());

        // 3. Layout dimensions based on mode
        int w = 215;
        int h = 62;
        if (cfg.hudMode == HudConfig.DisplayMode.COMPACT) {
            w = 180;
            h = 34;
        } else if (cfg.hudMode == HudConfig.DisplayMode.MINIMAL) {
            w = 120;
            h = 22;
        }

        float scale = cfg.hudScale;
        int scaledW = (int) (w * scale);
        int scaledH = (int) (h * scale);
        int renderX = Math.max(2, Math.min(screenWidth - scaledW - 2, cfg.hudPosX));
        int renderY = Math.max(2, Math.min(screenHeight - scaledH - 2, cfg.hudPosY));

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(renderX, renderY, 0);
        pose.scale(scale, scale, 1.0f);

        Font font = mc.font;
        int alpha = (int) (cfg.hudOpacity * 255.0f) << 24;

        if (cfg.hudOpacity > 0.02f) {
            guiGraphics.fill(-1, -1, w + 1, h + 1, alpha | 0x0E121A);
            guiGraphics.fill(0, 0, w, h, alpha | 0x161D28);
            guiGraphics.renderOutline(0, 0, w, h, (alpha != 0 ? 0xFF000000 : 0) | 0x35445A);
        }

        // Render based on display mode
        if (cfg.hudMode == HudConfig.DisplayMode.MINIMAL) {
            renderMinimalMode(guiGraphics, font, name, hp, maxHp, w, h, cfg);
        } else if (cfg.hudMode == HudConfig.DisplayMode.COMPACT) {
            renderCompactMode(guiGraphics, font, name, level, state, hp, maxHp, dist, sameDim, compDim, w, h, cfg);
        } else {
            renderFullMode(guiGraphics, font, name, level, state, substate, hp, maxHp, dist, compX, compY, compZ, sameDim, compDim, arrow, nearbyComp, player, w, h, cfg);
        }

        pose.popPose();
    }

    private void renderMinimalMode(GuiGraphics g, Font font, String name, float hp, float maxHp, int w, int h, HudConfig cfg) {
        boolean isDead = hp <= 0.0f || (lastSyncedData != null && "DEAD_WAITING_RESPAWN".equalsIgnoreCase(lastSyncedData.currentState));
        if (cfg.showName) {
            String title = isDead ? (name + " [MUERTO]") : name;
            String shortName = font.plainSubstrByWidth(title, w - 8);
            g.drawString(font, shortName, 4, 3, isDead ? 0xFFFF4444 : 0xFFFFD700, false);
        }
        if (cfg.showHealthBar) {
            int barX = 4;
            int barY = 13;
            int barW = w - 8;
            int barH = 5;
            g.fill(barX, barY, barX + barW, barY + barH, 0xFF141820);
            if (!isDead) {
                float hpFraction = Math.max(0.0f, Math.min(1.0f, hp / Math.max(1.0f, maxHp)));
                int fillW = (int) (barW * hpFraction);
                int hpColor = hpFraction > 0.5f ? 0xFF3CD058 : (hpFraction > 0.25f ? 0xFFFFD700 : 0xFFFF3333);
                if (fillW > 0) {
                    g.fill(barX, barY, barX + fillW, barY + barH, hpColor);
                }
            } else {
                g.fill(barX, barY, barX + barW, barY + barH, 0x88FF0000);
            }
        }
    }

    private void renderCompactMode(GuiGraphics g, Font font, String name, int level, String state, float hp, float maxHp,
                                   double dist, boolean sameDim, String compDim, int w, int h, HudConfig cfg) {
        boolean isDead = hp <= 0.0f || "MUERTO".equalsIgnoreCase(state) || "FALLECIDO".equalsIgnoreCase(state)
                || "DEAD_WAITING_RESPAWN".equalsIgnoreCase(state) || "RESPAWNING".equalsIgnoreCase(state);
        // Line 1: Name + Level + State
        int leftX = 4;
        if (cfg.showName) {
            Component nameComp = Component.literal(name).withStyle(isDead ? ChatFormatting.RED : ChatFormatting.GOLD);
            if (cfg.showLevel) {
                nameComp = nameComp.copy().append(Component.literal(" (Nv." + level + ")").withStyle(ChatFormatting.GRAY));
            }
            g.drawString(font, nameComp, leftX, 3, 0xFFFFFF, false);
        }
        if (cfg.showState) {
            String stateDisplay = isDead ? "[MUERTO]" : ("[" + state + "]");
            int stateColor = isDead ? 0xFFFF4444 : getStateColor(state);
            int stateW = font.width(stateDisplay);
            g.drawString(font, stateDisplay, w - stateW - 4, 3, stateColor, false);
        }

        // Line 2: Health Bar + Distance / Dimension / Respawn Countdown
        int barX = 4;
        int barY = 16;
        int barW = cfg.showDistance ? (w - 75) : (w - 8);
        int barH = 10;

        if (cfg.showHealthBar) {
            g.fill(barX, barY, barX + barW, barY + barH, 0xFF141820);
            g.renderOutline(barX, barY, barW, barH, 0xFF2A3444);

            if (isDead) {
                String countdown = (lastSyncedData != null && lastSyncedData.respawnSecondsRemaining > 0)
                        ? (lastSyncedData.respawnSecondsRemaining + "s") : "0s";
                g.drawString(font, "Respawn: " + countdown, barX + 3, barY + 1, 0xFFFF8888, true);
            } else {
                float hpFraction = Math.max(0.0f, Math.min(1.0f, hp / Math.max(1.0f, maxHp)));
                int fillW = (int) ((barW - 2) * hpFraction);
                int hpColor = hpFraction > 0.5f ? 0xFF3CD058 : (hpFraction > 0.25f ? 0xFFFFD700 : 0xFFFF3333);
                if (fillW > 0) {
                    g.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + barH - 1, hpColor);
                }
                if (cfg.showHealthNumber) {
                    String hpText = (int) hp + "/" + (int) maxHp;
                    int textW = font.width(hpText);
                    int textX = barX + (barW - textW) / 2;
                    g.drawString(font, hpText, textX, barY + 1, 0xFFFFFFFF, true);
                }
            }
        }

        if (cfg.showDistance) {
            String dimName = getFormattedDimensionName(compDim);
            String dStr = isDead ? "Caído" : (sameDim ? ((int) dist + "m") : ("[" + dimName + "]"));
            int dColor = isDead ? 0xFFFF5555 : (sameDim ? 0xFFA0B0C4 : getDimensionColor(compDim));
            g.drawString(font, dStr, w - font.width(dStr) - 4, 17, dColor, false);
        }
    }

    private void renderFullMode(GuiGraphics g, Font font, String name, int level, String state, String substate,
                                float hp, float maxHp, double dist, double compX, double compY, double compZ,
                                boolean sameDim, String compDim, String arrow, CompanionEntity nearbyComp, Player player,
                                int w, int h, HudConfig cfg) {
        // Line 1: Direction Arrow + Name + Level + State
        int curX = 4;
        if (!cfg.compassEnabled) { // Only show embedded arrow if independent compass is disabled
            g.drawString(font, arrow, curX, 4, 0xFFFFD700, false);
            curX += 12;
        }

        if (cfg.showName) {
            Component nameLine = Component.literal(name).withStyle(ChatFormatting.GOLD);
            if (cfg.showLevel) {
                nameLine = nameLine.copy().append(Component.literal(" (Nv." + level + ")").withStyle(ChatFormatting.GRAY));
            }
            g.drawString(font, nameLine, curX, 4, 0xFFFFFF, false);
        }

        if (cfg.showState) {
            String stateDisplay = "[" + state + "]";
            int stateColor = getStateColor(state);
            int stateW = font.width(stateDisplay);
            g.drawString(font, stateDisplay, w - stateW - 4, 4, stateColor, false);
        }

        // Line 2: Distance, Coordinates & Dimension
        if (cfg.showDistance || cfg.showCoords) {
            String distStr = "";
            if (sameDim) {
                if (cfg.showDistance) distStr += (int) dist + "m  ";
                if (cfg.showCoords) distStr += "(" + (int) compX + ", " + (int) compY + ", " + (int) compZ + ")";
            } else {
                distStr = "Dist. Lejana  (" + (int) compX + ", " + (int) compY + ", " + (int) compZ + ")";
            }
            g.drawString(font, distStr, 4, 15, 0xFFA0B0C4, false);
        }

        // Dimension indicator on the right side of Line 2 (isolated and never overlapping)
        String dimName = getFormattedDimensionName(compDim);
        String dimTag = "[" + dimName + "]";
        int dimColor = getDimensionColor(compDim);
        int dimW = font.width(dimTag);
        g.drawString(font, dimTag, w - dimW - 4, 15, dimColor, false);

        // Line 3: Health Bar (enlarged height and centered text)
        if (cfg.showHealthBar) {
            int barX = 4;
            int barY = 26;
            int barW = w - 8;
            int barH = 10;
            g.fill(barX, barY, barX + barW, barY + barH, 0xFF141820);
            g.renderOutline(barX, barY, barW, barH, 0xFF2A3444);

            float hpFraction = Math.max(0.0f, Math.min(1.0f, hp / Math.max(1.0f, maxHp)));
            int fillW = (int) ((barW - 2) * hpFraction);
            int hpColor = hpFraction > 0.5f ? 0xFF3CD058 : (hpFraction > 0.25f ? 0xFFFFD700 : 0xFFFF3333);
            if (fillW > 0) {
                g.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + barH - 1, hpColor);
            }
            if (cfg.showHealthNumber) {
                String hpText = "❤ " + (int) hp + " / " + (int) maxHp;
                int textW = font.width(hpText);
                int textX = barX + (barW - textW) / 2;
                g.drawString(font, hpText, textX, barY + 1, 0xFFFFFFFF, true);
            }
        }

        // Line 4 & 5: State, Active Task, or Dead/Downed Alert
        boolean isDead = hp <= 0.0f || "MUERTO".equalsIgnoreCase(state) || "FALLECIDO".equalsIgnoreCase(state)
                || "DEAD_WAITING_RESPAWN".equalsIgnoreCase(state) || "RESPAWNING".equalsIgnoreCase(state);
        boolean isDowned = "DOWNED".equalsIgnoreCase(state) || "HERIDO".equalsIgnoreCase(state);

        if (isDead) {
            String countdownText;
            if (lastSyncedData != null && lastSyncedData.respawnSecondsRemaining > 0) {
                int s = lastSyncedData.respawnSecondsRemaining;
                countdownText = String.format("Reaparición en: %02d:%02d", s / 60, s % 60);
            } else if (lastSyncedData != null && lastSyncedData.currentTaskDesc != null && lastSyncedData.currentTaskDesc.startsWith("Reaparición")) {
                countdownText = lastSyncedData.currentTaskDesc;
            } else {
                countdownText = "Reaparición en breve...";
            }
            g.drawString(font, "Estado: §c[MUERTO] §f" + countdownText, 4, 38, 0xFFFF5555, false);
            g.drawString(font, "§a✔ [Inventario conservado: 100%]", 4, 49, 0xFF55FF55, false);
        } else if (isDowned) {
            g.drawString(font, "Estado: §6¡Herido e incapacitado!", 4, 38, 0xFFFF8800, false);
            g.drawString(font, (player.tickCount % 20 < 10) ? "§c⚠ ¡Requiere auxilio urgente!" : "§eAuxílialo en sus coordenadas", 4, 49, 0xFFFF5555, false);
        } else {
            if (cfg.showOrder) {
                String activeTask = lastSyncedData != null ? lastSyncedData.currentTaskDesc : "";
                if (activeTask == null || activeTask.isEmpty() || "Ninguna".equalsIgnoreCase(activeTask)) {
                    activeTask = (nearbyComp != null && nearbyComp.getCompanionBrain() != null && nearbyComp.getCompanionBrain().getTaskManager().getCurrentTask() != null)
                            ? nearbyComp.getCompanionBrain().getTaskManager().getCurrentTask().getDescription()
                            : "En reposo";
                }

                int queueCount = (lastSyncedData != null && lastSyncedData.queueDescriptions != null) ? lastSyncedData.queueDescriptions.size() : 0;
                String stateTree = "Orden: " + activeTask + (queueCount > 0 ? " (+" + queueCount + ")" : "");
                String treeSub = font.plainSubstrByWidth(stateTree, w - 8);
                g.drawString(font, treeSub, 4, 38, 0xFFFFD700, false);
            }

            if (cfg.showActivity) {
                float hpFraction = Math.max(0.0f, Math.min(1.0f, hp / Math.max(1.0f, maxHp)));
                if (hpFraction <= 0.25f && (player.tickCount % 20 < 10)) {
                    g.drawString(font, "⚠ ¡SALUD CRÍTICA!", 4, 49, 0xFFFF3333, false);
                } else if (substate != null && !substate.isEmpty()) {
                    String subStr = font.plainSubstrByWidth(substate, w - 8);
                    g.drawString(font, subStr, 4, 49, 0xFF8AA8D4, false);
                } else {
                    g.drawString(font, "Esperando instrucciones", 4, 49, 0xFF6A7E9A, false);
                }
            }
        }
    }

    public static String getFormattedDimensionName(String dimKey) {
        if (dimKey == null || dimKey.isEmpty()) return "Overworld";
        String clean = dimKey.toLowerCase();
        if (clean.contains("the_nether") || clean.contains(":nether") || clean.equals("nether")) {
            return "Nether";
        }
        if (clean.contains("the_end") || clean.contains(":end") || clean.equals("end")) {
            return "The End";
        }
        if (clean.contains("overworld")) {
            return "Overworld";
        }
        String path = dimKey.contains(":") ? dimKey.split(":")[1] : dimKey;
        String[] parts = path.replace('_', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
        }
        return sb.length() > 0 ? sb.toString() : "Overworld";
    }

    public static int getDimensionColor(String dimKey) {
        if (dimKey == null) return 0xFF55FF55;
        String clean = dimKey.toLowerCase();
        if (clean.contains("nether")) {
            return 0xFFFF5555; // Red for Nether
        }
        if (clean.contains("end")) {
            return 0xFFFF55FF; // Magenta/Purple for The End
        }
        if (clean.contains("overworld")) {
            return 0xFF55FF55; // Green for Overworld
        }
        return 0xFF55FFFF; // Aqua for modded dimensions
    }

    public static String getDirectionArrow(double dx, double dz, float playerYaw) {
        double angleRad = Math.atan2(dz, dx);
        double angleDeg = Math.toDegrees(angleRad) - 90.0;
        double relative = (angleDeg - playerYaw) % 360.0;
        if (relative < -180.0) relative += 360.0;
        if (relative > 180.0) relative -= 360.0;

        return CompanionCompassOverlay.getDirectionGlyph(relative);
    }

    private static int getStateColor(String state) {
        if (state == null) return 0xFFA0A0A0;
        return switch (state.toUpperCase()) {
            case "FOLLOWING" -> 0xFF3CD058;
            case "MINING" -> 0xFFFFAA00;
            case "CHOPPING" -> 0xFFD89B50;
            case "BUILDING" -> 0xFF00E5FF;
            case "COMBAT", "ATTACKING", "FIGHTING" -> 0xFFFF4040;
            case "DANGER", "FLEEING" -> 0xFFFF8800;
            case "HEALING" -> 0xFF55FF55;
            case "STUCK" -> 0xFFFF1111;
            case "STAYING" -> 0xFFFFD700;
            case "DOWNED", "HERIDO" -> 0xFFFF8800;
            case "MUERTO", "FALLECIDO", "DEAD_WAITING_RESPAWN", "RESPAWNING" -> 0xFFFF4444;
            default -> 0xFFA0A0A0;
        };
    }
}
