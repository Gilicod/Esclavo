package com.aicompanion.debug;

import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;

public final class EsclavoClientTrace {
   private EsclavoClientTrace() {
   }

   public static void register() {
      MinecraftForge.EVENT_BUS.addListener(EsclavoClientTrace::onChatReceived);
      MinecraftForge.EVENT_BUS.addListener(EsclavoClientTrace::onChatSent);
   }

   private static void onChatReceived(ClientChatReceivedEvent var0) {
      try {
         if (var0.getMessage() != null) {
            EsclavoTrace.log("CHAT", var0.getMessage().getString());
         }
      } catch (Throwable var2) {
      }
   }

   private static void onChatSent(ClientChatEvent var0) {
      try {
         EsclavoTrace.log("CHAT-YO", var0.getOriginalMessage());
      } catch (Throwable var2) {
      }
   }
}
