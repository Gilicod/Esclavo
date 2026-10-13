package com.aicompanion.client;

import com.aicompanion.AICompanionMod;
import com.aicompanion.client.gui.CompanionHudOverlay;
import com.aicompanion.client.gui.CompanionScreen;
import com.aicompanion.client.key.ModKeyBindings;
import com.aicompanion.client.renderer.CompanionRenderer;
import com.aicompanion.init.ModEntities;
import com.aicompanion.init.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = AICompanionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.COMPANION_MENU.get(), CompanionScreen::new);
        });

        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(
                net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new com.aicompanion.client.gui.EsclabConfigScreen(parent))
        );
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.COMPANION.get(), CompanionRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyBindings.KEY_OPEN_GUI);
        event.register(ModKeyBindings.KEY_RECALL);
        event.register(ModKeyBindings.KEY_TOGGLE_HUD);
    }

    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.CHAT_PANEL.id(), "companion_hud", new CompanionHudOverlay());
        event.registerAbove(VanillaGuiOverlay.CHAT_PANEL.id(), "companion_compass", com.aicompanion.client.gui.hud.CompanionCompassOverlay.INSTANCE);
    }
}
