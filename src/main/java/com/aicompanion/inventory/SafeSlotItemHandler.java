package com.aicompanion.inventory;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

public class SafeSlotItemHandler extends SlotItemHandler {

    public SafeSlotItemHandler(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
        super(itemHandler, index, xPosition, yPosition);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (this.getItemHandler() == null || this.getSlotIndex() >= this.getItemHandler().getSlots()) {
            return false;
        }
        return super.mayPlace(stack);
    }

    @Override
    public ItemStack getItem() {
        if (this.getItemHandler() == null || this.getSlotIndex() < 0 || this.getSlotIndex() >= this.getItemHandler().getSlots()) {
            return ItemStack.EMPTY;
        }
        try {
            return super.getItem();
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public void set(ItemStack stack) {
        if (this.getItemHandler() != null && this.getSlotIndex() >= 0 && this.getSlotIndex() < this.getItemHandler().getSlots()) {
            try {
                super.set(stack);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public int getMaxStackSize() {
        if (this.getItemHandler() == null || this.getSlotIndex() < 0 || this.getSlotIndex() >= this.getItemHandler().getSlots()) {
            return 64;
        }
        return super.getMaxStackSize();
    }
}
