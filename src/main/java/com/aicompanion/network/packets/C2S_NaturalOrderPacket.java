package com.aicompanion.network.packets;

import com.aicompanion.data.CompanionManager;
import com.aicompanion.entity.CompanionEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class C2S_NaturalOrderPacket {
    private final int entityId;
    private final UUID companionUUID;
    private final String orderText;

    public C2S_NaturalOrderPacket(int entityId, String orderText) {
        this(entityId, null, orderText);
    }

    public C2S_NaturalOrderPacket(int entityId, UUID companionUUID, String orderText) {
        this.entityId = entityId;
        this.companionUUID = companionUUID;
        this.orderText = orderText != null ? orderText : "";
    }

    public static void encode(C2S_NaturalOrderPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeBoolean(msg.companionUUID != null);
        if (msg.companionUUID != null) {
            buf.writeUUID(msg.companionUUID);
        }
        buf.writeUtf(msg.orderText, 512);
    }

    public static C2S_NaturalOrderPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        UUID companionUUID = buf.readBoolean() ? buf.readUUID() : null;
        String orderText = buf.readUtf(512);
        return new C2S_NaturalOrderPacket(entityId, companionUUID, orderText);
    }

    public static void handle(C2S_NaturalOrderPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] Packet C2S_NaturalOrderPacket received: orderText='{}', player={}", msg.orderText, player.getName().getString());

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

            if (companion == null) {
                com.aicompanion.AICompanionMod.LOGGER.warn("[ESCLAVO-DEBUG] C2S_NaturalOrderPacket: No companion found for player {}", player.getName().getString());
                player.sendSystemMessage(Component.translatable("message.aicompanion.not_found"));
                return;
            }

            if (companion.getOwnerUUID() == null || (!companion.getOwnerUUID().equals(player.getUUID()) && !player.hasPermissions(2))) {
                com.aicompanion.AICompanionMod.LOGGER.warn("[ESCLAVO-DEBUG] C2S_NaturalOrderPacket: Player {} is not owner of companion {}", player.getName().getString(), companion.getId());
                player.sendSystemMessage(Component.translatable("message.aicompanion.not_owner"));
                return;
            }

            // Automatic raycast chest authorization for "este cofre"
            String lower = msg.orderText.toLowerCase();
            if (lower.contains("este cofre") || lower.contains("el cofre") || lower.contains("los cofres")) {
                net.minecraft.world.phys.HitResult hit = player.pick(6.0D, 0.0F, false);
                if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                    net.minecraft.core.BlockPos hitPos = ((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos();
                    net.minecraft.world.level.block.entity.BlockEntity be = player.level().getBlockEntity(hitPos);
                    boolean isContainer = (be != null && (be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).isPresent() || be instanceof net.minecraft.world.Container))
                            || player.level().getBlockState(hitPos).getBlock() instanceof net.minecraft.world.level.block.ChestBlock;
                    if (isContainer) {
                        companion.authorizeChest(hitPos, player.level().dimension().location(), player.getUUID());
                        player.sendSystemMessage(
                                Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                        .withStyle(net.minecraft.ChatFormatting.GOLD)
                                        .append(Component.literal("Cofre en " + hitPos.toShortString() + " autorizado automáticamente.").withStyle(net.minecraft.ChatFormatting.GREEN))
                        );
                    }
                }
            }

            com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] Executing natural order '{}' on companion id={}", msg.orderText, companion.getId());
            companion.getCompanionBrain().executeNaturalOrder(msg.orderText);
        });
        ctx.get().setPacketHandled(true);
    }
}
