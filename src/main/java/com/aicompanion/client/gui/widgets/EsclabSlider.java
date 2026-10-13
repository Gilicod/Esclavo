package com.aicompanion.client.gui.widgets;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class EsclabSlider extends AbstractSliderButton {
    private final String prefix;
    private final double min;
    private final double max;
    private final boolean isPercent;
    private final Consumer<Double> onChange;

    public EsclabSlider(int x, int y, int width, int height, String prefix, double min, double max, double current, boolean isPercent, Consumer<Double> onChange) {
        super(x, y, width, height, Component.empty(), (Math.max(min, Math.min(max, current)) - min) / (max - min));
        this.prefix = prefix;
        this.min = min;
        this.max = max;
        this.isPercent = isPercent;
        this.onChange = onChange;
        updateMessage();
    }

    public double getCurrentValue() {
        return min + value * (max - min);
    }

    public void setCurrentValue(double val) {
        this.value = (Math.max(min, Math.min(max, val)) - min) / (max - min);
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        double val = getCurrentValue();
        if (isPercent) {
            setMessage(Component.literal(prefix + ": " + (int) (val * 100) + "%"));
        } else {
            setMessage(Component.literal(String.format(java.util.Locale.ROOT, "%s: %.2f", prefix, val)));
        }
    }

    @Override
    protected void applyValue() {
        if (onChange != null) {
            onChange.accept(getCurrentValue());
        }
    }
}
