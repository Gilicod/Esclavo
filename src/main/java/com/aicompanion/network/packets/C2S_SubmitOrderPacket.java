package com.aicompanion.network.packets;

import com.aicompanion.ai.order.OrderResultStatus;
import com.aicompanion.ai.order.OrderSubmitMode;
import com.aicompanion.data.CompanionManager;
import com.aicompanion.debug.EsclavoTrace;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.network.CompanionNetworking;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent.Context;

public class C2S_SubmitOrderPacket {
   private static final Map<UUID, Long> LAST_SUBMIT_TIMES = new ConcurrentHashMap<>();
   private static final long RATE_LIMIT_MS = 250L;
   private final int requestId;
   private final int entityId;
   private final UUID companionUUID;
   private final String rawOrder;
   private final OrderSubmitMode submitMode;

   public C2S_SubmitOrderPacket(int var1, int var2, UUID var3, String var4, OrderSubmitMode var5) {
      this.requestId = var1;
      this.entityId = var2;
      this.companionUUID = var3;
      this.rawOrder = var4 != null ? var4 : "";
      this.submitMode = var5 != null ? var5 : OrderSubmitMode.REPLACE_CURRENT;
   }

   public static void encode(C2S_SubmitOrderPacket var0, FriendlyByteBuf var1) {
      var1.writeInt(var0.requestId);
      var1.writeInt(var0.entityId);
      var1.writeBoolean(var0.companionUUID != null);
      if (var0.companionUUID != null) {
         var1.writeUUID(var0.companionUUID);
      }

      var1.writeUtf(var0.rawOrder, 512);
      var1.writeEnum(var0.submitMode);
   }

   public static C2S_SubmitOrderPacket decode(FriendlyByteBuf var0) {
      int var1 = var0.readInt();
      int var2 = var0.readInt();
      UUID var3 = var0.readBoolean() ? var0.readUUID() : null;
      String var4 = var0.readUtf(512);
      OrderSubmitMode var5 = (OrderSubmitMode)var0.readEnum(OrderSubmitMode.class);
      return new C2S_SubmitOrderPacket(var1, var2, var3, var4, var5);
   }

   public static void handle(C2S_SubmitOrderPacket var0, Supplier<Context> var1) {
      ((Context)var1.get())
         .enqueueWork(
            () -> {
               ServerPlayer var2 = ((Context)var1.get()).getSender();
               if (var2 != null) {
                  EsclavoTrace.log("ORDEN", "texto='" + var0.rawOrder + "' modo=" + var0.submitMode + " jugador=" + var2.getName().getString());
                  long var3 = System.currentTimeMillis();
                  Long var5 = LAST_SUBMIT_TIMES.get(var2.getUUID());
                  if (var5 != null && var3 - var5 < 250L) {
                     CompanionNetworking.CHANNEL
                        .sendTo(
                           new S2C_OrderResultPacket(
                              var0.requestId, var0.companionUUID, OrderResultStatus.REJECTED, "Demasiadas peticiones. Espera un momento.", 0, ""
                           ),
                           var2.connection.connection,
                           NetworkDirection.PLAY_TO_CLIENT
                        );
                  } else {
                     LAST_SUBMIT_TIMES.put(var2.getUUID(), var3);
                     CompanionEntity var6 = null;
                     if (var0.companionUUID != null) {
                        var6 = CompanionManager.getCompanionByUUID(var2.getServer(), var0.companionUUID);
                     }

                     if (var6 == null && var0.entityId > 0 && var2.level().getEntity(var0.entityId) instanceof CompanionEntity var7) {
                        var6 = var7;
                     }

                     if (var6 == null) {
                        var6 = CompanionManager.getCompanionForOwner(var2.getServer(), var2.getUUID());
                     }

                     if (var6 == null) {
                        CompanionNetworking.CHANNEL
                           .sendTo(
                              new S2C_OrderResultPacket(
                                 var0.requestId, var0.companionUUID, OrderResultStatus.REJECTED, "No se encontró a tu Esclavo en este mundo.", 0, ""
                              ),
                              var2.connection.connection,
                              NetworkDirection.PLAY_TO_CLIENT
                           );
                     } else if (var6.getOwnerUUID() == null || !var6.getOwnerUUID().equals(var2.getUUID()) && !var2.hasPermissions(2)) {
                        CompanionNetworking.CHANNEL
                           .sendTo(
                              new S2C_OrderResultPacket(var0.requestId, var6.getUUID(), OrderResultStatus.REJECTED, "No eres el dueño de este Esclavo.", 0, ""),
                              var2.connection.connection,
                              NetworkDirection.PLAY_TO_CLIENT
                           );
                     } else if (var6.getCurrentState() == com.aicompanion.entity.CompanionState.DOWNED) {
                        // 1.13.9: herido (1 HP) -> no aceptar órdenes hasta reanimarlo (antes trabajaba a 1 HP y huía en bucle)
                        EsclavoTrace.log("ORDEN-RESULTADO", "estado=REJECTED motivo=DOWNED vida=" + var6.getHealth());
                        CompanionNetworking.CHANNEL
                           .sendTo(
                              new S2C_OrderResultPacket(var0.requestId, var6.getUUID(), OrderResultStatus.REJECTED,
                                 "Estoy gravemente herido. Haz clic derecho sobre mí para reanimarme y luego dame la orden.", 0, ""),
                              var2.connection.connection,
                              NetworkDirection.PLAY_TO_CLIENT
                           );
                     } else {
                        var6.getCompanionBrain().handleSubmitOrder(var0.requestId, var0.rawOrder, var0.submitMode, var2);
                     }
                  }
               }
            }
         );
      ((Context)var1.get()).setPacketHandled(true);
   }
}
