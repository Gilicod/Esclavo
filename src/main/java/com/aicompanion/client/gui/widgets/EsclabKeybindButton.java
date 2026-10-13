package com.aicompanion.client.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class EsclabKeybindButton extends AbstractWidget {
    private final KeyMapping keyMapping;
    private final Component actionLabel;
    private boolean listening = false;
    private boolean hasConflict = false;
    private String conflictActionName = null;
    private Consumer<EsclabKeybindButton> onChange;

    public EsclabKeybindButton(int x, int y, int width, int height, Component actionLabel, KeyMapping keyMapping) {
        super(x, y, width, height, Component.empty());
        this.actionLabel = actionLabel;
        this.keyMapping = keyMapping;
        updateConflictStatus();
    }

    public void setOnChange(Consumer<EsclabKeybindButton> onChange) {
        this.onChange = onChange;
    }

    public void updateConflictStatus() {
        this.hasConflict = false;
        this.conflictActionName = null;
        if (!keyMapping.isUnbound()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options != null && mc.options.keyMappings != null) {
                for (KeyMapping other : mc.options.keyMappings) {
                    if (other != keyMapping && other.same(keyMapping)) {
                        this.hasConflict = true;
                        this.conflictActionName = Component.translatable(other.getName()).getString();
                        break;
                    }
                }
            }
        }
    }

    public boolean isListening() {
        return listening;
    }

    public void setListening(boolean listening) {
        this.listening = listening;
    }

    public KeyMapping getKeyMapping() {
        return keyMapping;
    }

    public void resetToDefault() {
        keyMapping.setToDefault();
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
        updateConflictStatus();
        if (onChange != null) onChange.accept(this);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.listening = !this.listening;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.listening) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // Cancel listening
            this.listening = false;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
            // Unassign key
            this.keyMapping.setKey(InputConstants.UNKNOWN);
        } else {
            // Assign key
            this.keyMapping.setKey(InputConstants.getKey(keyCode, scanCode));
        }

        this.listening = false;
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
        updateConflictStatus();
        if (onChange != null) onChange.accept(this);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.listening) {
            // Capture mouse click as keybind (e.g. mouse button 3, 4, 5)
            if (button != 0 && button != 1) { // 0=left click, 1=right click
                this.keyMapping.setKey(InputConstants.Type.MOUSE.getOrCreate(button));
                this.listening = false;
                KeyMapping.resetMapping();
                Minecraft.getInstance().options.save();
                updateConflictStatus();
                if (onChange != null) onChange.accept(this);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();

        boolean hovered = this.isHoveredOrFocused();
        int bgColor = listening ? 0xFF352B1E : (hovered ? 0xFF3D4D65 : 0xFF2B3647);
        int borderColor = listening ? 0xFFFFD700 : (hasConflict ? 0xFFFF6B35 : (hovered ? 0xFF8AA8D4 : 0xFF121620));

        // Background
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bgColor);

        // Border
        guiGraphics.fill(x, y, x + w, y + 1, borderColor);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, borderColor);
        guiGraphics.fill(x, y, x + 1, y + h, borderColor);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, borderColor);

        Font font = Minecraft.getInstance().font;

        // Render Action Label on left
        guiGraphics.drawString(font, actionLabel, x + 6, y + (h - 8) / 2, 0xFFE0E8F2, false);

        // Render Key status on right
        String keyText;
        int keyColor;
        if (listening) {
            keyText = "> PRESIONA UNA TECLA <";
            keyColor = 0xFFFFD700;
        } else if (keyMapping.isUnbound()) {
            keyText = "[ SIN ASIGNAR ]";
            keyColor = 0xFFA0A0A0;
        } else {
            keyText = "[ " + keyMapping.getTranslatedKeyMessage().getString() + " ]";
            keyColor = hasConflict ? 0xFFFF8844 : (keyMapping.isDefault() ? 0xFFFFFFFF : 0xFFE5B838);
        }

        int keyTextW = font.width(keyText);
        int keyX = x + w - keyTextW - 6;
        guiGraphics.drawString(font, keyText, keyX, y + (h - 8) / 2, keyColor, false);

        // Tooltip if conflict
        if (hovered && hasConflict && !listening) {
            guiGraphics.renderTooltip(font, Component.literal("Conflicto con: " + conflictActionName).withStyle(ChatFormatting.GOLD), mouseX, mouseY);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
