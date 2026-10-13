package com.aicompanion.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class EsclabTabButton extends Button {
    private final int tabIndex;
    private boolean activeTab;

    public EsclabTabButton(int x, int y, int width, int height, Component title, int tabIndex, boolean activeTab, OnPress onPress) {
        super(x, y, width, height, title, onPress, DEFAULT_NARRATION);
        this.tabIndex = tabIndex;
        this.activeTab = activeTab;
    }

    public void setActiveTab(boolean active) {
        this.activeTab = active;
    }

    public int getTabIndex() {
        return this.tabIndex;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();

        boolean hovered = this.isHoveredOrFocused();

        int bgColor = activeTab ? 0xFF2D384A : (hovered ? 0xFF354256 : 0xFF1C222C);
        int borderColor = activeTab ? 0xFFE5B838 : (hovered ? 0xFF6A82A5 : 0xFF121620);

        // Fill Tab Background
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bgColor);

        // Tab Borders
        guiGraphics.fill(x, y, x + w, y + 1, borderColor); // Top
        guiGraphics.fill(x, y + h - 1, x + w, y + h, borderColor); // Bottom
        guiGraphics.fill(x, y, x + 1, y + h, borderColor); // Left
        guiGraphics.fill(x + w - 1, y, x + w, y + h, borderColor); // Right

        // Gold Corner Accents if active tab
        if (activeTab) {
            guiGraphics.fill(x + 1, y + 1, x + 3, y + 3, 0xFFFFE580);
            guiGraphics.fill(x + w - 3, y + 1, x + w - 1, y + 3, 0xFFFFE580);
            guiGraphics.fill(x + 1, y + h - 3, x + 3, y + h - 1, 0xFFFFE580);
            guiGraphics.fill(x + w - 3, y + h - 3, x + w - 1, y + h - 1, 0xFFFFE580);
        }

        // Draw bracketed text: e.g. [ INVENTARIO ]
        Font font = Minecraft.getInstance().font;
        String rawText = this.getMessage().getString();
        String display = rawText.startsWith("[") ? rawText : "[" + rawText + "]";

        int textColor = activeTab ? 0xFFFFD700 : (hovered ? 0xFFFFFFFF : 0xFFB0C0D4);
        int textX = x + (w - font.width(display)) / 2;
        int textY = y + (h - font.lineHeight) / 2 + 1;
        guiGraphics.drawString(font, display, textX, textY, textColor, false);
    }
}
