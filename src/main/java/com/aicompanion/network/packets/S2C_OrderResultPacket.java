package com.aicompanion.network.packets;

import com.aicompanion.ai.order.OrderResultStatus;
import com.aicompanion.client.gui.CompanionScreen;
import com.aicompanion.debug.EsclavoTrace;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public class S2C_OrderResultPacket {
   private final int requestId;
   private final UUID companionUUID;
   private final OrderResultStatus status;
   private final String message;
   private final int taskCount;
   private final String goalSummary;

   public S2C_OrderResultPacket(int var1, UUID var2, OrderResultStatus var3, String var4, int var5, String var6) {
      this.requestId = var1;
      this.companionUUID = var2;
      this.status = var3 != null ? var3 : OrderResultStatus.ERROR;
      this.message = var4 != null ? var4 : "";
      this.taskCount = var5;
      this.goalSummary = var6 != null ? var6 : "";
      if ("Server thread".equals(Thread.currentThread().getName())) {
         EsclavoTrace.log(
            "ORDEN-RESULTADO", "estado=" + this.status + " tareas=" + var5 + " objetivos='" + this.goalSummary + "' mensaje='" + this.message + "'"
         );
      }
   }

   public static void encode(S2C_OrderResultPacket var0, FriendlyByteBuf var1) {
      var1.writeInt(var0.requestId);
      var1.writeBoolean(var0.companionUUID != null);
      if (var0.companionUUID != null) {
         var1.writeUUID(var0.companionUUID);
      }

      var1.writeEnum(var0.status);
      var1.writeUtf(var0.message, 512);
      var1.writeInt(var0.taskCount);
      var1.writeUtf(var0.goalSummary, 512);
   }

   public static S2C_OrderResultPacket decode(FriendlyByteBuf var0) {
      int var1 = var0.readInt();
      UUID var2 = var0.readBoolean() ? var0.readUUID() : null;
      OrderResultStatus var3 = (OrderResultStatus)var0.readEnum(OrderResultStatus.class);
      String var4 = var0.readUtf(512);
      int var5 = var0.readInt();
      String var6 = var0.readUtf(512);
      return new S2C_OrderResultPacket(var1, var2, var3, var4, var5, var6);
   }

   public static void handle(S2C_OrderResultPacket var0, Supplier<Context> var1) {
      ((Context)var1.get()).enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
         if (Minecraft.getInstance().screen instanceof CompanionScreen var1x) {
            var1x.handleOrderResult(var0);
         }
      }));
      ((Context)var1.get()).setPacketHandled(true);
   }

   public int getRequestId() {
      return this.requestId;
   }

   public UUID getCompanionUUID() {
      return this.companionUUID;
   }

   public OrderResultStatus getStatus() {
      return this.status;
   }

   public String getMessage() {
      return this.message;
   }

   public int getTaskCount() {
      return this.taskCount;
   }

   public String getGoalSummary() {
      return this.goalSummary;
   }
}
