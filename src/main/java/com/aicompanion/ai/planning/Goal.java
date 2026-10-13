package com.aicompanion.ai.planning;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public class Goal {
    public enum Type {
        OBTAIN,
        KILL,
        GO_TO,
        BUILD,
        STAY,
        FOLLOW,
        DEPOSIT,
        RETURN_OWNER,
        RETURN_HOME,
        FARM,
        GUARD,
        MAINTAIN_CHEST
    }

    private final Type type;
    private ResourceLocation targetResource;
    private String stringParam;
    private int amount = 1;
    private BlockPos targetPos;

    public Goal(Type type) {
        this.type = type;
    }

    public static Goal farm(int radius) {
        Goal g = new Goal(Type.FARM);
        g.amount = radius > 0 ? radius : 16;
        return g;
    }

    public static Goal guard(int radius) {
        Goal g = new Goal(Type.GUARD);
        g.amount = radius > 0 ? radius : 16;
        return g;
    }

    public static Goal maintainChest(BlockPos chestPos) {
        return maintainChest(chestPos, null, 0);
    }

    public static Goal maintainChest(BlockPos chestPos, ResourceLocation targetSupplyItem, int count) {
        Goal g = new Goal(Type.MAINTAIN_CHEST);
        g.targetPos = chestPos;
        g.targetResource = targetSupplyItem;
        g.amount = count;
        return g;
    }

    public static Goal obtain(ResourceLocation res, int amount) {
        return obtain(res, amount, null);
    }

    public static Goal obtain(ResourceLocation res, int amount, String stringParam) {
        Goal g = new Goal(Type.OBTAIN);
        g.targetResource = res;
        g.amount = amount;
        g.stringParam = stringParam;
        return g;
    }

    public static Goal kill(ResourceLocation entity, int amount) {
        Goal g = new Goal(Type.KILL);
        g.targetResource = entity;
        g.amount = amount;
        return g;
    }

    public static Goal goTo(BlockPos pos) {
        Goal g = new Goal(Type.GO_TO);
        g.targetPos = pos;
        return g;
    }

    public static Goal build(String blueprint) {
        Goal g = new Goal(Type.BUILD);
        g.stringParam = blueprint;
        return g;
    }

    /** Build at an explicit origin chosen by the player (interactive build dialogue). */
    public static Goal build(String blueprint, BlockPos origin) {
        Goal g = build(blueprint);
        g.targetPos = origin;
        return g;
    }

    /**
     * Return to the owner. When {@code deliver} is true the companion must also hand over
     * the items gathered by the preceding goals ("tráemelos", "dámelos", "entrégamelos").
     */
    public static Goal returnOwner(boolean deliver) {
        Goal g = new Goal(Type.RETURN_OWNER);
        if (deliver) g.stringParam = "deliver";
        return g;
    }

    public boolean isDelivery() {
        return type == Type.RETURN_OWNER && "deliver".equals(stringParam);
    }

    public Type getType() { return type; }
    public ResourceLocation getTargetResource() { return targetResource; }
    public String getStringParam() { return stringParam; }
    public int getAmount() { return amount; }
    public BlockPos getTargetPos() { return targetPos; }

    public net.minecraft.nbt.CompoundTag save() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("Type", type.name());
        if (targetResource != null) tag.putString("TargetResource", targetResource.toString());
        if (stringParam != null) tag.putString("StringParam", stringParam);
        tag.putInt("Amount", amount);
        if (targetPos != null) tag.put("TargetPos", net.minecraft.nbt.NbtUtils.writeBlockPos(targetPos));
        return tag;
    }

    public static Goal load(net.minecraft.nbt.CompoundTag tag) {
        if (!tag.contains("Type")) return null;
        try {
            Type type = Type.valueOf(tag.getString("Type"));
            Goal g = new Goal(type);
            if (tag.contains("TargetResource")) g.targetResource = new ResourceLocation(tag.getString("TargetResource"));
            if (tag.contains("StringParam")) g.stringParam = tag.getString("StringParam");
            if (tag.contains("Amount")) g.amount = tag.getInt("Amount");
            if (tag.contains("TargetPos")) g.targetPos = net.minecraft.nbt.NbtUtils.readBlockPos(tag.getCompound("TargetPos"));
            return g;
        } catch (Exception ignored) {
            return null;
        }
    }
}
