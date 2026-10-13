package com.aicompanion.client.key;

import com.aicompanion.AICompanionConfig;
import com.aicompanion.AICompanionMod;
import com.aicompanion.network.CompanionNetworking;
import com.aicompanion.network.packets.C2S_ActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AICompanionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientKeyHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        // 1. Open Companion GUI Shortcut
        boolean guiOpened = false;
        while (ModKeyBindings.KEY_OPEN_GUI.consumeClick()) {
            guiOpened = true;
            var lastData = com.aicompanion.client.gui.CompanionHudOverlay.getLastSyncedData();
            int entId = lastData != null ? lastData.entityId : 0;
            java.util.UUID compUUID = lastData != null ? lastData.companionUUID : null;
            CompanionNetworking.CHANNEL.sendToServer(new C2S_ActionPacket(C2S_ActionPacket.Action.OPEN_GUI, entId, compUUID, ""));
        }

        // 2. Fast Recall Shortcut
        while (ModKeyBindings.KEY_RECALL.consumeClick()) {
            var lastData = com.aicompanion.client.gui.CompanionHudOverlay.getLastSyncedData();
            int entId = lastData != null ? lastData.entityId : 0;
            java.util.UUID compUUID = lastData != null ? lastData.companionUUID : null;
            CompanionNetworking.CHANNEL.sendToServer(new C2S_ActionPacket(C2S_ActionPacket.Action.RECALL, entId, compUUID, ""));
        }

        // 3. Toggle HUD Overlay Shortcut
        // Prevent accidental toggle when KEY_TOGGLE_HUD shares the same key as KEY_OPEN_GUI
        if (guiOpened && ModKeyBindings.KEY_TOGGLE_HUD.getKey().getValue() == ModKeyBindings.KEY_OPEN_GUI.getKey().getValue()) {
            while (ModKeyBindings.KEY_TOGGLE_HUD.consumeClick()) {
                // Consume without toggling HUD
            }
        } else if (ModKeyBindings.KEY_TOGGLE_HUD.consumeClick()) {
            com.aicompanion.client.gui.hud.HudConfig hudCfg = com.aicompanion.client.gui.hud.HudConfig.get();
            boolean newVal = !hudCfg.hudVisible;
            hudCfg.hudVisible = newVal;
            com.aicompanion.client.gui.CompanionHudOverlay.isVisible = newVal;
            hudCfg.save();
            AICompanionConfig.CLIENT.showHudOverlay.set(newVal);
            mc.gui.getChat().addMessage(
                    Component.literal("§6[Esclavo] §fHUD en pantalla: " + (newVal ? "§aActivado" : "§cDesactivado"))
            );
        }
    }
}
