package com.aicompanion.entity;

import net.minecraft.nbt.CompoundTag;

public class CompanionSettings {

    public enum FoodStoragePolicy {
        RESERVE_16_STORE_REST("Reservar 16 y guardar resto en Baúl", 16),
        STORE_ALL_FOOD("Guardar toda en Baúl (reservar 4)", 4),
        DELIVER_TO_OWNER("Entregar excedente al propietario", 16),
        KEEP_IN_INVENTORY("Conservar toda en mochila", 2304);

        private final String displayName;
        private final int reserveLimit;

        FoodStoragePolicy(String displayName, int reserveLimit) {
            this.displayName = displayName;
            this.reserveLimit = reserveLimit;
        }

        public String getDisplayName() { return displayName; }
        public int getReserveLimit() { return reserveLimit; }

        public FoodStoragePolicy next() {
            FoodStoragePolicy[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }

        public static FoodStoragePolicy fromName(String name) {
            if (name == null) return RESERVE_16_STORE_REST;
            for (FoodStoragePolicy p : values()) {
                if (p.name().equalsIgnoreCase(name)) return p;
            }
            return RESERVE_16_STORE_REST;
        }
    }

    public enum AutoDepositPolicy {
        ON_FULL_OR_IDLE("Al llenarse (≤3) o volver a base"),
        ON_FULL_ONLY("Sólo mochila casi llena (≤3 libres)"),
        MANUAL_ONLY("Sólo por orden manual");

        private final String displayName;

        AutoDepositPolicy(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }

        public AutoDepositPolicy next() {
            AutoDepositPolicy[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }

        public static AutoDepositPolicy fromName(String name) {
            if (name == null) return ON_FULL_OR_IDLE;
            for (AutoDepositPolicy p : values()) {
                if (p.name().equalsIgnoreCase(name)) return p;
            }
            return ON_FULL_OR_IDLE;
        }
    }

    private boolean autoFollow = true;
    private boolean autoEquip = true;
    private boolean autoEat = true;
    private boolean autoDeliver = false;
    private boolean autoReturn = true;
    private boolean allowBlockBreaking = true;
    private boolean allowBlockPlacement = true;
    private boolean allowCombat = true;
    private boolean enableChunkLoading = true;
    private FoodStoragePolicy foodStoragePolicy = FoodStoragePolicy.RESERVE_16_STORE_REST;
    private AutoDepositPolicy autoDepositPolicy = AutoDepositPolicy.ON_FULL_OR_IDLE;

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

    public FoodStoragePolicy getFoodStoragePolicy() { return foodStoragePolicy; }
    public void setFoodStoragePolicy(FoodStoragePolicy policy) {
        this.foodStoragePolicy = policy != null ? policy : FoodStoragePolicy.RESERVE_16_STORE_REST;
    }
    public void cycleFoodStoragePolicy() {
        this.foodStoragePolicy = this.foodStoragePolicy.next();
    }

    public AutoDepositPolicy getAutoDepositPolicy() { return autoDepositPolicy; }
    public void setAutoDepositPolicy(AutoDepositPolicy policy) {
        this.autoDepositPolicy = policy != null ? policy : AutoDepositPolicy.ON_FULL_OR_IDLE;
    }
    public void cycleAutoDepositPolicy() {
        this.autoDepositPolicy = this.autoDepositPolicy.next();
    }

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
        tag.putString("FoodStoragePolicy", foodStoragePolicy.name());
        tag.putString("AutoDepositPolicy", autoDepositPolicy.name());
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
        if (tag.contains("FoodStoragePolicy")) foodStoragePolicy = FoodStoragePolicy.fromName(tag.getString("FoodStoragePolicy"));
        if (tag.contains("AutoDepositPolicy")) autoDepositPolicy = AutoDepositPolicy.fromName(tag.getString("AutoDepositPolicy"));
    }
}
