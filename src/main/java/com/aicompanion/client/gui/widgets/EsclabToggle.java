package com.aicompanion.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class EsclabToggle extends AbstractWidget {
    private boolean state;
    private final Consumer<Boolean> onToggle;

    public EsclabToggle(int x, int y, int width, int height, Component label, boolean initialState, Consumer<Boolean> onToggle) {
        super(x, y, width, height, label);
        this.state = initialState;
        this.onToggle = onToggle;
    }

    public boolean getState() {
        return state;
    }

    public void setState(boolean state) {
        this.state = state;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.state = !this.state;
        if (this.onToggle != null) {
            this.onToggle.accept(this.state);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();

        Font font = Minecraft.getInstance().font;

        // Label on left
        int labelY = y + (h - font.lineHeight) / 2;
        guiGraphics.drawString(font, this.getMessage(), x, labelY, 0xFFE0E8F2, false);

        // Switch pill on right
        int switchW = 38;
        int switchH = 14;
        int switchX = x + w - switchW;
        int switchY = y + (h - switchH) / 2;

        int pillBorder = 0xFF121620;
        int pillBg = 0xFF1C222C;
        guiGraphics.fill(switchX, switchY, switchX + switchW, switchY + switchH, pillBg);
        guiGraphics.renderOutline(switchX, switchY, switchW, switchH, pillBorder);

        String statusText = state ? "ON" : "OFF";
        int statusColor = state ? 0xFF3CD058 : 0xFFE04040;
        int indicatorW = 16;
        int indicatorX = state ? (switchX + switchW - indicatorW - 1) : (switchX + 1);

        guiGraphics.fill(indicatorX, switchY + 1, indicatorX + indicatorW, switchY + switchH - 1, state ? 0xFF2A8B3B : 0xFF8A2525);
        int textX = switchX + (switchW - font.width(statusText)) / 2;
        int textY = switchY + (switchH - font.lineHeight) / 2;
        guiGraphics.drawString(font, statusText, textX, textY, statusColor, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
