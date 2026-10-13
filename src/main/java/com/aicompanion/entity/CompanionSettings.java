package com.aicompanion.entity;

import net.minecraft.nbt.CompoundTag;

public class CompanionSettings {
    private boolean autoFollow = true;
    private boolean autoEquip = true;
    private boolean autoEat = true;
    private boolean autoDeliver = false;
    private boolean autoReturn = true;
    private boolean allowBlockBreaking = true;
    private boolean allowBlockPlacement = true;
    private boolean allowCombat = true;
    private boolean enableChunkLoading = true;

    public CompanionSettings() {}

    public boolean isAutoFollow() { return autoFollow; }
    public void setAutoFollow(boolean autoFollow) { this.autoFollow = autoFollow; }

    public boolean isAutoEquip() { return autoEquip; }
    public void setAutoEquip(boolean autoEquip) { this.autoEquip = autoEquip; }

    public boolean isAutoEat() { return autoEat; }
    public void setAutoEat(boolean autoEat) { this.autoEat = autoEat; }

    public boolean isAutoDeliver() { return autoDeliver; }
    public void setAutoDeliver(boolean autoDeliver) { this.autoDeliver = autoDeliver; }

    public boolean isAutoReturn() { return autoReturn; }
    public void setAutoReturn(boolean autoReturn) { this.autoReturn = autoReturn; }

    public boolean isAllowBlockBreaking() { return allowBlockBreaking; }
    public void setAllowBlockBreaking(boolean allowBlockBreaking) { this.allowBlockBreaking = allowBlockBreaking; }

    public boolean isAllowBlockPlacement() { return allowBlockPlacement; }
    public void setAllowBlockPlacement(boolean allowBlockPlacement) { this.allowBlockPlacement = allowBlockPlacement; }

    public boolean isAllowCombat() { return allowCombat; }
    public void setAllowCombat(boolean allowCombat) { this.allowCombat = allowCombat; }

    public boolean isEnableChunkLoading() { return enableChunkLoading; }
    public void setEnableChunkLoading(boolean enableChunkLoading) { this.enableChunkLoading = enableChunkLoading; }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("AutoFollow", autoFollow);
        tag.putBoolean("AutoEquip", autoEquip);
        tag.putBoolean("AutoEat", autoEat);
        tag.putBoolean("AutoDeliver", autoDeliver);
        tag.putBoolean("AutoReturn", autoReturn);
        tag.putBoolean("AllowBlockBreaking", allowBlockBreaking);
        tag.putBoolean("AllowBlockPlacement", allowBlockPlacement);
        tag.putBoolean("AllowCombat", allowCombat);
        tag.putBoolean("EnableChunkLoading", enableChunkLoading);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        if (tag.contains("AutoFollow")) autoFollow = tag.getBoolean("AutoFollow");
        if (tag.contains("AutoEquip")) autoEquip = tag.getBoolean("AutoEquip");
        if (tag.contains("AutoEat")) autoEat = tag.getBoolean("AutoEat");
        if (tag.contains("AutoDeliver")) autoDeliver = tag.getBoolean("AutoDeliver");
        if (tag.contains("AutoReturn")) autoReturn = tag.getBoolean("AutoReturn");
        if (tag.contains("AllowBlockBreaking")) allowBlockBreaking = tag.getBoolean("AllowBlockBreaking");
        if (tag.contains("AllowBlockPlacement")) allowBlockPlacement = tag.getBoolean("AllowBlockPlacement");
        if (tag.contains("AllowCombat")) allowCombat = tag.getBoolean("AllowCombat");
        if (tag.contains("EnableChunkLoading")) enableChunkLoading = tag.getBoolean("EnableChunkLoading");
    }
}
