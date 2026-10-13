package com.aicompanion.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class EsclabButton extends Button {
    private boolean isGoldSelected = false;
    private OnPress actionHandler;

    public EsclabButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, b -> {}, DEFAULT_NARRATION);
        this.actionHandler = onPress;
    }

    public void setOnPress(OnPress onPress) {
        this.actionHandler = onPress;
    }

    @Override
    public void onPress() {
        if (this.actionHandler != null) {
            this.actionHandler.onPress(this);
        }
    }

    public void setGoldSelected(boolean selected) {
        this.isGoldSelected = selected;
    }

    public boolean isGoldSelected() {
        return this.isGoldSelected;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();

        boolean hovered = this.isHoveredOrFocused();
        int bgColor = !this.active ? 0xFF1E2430 : (hovered ? 0xFF3D4D65 : 0xFF2B3647);
        int topHighlight = !this.active ? 0xFF2A3240 : (hovered ? 0xFF587093 : 0xFF45556F);
        int bottomShadow = !this.active ? 0xFF141820 : 0xFF181F2A;
        int borderColor = isGoldSelected ? 0xFFE5B838 : (hovered ? 0xFF8AA8D4 : 0xFF121620);

        // Fill background
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bgColor);

        // Borders
        guiGraphics.fill(x, y, x + w, y + 1, borderColor); // Top
        guiGraphics.fill(x, y + h - 1, x + w, y + h, borderColor); // Bottom
        guiGraphics.fill(x, y, x + 1, y + h, borderColor); // Left
        guiGraphics.fill(x + w - 1, y, x + w, y + h, borderColor); // Right

        // Gold corner accents if selected
        if (isGoldSelected) {
            guiGraphics.fill(x + 1, y + 1, x + 3, y + 3, 0xFFFFE066);
            guiGraphics.fill(x + w - 3, y + 1, x + w - 1, y + 3, 0xFFFFE066);
            guiGraphics.fill(x + 1, y + h - 3, x + 3, y + h - 1, 0xFFFFE066);
            guiGraphics.fill(x + w - 3, y + h - 3, x + w - 1, y + h - 1, 0xFFFFE066);
        } else if (this.active) {
            // Inner bevel
            guiGraphics.fill(x + 1, y + 1, x + w - 1, y + 2, topHighlight);
            guiGraphics.fill(x + 1, y + 1, x + 2, y + h - 1, topHighlight);
            guiGraphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, bottomShadow);
            guiGraphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, bottomShadow);
        }

        // Centered Text
        Font font = Minecraft.getInstance().font;
        int textColor = !this.active ? 0xFF657386 : (isGoldSelected ? 0xFFFFD700 : (hovered ? 0xFFFFFFFF : 0xFFD8E2ED));
        int textX = x + (w - font.width(this.getMessage())) / 2;
        int textY = y + (h - font.lineHeight) / 2 + 1;
        guiGraphics.drawString(font, this.getMessage(), textX, textY, textColor, false);
    }
}
