package com.aicompanion.network.packets;

import com.aicompanion.client.gui.UnsummonedCompanionScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2C_OpenSummonScreenPacket {
    private final boolean hasSavedData;
    private final String companionName;
    private final boolean isDeadWaitingRespawn;
    private final int respawnSecondsRemaining;
    private final int totalRespawnSeconds;

    public S2C_OpenSummonScreenPacket(boolean hasSavedData, String companionName) {
        this(hasSavedData, companionName, false, 0, 60);
    }

    public S2C_OpenSummonScreenPacket(boolean hasSavedData, String companionName, boolean isDeadWaitingRespawn, int respawnSecondsRemaining, int totalRespawnSeconds) {
        this.hasSavedData = hasSavedData;
        this.companionName = companionName != null ? companionName : "Esclavo";
        this.isDeadWaitingRespawn = isDeadWaitingRespawn;
        this.respawnSecondsRemaining = Math.max(0, respawnSecondsRemaining);
        this.totalRespawnSeconds = Math.max(1, totalRespawnSeconds);
    }

    public static void encode(S2C_OpenSummonScreenPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.hasSavedData);
        buf.writeUtf(msg.companionName, 64);
        buf.writeBoolean(msg.isDeadWaitingRespawn);
        buf.writeInt(msg.respawnSecondsRemaining);
        buf.writeInt(msg.totalRespawnSeconds);
    }

    public static S2C_OpenSummonScreenPacket decode(FriendlyByteBuf buf) {
        boolean hasSaved = buf.readBoolean();
        String name = buf.readUtf(64);
        boolean isDead = false;
        int remaining = 0;
        int total = 60;
        if (buf.isReadable()) {
            isDead = buf.readBoolean();
            remaining = buf.readInt();
            total = buf.readInt();
        }
        return new S2C_OpenSummonScreenPacket(hasSaved, name, isDead, remaining, total);
    }

    public static void handle(S2C_OpenSummonScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Minecraft.getInstance().setScreen(new UnsummonedCompanionScreen(
                        msg.hasSavedData, msg.companionName, msg.isDeadWaitingRespawn, msg.respawnSecondsRemaining, msg.totalRespawnSeconds
                ));
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
