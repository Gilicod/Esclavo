package com.aicompanion.init;

import com.aicompanion.AICompanionMod;
import com.aicompanion.inventory.CompanionMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, AICompanionMod.MOD_ID);

    public static final RegistryObject<MenuType<CompanionMenu>> COMPANION_MENU =
            MENUS.register("companion_menu", () -> IForgeMenuType.create(CompanionMenu::createClientMenu));
}
