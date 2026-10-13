package com.aicompanion.init;

import com.aicompanion.AICompanionMod;
import com.aicompanion.item.CompanionWhistleItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, AICompanionMod.MOD_ID);

    public static final RegistryObject<Item> COMPANION_WHISTLE =
            ITEMS.register("companion_whistle", () -> new CompanionWhistleItem(new Item.Properties().stacksTo(1)));
}
