package com.aicompanion.network;

import com.aicompanion.AICompanionMod;
import com.aicompanion.network.packets.C2S_ActionPacket;
import com.aicompanion.network.packets.C2S_NaturalOrderPacket;
import com.aicompanion.network.packets.S2C_SyncCompanionDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class CompanionNetworking {
    private static final String PROTOCOL_VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AICompanionMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, C2S_ActionPacket.class, C2S_ActionPacket::encode, C2S_ActionPacket::decode, C2S_ActionPacket::handle);
        CHANNEL.registerMessage(id++, C2S_NaturalOrderPacket.class, C2S_NaturalOrderPacket::encode, C2S_NaturalOrderPacket::decode, C2S_NaturalOrderPacket::handle);
        CHANNEL.registerMessage(id++, S2C_SyncCompanionDataPacket.class, S2C_SyncCompanionDataPacket::encode, S2C_SyncCompanionDataPacket::decode, S2C_SyncCompanionDataPacket::handle);
        CHANNEL.registerMessage(id++, com.aicompanion.network.packets.S2C_OpenSummonScreenPacket.class, com.aicompanion.network.packets.S2C_OpenSummonScreenPacket::encode, com.aicompanion.network.packets.S2C_OpenSummonScreenPacket::decode, com.aicompanion.network.packets.S2C_OpenSummonScreenPacket::handle);
        CHANNEL.registerMessage(id++, com.aicompanion.network.packets.C2S_SubmitOrderPacket.class, com.aicompanion.network.packets.C2S_SubmitOrderPacket::encode, com.aicompanion.network.packets.C2S_SubmitOrderPacket::decode, com.aicompanion.network.packets.C2S_SubmitOrderPacket::handle);
        CHANNEL.registerMessage(id++, com.aicompanion.network.packets.S2C_OrderResultPacket.class, com.aicompanion.network.packets.S2C_OrderResultPacket::encode, com.aicompanion.network.packets.S2C_OrderResultPacket::decode, com.aicompanion.network.packets.S2C_OrderResultPacket::handle);
        CHANNEL.registerMessage(id++, com.aicompanion.network.packets.C2S_RequestOrderPreviewPacket.class, com.aicompanion.network.packets.C2S_RequestOrderPreviewPacket::encode, com.aicompanion.network.packets.C2S_RequestOrderPreviewPacket::decode, com.aicompanion.network.packets.C2S_RequestOrderPreviewPacket::handle);
        CHANNEL.registerMessage(id++, com.aicompanion.network.packets.S2C_OrderPreviewPacket.class, com.aicompanion.network.packets.S2C_OrderPreviewPacket::encode, com.aicompanion.network.packets.S2C_OrderPreviewPacket::decode, com.aicompanion.network.packets.S2C_OrderPreviewPacket::handle);
    }
}
