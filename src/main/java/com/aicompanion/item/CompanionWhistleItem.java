package com.aicompanion.item;

import com.aicompanion.data.CompanionManager;
import com.aicompanion.data.CompanionSavedData;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import com.aicompanion.entity.DefaultBehavior;
import com.aicompanion.util.SafeSpawnFinder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class CompanionWhistleItem extends Item {
    public CompanionWhistleItem(Properties properties) {
        super(properties);
    }

    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        Level level = context.getLevel();
        net.minecraft.core.BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (player instanceof ServerPlayer serverPlayer && !level.isClientSide) {
            ServerLevel serverLevel = (ServerLevel) level;
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
            boolean isContainer = (be != null && (be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).isPresent() || be instanceof net.minecraft.world.Container))
                    || level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.ChestBlock;

            if (isContainer) {
                // Check server protection
                if (!serverLevel.mayInteract(serverPlayer, pos)) {
                    serverPlayer.sendSystemMessage(Component.literal("[Esclavo] No tienes permiso en este servidor para interactuar con este almacenamiento.").withStyle(ChatFormatting.RED));
                    return net.minecraft.world.InteractionResult.FAIL;
                }

                CompanionEntity companion = CompanionManager.getCompanionForOwner(serverLevel.getServer(), serverPlayer.getUUID());
                if (companion != null && companion.isAlive()) {
                    var existingEntry = companion.getAuthorizedChestAt(pos, level);
                    if (existingEntry != null) {
                        String oldLabel = existingEntry.getFullLabel();
                        companion.unauthorizeChest(pos, level.dimension().location());
                        companion.syncToOwner();
                        serverPlayer.sendSystemMessage(
                                Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                        .withStyle(ChatFormatting.GOLD)
                                        .append(Component.literal(oldLabel + " en " + pos.toShortString() + " DESAUTORIZADO.").withStyle(ChatFormatting.YELLOW))
                        );
                    } else {
                        var created = companion.authorizeChestWithEntry(pos, level.dimension().location(), serverPlayer.getUUID());
                        String label = created != null ? created.getFullLabel() : ("Cofre en " + pos.toShortString());
                        if (serverPlayer.isShiftKeyDown()) {
                            companion.setHomePos(pos, level.dimension());
                            serverPlayer.sendSystemMessage(
                                    Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                            .withStyle(ChatFormatting.GOLD)
                                            .append(Component.literal("¡" + label + " en " + pos.toShortString() + " AUTORIZADO y marcado como ALMACÉN PRINCIPAL (★)!").withStyle(ChatFormatting.GREEN))
                            );
                        } else {
                            serverPlayer.sendSystemMessage(
                                    Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                            .withStyle(ChatFormatting.GOLD)
                                            .append(Component.literal("¡" + label + " en " + pos.toShortString() + " AUTORIZADO! Puedes configurarlo en la pestaña BAÚLES.").withStyle(ChatFormatting.GREEN))
                            );
                        }
                        companion.syncToOwner();
                    }
                    return net.minecraft.world.InteractionResult.SUCCESS;
                }
            }
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            ServerLevel serverLevel = (ServerLevel) level;
            CompanionEntity companion = CompanionManager.getCompanionForOwner(serverLevel.getServer(), serverPlayer.getUUID());

            if (serverPlayer.isShiftKeyDown()) {
                // Shift + Click: Recall behavior
                if (companion != null && companion.isAlive()) {
                    SafeSpawnFinder.SafeSpawnResult safeResult = SafeSpawnFinder.findSafeSpawnNearEntity(serverLevel, serverPlayer, companion);
                    if (safeResult.isSuccess()) {
                        Vec3 safePos = safeResult.getPosition();
                        companion.teleportTo(safePos.x, safePos.y, safePos.z);
                        SafeSpawnFinder.faceEntity(companion, serverPlayer);
                        companion.getNavigation().stop();
                        companion.setPreferredIdleBehavior(DefaultBehavior.FOLLOW);
                        companion.setCurrentState(CompanionState.FOLLOWING);
                        serverPlayer.sendSystemMessage(Component.translatable("message.aicompanion.recalled").withStyle(ChatFormatting.GREEN));
                    } else {
                        serverPlayer.sendSystemMessage(Component.translatable("message.aicompanion.no_safe_spawn").withStyle(ChatFormatting.RED));
                    }
                } else if (CompanionSavedData.get(serverLevel).hasSavedCompanion(serverPlayer.getUUID())) {
                    CompanionEntity restored = CompanionManager.restoreCompanion(serverPlayer);
                    if (restored != null) {
                        serverPlayer.sendSystemMessage(Component.translatable("message.aicompanion.restored").withStyle(ChatFormatting.GREEN));
                    }
                } else {
                    serverPlayer.sendSystemMessage(Component.translatable("message.aicompanion.not_found").withStyle(ChatFormatting.RED));
                }
            } else {
                // Normal click: Summon if inactive, recall if far, open GUI if near
                if (companion == null || !companion.isAlive()) {
                    if (CompanionSavedData.get(serverLevel).hasSavedCompanion(serverPlayer.getUUID())) {
                        CompanionEntity restored = CompanionManager.restoreCompanion(serverPlayer);
                        if (restored != null) {
                            serverPlayer.sendSystemMessage(Component.translatable("message.aicompanion.restored").withStyle(ChatFormatting.GREEN));
                        }
                    } else {
                        CompanionEntity summoned = CompanionManager.summonNewCompanion(serverPlayer, "Esclavo");
                        if (summoned != null) {
                            serverPlayer.sendSystemMessage(Component.translatable("message.aicompanion.summoned").withStyle(ChatFormatting.GREEN));
                        }
                    }
                } else {
                    double dist = companion.distanceTo(serverPlayer);
                    if (dist > 16.0) {
                        SafeSpawnFinder.SafeSpawnResult safeResult = SafeSpawnFinder.findSafeSpawnNearEntity(serverLevel, serverPlayer, companion);
                        if (safeResult.isSuccess()) {
                            Vec3 safePos = safeResult.getPosition();
                            companion.teleportTo(safePos.x, safePos.y, safePos.z);
                            SafeSpawnFinder.faceEntity(companion, serverPlayer);
                            companion.getNavigation().stop();
                            companion.setPreferredIdleBehavior(DefaultBehavior.FOLLOW);
                            companion.setCurrentState(CompanionState.FOLLOWING);
                            serverPlayer.sendSystemMessage(Component.translatable("message.aicompanion.recalled").withStyle(ChatFormatting.GREEN));
                        } else {
                            serverPlayer.sendSystemMessage(Component.translatable("message.aicompanion.no_safe_spawn").withStyle(ChatFormatting.RED));
                        }
                    } else {
                        companion.openCompanionMenu(serverPlayer);
                    }
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
