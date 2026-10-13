package com.aicompanion.network.packets;

import com.aicompanion.data.CompanionManager;
import com.aicompanion.entity.CompanionEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class C2S_RequestOrderPreviewPacket {
    private final int requestId;
    private final int entityId;
    private final UUID companionUUID;
    private final String rawOrder;

    public C2S_RequestOrderPreviewPacket(int requestId, int entityId, UUID companionUUID, String rawOrder) {
        this.requestId = requestId;
        this.entityId = entityId;
        this.companionUUID = companionUUID;
        this.rawOrder = rawOrder != null ? rawOrder : "";
    }

    public static void encode(C2S_RequestOrderPreviewPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.requestId);
        buf.writeInt(msg.entityId);
        buf.writeBoolean(msg.companionUUID != null);
        if (msg.companionUUID != null) {
            buf.writeUUID(msg.companionUUID);
        }
        buf.writeUtf(msg.rawOrder, 512);
    }

    public static C2S_RequestOrderPreviewPacket decode(FriendlyByteBuf buf) {
        int reqId = buf.readInt();
        int entId = buf.readInt();
        UUID compUUID = buf.readBoolean() ? buf.readUUID() : null;
        String order = buf.readUtf(512);
        return new C2S_RequestOrderPreviewPacket(reqId, entId, compUUID, order);
    }

    public static void handle(C2S_RequestOrderPreviewPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            CompanionEntity companion = null;
            if (msg.companionUUID != null) {
                companion = CompanionManager.getCompanionByUUID(player.getServer(), msg.companionUUID);
            }
            if (companion == null && msg.entityId > 0) {
                Entity ent = player.level().getEntity(msg.entityId);
                if (ent instanceof CompanionEntity c) {
                    companion = c;
                }
            }
            if (companion == null) {
                companion = CompanionManager.getCompanionForOwner(player.getServer(), player.getUUID());
            }

            if (companion != null) {
                companion.getCompanionBrain().handleOrderPreviewRequest(msg.requestId, msg.rawOrder, player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
