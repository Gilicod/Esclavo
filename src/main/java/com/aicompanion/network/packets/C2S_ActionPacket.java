package com.aicompanion.network.packets;

import com.aicompanion.ai.parser.SpanishAliases;
import com.aicompanion.ai.tasks.*;
import com.aicompanion.data.CompanionManager;
import com.aicompanion.data.CompanionSavedData;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionSettings;
import com.aicompanion.entity.DefaultBehavior;
import com.aicompanion.inventory.CompanionMenu;
import com.aicompanion.ai.mining.MiningTarget;
import com.aicompanion.inventory.EquipmentManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import com.aicompanion.ai.CompanionTask;
import com.aicompanion.ai.planning.Goal;
import com.aicompanion.ai.planning.PrerequisitePlanner;

public class C2S_ActionPacket {
    public enum Action {
        PAUSE, RESUME, STOP, FOLLOW, STAY, HOME, SET_HOME, RECALL,
        MINE, CHOP, KILL, BUILD, CLEAR_QUEUE, REMOVE_QUEUE_INDEX,
        TOGGLE_AUTO_EQUIP, TOGGLE_AUTO_EAT, GOTO, DEPOSIT, WITHDRAW, TOGGLE_CONFIG, REPLAN,
        EQUIP_BEST, TEST_LLM_CONNECTION, SUMMON, DISMISS, OPEN_GUI, SET_NAME,
        ORGANIZE_INVENTORY,
        CHEST_AUTHORIZE_NEARBY, CHEST_UNAUTHORIZE, CHEST_CYCLE_CATEGORY, CHEST_RENAME,
        CHEST_SET_PRIMARY, CHEST_DEPOSIT_TO, CYCLE_FOOD_POLICY, CYCLE_AUTODEPOSIT_POLICY,
        CYCLE_AI_AUTONOMY, CYCLE_AI_PERSONALITY, CLEAR_DANGER_ZONES
    }

    private final Action action;
    private final int entityId;
    private final UUID companionUUID;
    private final String payload;

    public C2S_ActionPacket(Action action, int entityId) {
        this(action, entityId, null, "");
    }

    public C2S_ActionPacket(Action action, int entityId, String payload) {
        this(action, entityId, null, payload);
    }

    public C2S_ActionPacket(Action action, int entityId, UUID companionUUID, String payload) {
        this.action = action;
        this.entityId = entityId;
        this.companionUUID = companionUUID;
        this.payload = payload != null ? payload : "";
    }

    public static void encode(C2S_ActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeInt(msg.entityId);
        buf.writeBoolean(msg.companionUUID != null);
        if (msg.companionUUID != null) {
            buf.writeUUID(msg.companionUUID);
        }
        buf.writeUtf(msg.payload, 1024);
    }

    public static C2S_ActionPacket decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        int entityId = buf.readInt();
        UUID companionUUID = buf.readBoolean() ? buf.readUUID() : null;
        String payload = buf.readUtf(1024);
        return new C2S_ActionPacket(action, entityId, companionUUID, payload);
    }

    public static void handle(C2S_ActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] Packet C2S_ActionPacket received: action={}, payload='{}', player={}", msg.action, msg.payload, player.getName().getString());

            // 1. Resolve companion across dimensions via CompanionManager or entity lookup
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

            com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] C2S_ActionPacket companion found: id={}, state={}", (companion != null ? companion.getId() : -1), (companion != null ? companion.getCurrentState() : "null"));

            if (companion == null) {
                if (msg.action == Action.SUMMON) {
                    net.minecraft.nbt.CompoundTag savedTag = CompanionSavedData.get(player.serverLevel()).getSavedCompanion(player.getUUID());
                    boolean wasDead = savedTag != null && com.aicompanion.entity.CompanionState.DEAD_WAITING_RESPAWN.name().equals(savedTag.getString("State"));
                    boolean forceAdmin = "ADMIN_FORCE".equalsIgnoreCase(msg.payload) && player.hasPermissions(2);
                    CompanionEntity spawned = CompanionManager.restoreCompanion(player, forceAdmin);
                    if (spawned != null) {
                        spawned.openCompanionMenu(player);
                        String notice = forceAdmin ? "¡Reaparición forzada por administrador!" : (wasDead ? "¡Reaparecido tras recuperarse en combate!" : "¡Invocado a tu posición!");
                        player.sendSystemMessage(
                                Component.literal("[" + spawned.getCustomCompanionName() + "] ")
                                        .withStyle(net.minecraft.ChatFormatting.GOLD)
                                        .append(Component.literal(notice).withStyle(net.minecraft.ChatFormatting.GREEN))
                        );
                    }
                    return;
                }
                if (msg.action == Action.OPEN_GUI) {
                    net.minecraft.nbt.CompoundTag tag = CompanionSavedData.get(player.serverLevel()).getSavedCompanion(player.getUUID());
                    boolean hasSaved = tag != null;
                    String compName = (tag != null && tag.contains("CustomCompanionName")) ? tag.getString("CustomCompanionName") : "Esclavo";
                    boolean isDead = tag != null && com.aicompanion.entity.CompanionState.DEAD_WAITING_RESPAWN.name().equals(tag.getString("State"));
                    int remainingSecs = 0;
                    int totalSecs = 60;
                    if (isDead) {
                        net.minecraft.server.level.ServerLevel overworld = player.serverLevel().getServer().overworld();
                        long now = overworld != null ? overworld.getGameTime() : player.serverLevel().getGameTime();
                        long respawnGameTime = tag.getLong("RespawnGameTime");
                        remainingSecs = (int) Math.max(0, Math.ceil((respawnGameTime - now) / 20.0));
                        totalSecs = tag.contains("TotalRespawnSeconds") ? tag.getInt("TotalRespawnSeconds") : 60;
                    }
                    com.aicompanion.network.CompanionNetworking.CHANNEL.send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                            new S2C_OpenSummonScreenPacket(hasSaved, compName, isDead, remainingSecs, totalSecs)
                    );
                    return;
                }
                net.minecraft.nbt.CompoundTag savedTag = CompanionSavedData.get(player.serverLevel()).getSavedCompanion(player.getUUID());
                if (savedTag != null && com.aicompanion.entity.CompanionState.DEAD_WAITING_RESPAWN.name().equals(savedTag.getString("State"))) {
                    String name = savedTag.contains("CustomCompanionName") ? savedTag.getString("CustomCompanionName") : "Esclavo";
                    player.sendSystemMessage(Component.literal("[" + name + "] ")
                            .withStyle(net.minecraft.ChatFormatting.GOLD)
                            .append(Component.literal("Está esperando su reaparición automática. No puede recibir acciones ahora.").withStyle(net.minecraft.ChatFormatting.RED)));
                    return;
                }

                player.sendSystemMessage(Component.translatable("message.aicompanion.not_found"));
                return;
            }

            // 2. Strict authorization: sender must be owner or OP
            if (companion.getOwnerUUID() == null || (!companion.getOwnerUUID().equals(player.getUUID()) && !player.hasPermissions(2))) {
                player.sendSystemMessage(Component.translatable("message.aicompanion.not_owner"));
                return;
            }

            // Block movement and work orders if downed or waiting respawn
            if (companion.getCurrentState() == com.aicompanion.entity.CompanionState.DOWNED ||
                companion.getCurrentState() == com.aicompanion.entity.CompanionState.DEAD_WAITING_RESPAWN ||
                companion.getCurrentState() == com.aicompanion.entity.CompanionState.RESPAWNING) {
                if (msg.action != Action.STOP && msg.action != Action.OPEN_GUI) {
                    player.sendSystemMessage(Component.literal("[" + companion.getCustomCompanionName() + "] ")
                            .withStyle(net.minecraft.ChatFormatting.GOLD)
                            .append(Component.literal("Está incapacitado o esperando reaparición. No puede recibir órdenes ahora.").withStyle(net.minecraft.ChatFormatting.RED)));
                    return;
                }
            }

            CompanionSettings settings = companion.getSettings();

            // 3. Process action safely
            switch (msg.action) {
                case PAUSE -> companion.getCompanionBrain().getTaskManager().pause();
                case RESUME -> {
                    if (companion.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                        com.aicompanion.ai.chunk.CompanionChunkManager.updateChunkTickets(companion);
                    }
                    companion.getCompanionBrain().getTaskManager().resume();
                }
                case STOP -> {
                    // STOP cancels active mission and returns to current preferred idle behavior (STAY or FOLLOW)
                    companion.getCompanionBrain().getTaskManager().stop();
                    companion.returnToDefaultBehavior();
                }
                case FOLLOW -> {
                    companion.setPreferredIdleBehavior(DefaultBehavior.FOLLOW);
                    companion.getCompanionBrain().getTaskManager().setTask(new FollowTask(companion));
                    player.sendSystemMessage(Component.translatable("message.aicompanion.following"));
                }
                case STAY -> {
                    companion.setPreferredIdleBehavior(DefaultBehavior.STAY);
                    companion.getCompanionBrain().getTaskManager().setTask(new StayTask(companion));
                    player.sendSystemMessage(Component.translatable("message.aicompanion.staying"));
                }
                case RECALL -> {
                    CompanionManager.recallCompanion(player);
                }
                case SET_HOME -> {
                    companion.setHomePos(player.blockPosition(), player.serverLevel().dimension());
                    player.sendSystemMessage(Component.translatable("message.aicompanion.home_set"));
                }
                case HOME -> {
                    if (companion.getHomePos() != null) {
                        companion.getCompanionBrain().getTaskManager().setTask(new ReturnTask(companion, true));
                    } else {
                        player.sendSystemMessage(Component.translatable("message.aicompanion.no_home"));
                    }
                }
                case MINE -> {
                    String[] parts = msg.payload.split(":");
                    String blockStr = parts.length > 0 ? parts[0] : "iron_ore";
                    int amount = parts.length > 1 ? parseInt(parts[1], 10) : 10;
                    amount = Math.max(1, Math.min(amount, 6400));
                    ResourceLocation ore = SpanishAliases.resolveOre(blockStr);
                    if (ore == null) {
                        String cleanBlock = blockStr.trim().toLowerCase().replace(' ', '_').replace('-', '_');
                        ore = ResourceLocation.tryParse(cleanBlock.contains(":") ? cleanBlock : "minecraft:" + cleanBlock);
                    }
                    if (ore == null) ore = new ResourceLocation("minecraft", "iron_ore");
                    Goal goal = Goal.obtain(ore, amount);
                    companion.getCompanionBrain().recordUserMission(List.of(goal), "Minar " + amount + " de " + blockStr);
                    List<CompanionTask> tasks = PrerequisitePlanner.resolvePlanWithPrerequisites(companion, goal);
                    companion.getCompanionBrain().applyTasks(tasks);
                }
                case CHOP -> {
                    String[] parts = msg.payload.split(":");
                    String woodStr = parts.length > 0 ? parts[0] : "oak";
                    int amount = parts.length > 1 ? parseInt(parts[1], 20) : 20;
                    amount = Math.max(1, Math.min(amount, 6400));
                    String wood = SpanishAliases.resolveWood(woodStr);
                    Goal goal = Goal.obtain(new ResourceLocation("minecraft", wood + "_log"), amount);
                    companion.getCompanionBrain().recordUserMission(List.of(goal), "Talar " + amount + " de " + woodStr);
                    List<CompanionTask> tasks = PrerequisitePlanner.resolvePlanWithPrerequisites(companion, goal);
                    companion.getCompanionBrain().applyTasks(tasks);
                }
                case BUILD -> {
                    String payload = msg.payload.isEmpty() ? "small_house" : msg.payload;
                    com.aicompanion.ai.building.BuildingConfig bConfig = com.aicompanion.ai.building.BuildingConfig.deserialize(payload);
                    String bpName = bConfig.getBlueprintId();

                    com.aicompanion.ai.building.Blueprint sourceBp = com.aicompanion.ai.building.BlueprintRegistry.get(bpName);
                    if (sourceBp == null || com.aicompanion.ai.building.BlueprintRegistry.isProcedural(bpName)) {
                        String woodMat = "oak";
                        String stoneMat = "cobblestone";
                        if (!payload.startsWith("cfg:")) {
                            String[] parts = payload.replace(":auto_gather", "").replace(":gather", "").replaceAll(":rot=\\d+", "").split(":");
                            if (parts.length > 1) woodMat = parts[1];
                            if (parts.length > 2) stoneMat = parts[2];
                        }
                        sourceBp = com.aicompanion.ai.building.BlueprintRegistry.getCustom(bpName, woodMat, stoneMat);
                    }

                    com.aicompanion.ai.building.Blueprint bp = com.aicompanion.ai.building.BlueprintTransformer.transform(sourceBp, bConfig);

                    com.aicompanion.ai.building.BuildProject existingProj = companion.getCompanionBrain().getActiveBuildProject();
                    BlockPos targetPos;
                    if (!bConfig.isForceNew() && existingProj != null && existingProj.isActive()
                            && (existingProj.getBlueprintId().equalsIgnoreCase(bpName) || existingProj.getBlueprintName().equalsIgnoreCase(bpName))
                            && bConfig.getTargetPos() == null) {
                        targetPos = existingProj.getOriginPos();
                        bConfig = existingProj.getBuildingConfig();
                    } else {
                        if (bConfig.isForceNew() && existingProj != null) {
                            existingProj.setStatus(com.aicompanion.ai.building.BuildProject.Status.CANCELLED);
                            companion.getCompanionBrain().clearActiveBuildProject();
                        }
                        if (bConfig.getTargetPos() != null) {
                            targetPos = bConfig.getTargetPos();
                        } else if (bConfig.getPlacementMode() == com.aicompanion.ai.building.BuildingConfig.PlacementMode.NEAR_COMPANION) {
                            targetPos = companion.blockPosition();
                        } else {
                            targetPos = player.blockPosition();
                        }
                    }

                    Goal goal = Goal.build(payload);
                    companion.getCompanionBrain().recordUserMission(List.of(goal), "Construir " + bpName + " (" + bConfig.getBuildMode().getDisplayName() + ")");

                    if (bConfig.isAutoGather() && bp != null) {
                        com.aicompanion.ai.building.MaterialAcquisitionPlanner.planAndEnqueueFullConstruction(companion, bp, targetPos, bConfig);
                    } else if (bp != null) {
                        companion.getCompanionBrain().getTaskManager().setTask(new BuildTask(companion, bp, targetPos, bConfig));
                    }
                }
                case CLEAR_QUEUE -> {
                    companion.getCompanionBrain().getTaskManager().getTaskQueue().clear();
                    player.sendSystemMessage(Component.translatable("message.aicompanion.queue_cleared"));
                }
                case REMOVE_QUEUE_INDEX -> {
                    int idx = parseInt(msg.payload, -1);
                    if (idx >= 0 && idx < companion.getCompanionBrain().getTaskManager().getTaskQueue().size()) {
                        companion.getCompanionBrain().getTaskManager().getTaskQueue().remove(idx);
                    }
                }
                case GOTO -> {
                    String[] parts = msg.payload.split(":");
                    if (parts.length >= 3) {
                        int x = parseInt(parts[0], companion.getBlockX());
                        int y = parseInt(parts[1], companion.getBlockY());
                        int z = parseInt(parts[2], companion.getBlockZ());
                        BlockPos targetPos = new BlockPos(x, y, z);
                        companion.getCompanionBrain().getTaskManager().setTask(new GotoTask(companion, targetPos));
                        player.sendSystemMessage(Component.literal("[" + companion.getCustomCompanionName() + "] Viajando a " + x + ", " + y + ", " + z + "."));
                    }
                }
                case DEPOSIT -> {
                    companion.getCompanionBrain().getTaskManager().setTask(new DepositTask(companion));
                    player.sendSystemMessage(Component.literal("[" + companion.getCustomCompanionName() + "] Depositando en cofre."));
                }
                case WITHDRAW -> {
                    String[] parts = msg.payload.split(":");
                    String itemStr = parts.length > 0 ? parts[0] : "iron_ingot";
                    int amount = parts.length > 1 ? parseInt(parts[1], 1) : 1;
                    amount = Math.max(1, Math.min(amount, 6400));
                    String cleanItem = itemStr.trim().toLowerCase().replace(' ', '_').replace('-', '_');
                    ResourceLocation itemId = ResourceLocation.tryParse(cleanItem.contains(":") ? cleanItem : "minecraft:" + cleanItem);
                    if (itemId == null) itemId = new ResourceLocation("minecraft", "iron_ingot");
                    companion.getCompanionBrain().getTaskManager().setTask(new WithdrawTask(companion, itemId, amount));
                }
                case REPLAN -> {
                    companion.getCompanionBrain().replanCurrentTask();
                    player.sendSystemMessage(Component.literal("[" + companion.getCustomCompanionName() + "] Replanificando tarea..."));
                }
                case TOGGLE_AUTO_EQUIP -> {
                    settings.setAutoEquip(!settings.isAutoEquip());
                    syncMenu(player, companion);
                }
                case TOGGLE_AUTO_EAT -> {
                    settings.setAutoEat(!settings.isAutoEat());
                    syncMenu(player, companion);
                }
                case EQUIP_BEST -> {
                    EquipmentManager.autoEquipBestArmorAndWeapons(companion, true);
                    syncMenu(player, companion);
                }
                case ORGANIZE_INVENTORY -> {
                    if (companion.getInventoryHandler() != null) {
                        boolean playerCarrying = (player.containerMenu != null && !player.containerMenu.getCarried().isEmpty());
                        if (playerCarrying) {
                            player.sendSystemMessage(
                                    Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                            .withStyle(net.minecraft.ChatFormatting.GOLD)
                                            .append(Component.literal("No se puede organizar mientras sostienes un objeto con el cursor.")
                                                    .withStyle(net.minecraft.ChatFormatting.YELLOW))
                            );
                        } else {
                            com.aicompanion.inventory.InventoryOrganizer.organize(companion.getInventoryHandler());
                            syncMenu(player, companion);
                            player.sendSystemMessage(
                                    Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                            .withStyle(net.minecraft.ChatFormatting.GOLD)
                                            .append(Component.literal("Mochila organizada por categorías.").withStyle(net.minecraft.ChatFormatting.GREEN))
                            );
                        }
                    }
                }
                case SUMMON -> {
                    CompanionManager.recallCompanion(player);
                    companion.openCompanionMenu(player);
                }
                case DISMISS -> {
                    CompanionManager.dismissCompanion(player);
                    player.closeContainer();
                    com.aicompanion.network.packets.S2C_SyncCompanionDataPacket dismissPacket = new com.aicompanion.network.packets.S2C_SyncCompanionDataPacket();
                    dismissPacket.health = 0.0F;
                    dismissPacket.currentState = "DESINVOCADO";
                    dismissPacket.currentTaskDesc = "Desinvocado";
                    com.aicompanion.network.CompanionNetworking.CHANNEL.send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                            dismissPacket
                    );
                    player.sendSystemMessage(
                            Component.literal("[Esclavo] ")
                                    .withStyle(net.minecraft.ChatFormatting.GOLD)
                                    .append(Component.literal("Guardado y desinvocado correctamente.").withStyle(net.minecraft.ChatFormatting.WHITE))
                    );
                    return;
                }
                case OPEN_GUI -> {
                    companion.syncToOwner();
                    companion.openCompanionMenu(player);
                }
                case SET_NAME -> {
                    String newName = msg.payload != null ? msg.payload.trim() : "";
                    if (!newName.isEmpty()) {
                        if (newName.length() > 32) newName = newName.substring(0, 32);
                        companion.setCustomCompanionName(newName);
                        CompanionSavedData.get(player.serverLevel()).saveCompanion(companion);
                        companion.syncToOwner();
                        player.sendSystemMessage(
                                Component.literal("[" + newName + "] ")
                                        .withStyle(net.minecraft.ChatFormatting.GOLD)
                                        .append(Component.literal("Nombre actualizado con éxito a: " + newName)
                                                .withStyle(net.minecraft.ChatFormatting.GREEN))
                        );
                    }
                }
                case TOGGLE_CONFIG -> {
                    String key = msg.payload.toLowerCase();
                    switch (key) {
                        case "auto_follow" -> {
                            if (companion.getPreferredIdleBehavior() == DefaultBehavior.FOLLOW) {
                                companion.setPreferredIdleBehavior(DefaultBehavior.STAY);
                                companion.getCompanionBrain().getTaskManager().setTask(new StayTask(companion));
                                settings.setAutoFollow(false);
                            } else {
                                companion.setPreferredIdleBehavior(DefaultBehavior.FOLLOW);
                                companion.getCompanionBrain().getTaskManager().setTask(new FollowTask(companion));
                                settings.setAutoFollow(true);
                            }
                        }
                        case "auto_equip" -> settings.setAutoEquip(!settings.isAutoEquip());
                        case "auto_eat" -> settings.setAutoEat(!settings.isAutoEat());
                        case "auto_deliver" -> settings.setAutoDeliver(!settings.isAutoDeliver());
                        case "auto_return" -> settings.setAutoReturn(!settings.isAutoReturn());
                        case "allow_block_breaking" -> settings.setAllowBlockBreaking(!settings.isAllowBlockBreaking());
                        case "allow_block_placement" -> settings.setAllowBlockPlacement(!settings.isAllowBlockPlacement());
                        case "allow_combat" -> settings.setAllowCombat(!settings.isAllowCombat());
                        case "chunk_loading" -> settings.setEnableChunkLoading(!settings.isEnableChunkLoading());
                    }
                    syncMenu(player, companion);
                }
                case TEST_LLM_CONNECTION -> {
                    String mode = com.aicompanion.AICompanionConfig.COMMON.aiMode.get();
                    final String compName = companion.getCustomCompanionName();

                    if ("LOCAL_ONLY".equalsIgnoreCase(mode)) {
                        String navStats = companion.getNavManager().getDebugStats();
                        player.sendSystemMessage(
                                Component.literal("[" + compName + "] ")
                                        .withStyle(net.minecraft.ChatFormatting.GOLD)
                                        .append(Component.literal("Motor de Inteligencia Local y Baritone 3D: 100% OPERATIVO (Sin dependencias externas ni servidores LLM).")
                                                .withStyle(net.minecraft.ChatFormatting.GREEN))
                        );
                        player.sendSystemMessage(
                                Component.literal("[" + compName + "] ")
                                        .withStyle(net.minecraft.ChatFormatting.GOLD)
                                        .append(Component.literal("Diagnóstico: " + navStats)
                                                .withStyle(net.minecraft.ChatFormatting.AQUA))
                        );
                        return;
                    }

                    String endpoint = com.aicompanion.AICompanionConfig.COMMON.aiEndpoint.get();
                    String model = com.aicompanion.AICompanionConfig.COMMON.aiModel.get();
                    player.sendSystemMessage(
                            Component.literal("[" + compName + "] ")
                                    .withStyle(net.minecraft.ChatFormatting.GOLD)
                                    .append(Component.literal("Probando conexión con endpoint: " + endpoint + " (Modelo: " + model + ")...")
                                            .withStyle(net.minecraft.ChatFormatting.YELLOW))
                    );

                    java.util.concurrent.CompletableFuture.runAsync(() -> {
                        long start = System.currentTimeMillis();
                        boolean reachable = false;
                        int code = -1;
                        String detail = "";
                        try {
                            String urlStr = endpoint;
                            if (urlStr.endsWith("/")) urlStr = urlStr.substring(0, urlStr.length() - 1);
                            if (urlStr.endsWith("/v1")) urlStr = urlStr + "/models";
                            else if (!urlStr.contains("/models") && !urlStr.contains("/api")) urlStr = urlStr + "/v1/models";

                            java.net.URI uri = java.net.URI.create(urlStr);
                            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) uri.toURL().openConnection();
                            conn.setRequestMethod("GET");
                            conn.setConnectTimeout(2500);
                            conn.setReadTimeout(2500);
                            code = conn.getResponseCode();
                            reachable = (code >= 200 && code < 400) || code == 401 || code == 403;
                            conn.disconnect();
                        } catch (Exception e) {
                            detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                        }
                        long latency = System.currentTimeMillis() - start;

                        final boolean ok = reachable;
                        final long lat = latency;
                        final int httpCode = code;
                        final String errorMsg = detail;

                        player.getServer().execute(() -> {
                            if (ok) {
                                player.sendSystemMessage(
                                        Component.literal("[" + compName + "] ")
                                                .withStyle(net.minecraft.ChatFormatting.GOLD)
                                                .append(Component.literal("¡Conexión con LLM exitosa! (" + lat + "ms, HTTP " + httpCode + ")")
                                                        .withStyle(net.minecraft.ChatFormatting.GREEN))
                                );
                            } else {
                                player.sendSystemMessage(
                                        Component.literal("[" + compName + "] ")
                                                .withStyle(net.minecraft.ChatFormatting.GOLD)
                                                .append(Component.literal("Endpoint inaccesible (" + (errorMsg.isEmpty() ? "HTTP " + httpCode : errorMsg) + "). Operando con Reglas Locales Deterministas.")
                                                        .withStyle(net.minecraft.ChatFormatting.RED))
                                );
                            }
                        });
                    });
                }
                case CHEST_AUTHORIZE_NEARBY -> {
                    var created = companion.authorizeNearestContainerAround(player.blockPosition(), player.level(), player.getUUID(), 8.0);
                    if (created == null && companion.level() == player.level()) {
                        created = companion.authorizeNearestContainerAround(companion.blockPosition(), companion.level(), player.getUUID(), 8.0);
                    }
                    if (created != null) {
                        player.sendSystemMessage(
                                Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                        .withStyle(net.minecraft.ChatFormatting.GOLD)
                                        .append(Component.literal("¡Autorizado " + created.getFullLabel() + " en " + created.getPos().toShortString() + "!").withStyle(net.minecraft.ChatFormatting.GREEN))
                        );
                    } else {
                        player.sendSystemMessage(
                                Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                        .withStyle(net.minecraft.ChatFormatting.GOLD)
                                        .append(Component.literal("No encontré ningún cofre o barril nuevo sin autorizar a menos de 8 bloques.").withStyle(net.minecraft.ChatFormatting.YELLOW))
                        );
                    }
                    syncMenu(player, companion);
                }
                case CHEST_UNAUTHORIZE -> {
                    int chestId = parseInt(msg.payload, -1);
                    if (chestId > 0 && companion.unauthorizeChestById(chestId)) {
                        player.sendSystemMessage(
                                Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                        .withStyle(net.minecraft.ChatFormatting.GOLD)
                                        .append(Component.literal("Baúl [#" + chestId + "] desautorizado.").withStyle(net.minecraft.ChatFormatting.YELLOW))
                        );
                    }
                    syncMenu(player, companion);
                }
                case CHEST_CYCLE_CATEGORY -> {
                    int chestId = parseInt(msg.payload, -1);
                    var newCat = companion.cycleAuthorizedChestCategory(chestId);
                    if (newCat != null) {
                        var entry = companion.getAuthorizedChestById(chestId);
                        player.sendSystemMessage(
                                Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                        .withStyle(net.minecraft.ChatFormatting.GOLD)
                                        .append(Component.literal("Categoría actualizada: " + (entry != null ? entry.getFullLabel() : newCat.getDisplayName())).withStyle(net.minecraft.ChatFormatting.AQUA))
                        );
                    }
                    syncMenu(player, companion);
                }
                case CHEST_RENAME -> {
                    int sep = msg.payload.indexOf(':');
                    if (sep > 0) {
                        int chestId = parseInt(msg.payload.substring(0, sep), -1);
                        String newName = msg.payload.substring(sep + 1).trim();
                        if (companion.renameAuthorizedChest(chestId, newName)) {
                            var entry = companion.getAuthorizedChestById(chestId);
                            player.sendSystemMessage(
                                    Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                            .withStyle(net.minecraft.ChatFormatting.GOLD)
                                            .append(Component.literal("Baúl renombrado a: " + (entry != null ? entry.getFullLabel() : newName)).withStyle(net.minecraft.ChatFormatting.GREEN))
                            );
                        }
                    }
                    syncMenu(player, companion);
                }
                case CHEST_SET_PRIMARY -> {
                    int chestId = parseInt(msg.payload, -1);
                    if (companion.setPrimaryChestById(chestId)) {
                        var entry = companion.getAuthorizedChestById(chestId);
                        player.sendSystemMessage(
                                Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                        .withStyle(net.minecraft.ChatFormatting.GOLD)
                                        .append(Component.literal("Almacén principal (★) fijado en " + (entry != null ? entry.getFullLabel() : ("#" + chestId)) + ".").withStyle(net.minecraft.ChatFormatting.GREEN))
                        );
                    }
                    syncMenu(player, companion);
                }
                case CHEST_DEPOSIT_TO -> {
                    int chestId = parseInt(msg.payload, -1);
                    var entry = companion.getAuthorizedChestById(chestId);
                    if (entry != null) {
                        companion.getCompanionBrain().getTaskManager().setTask(new DepositTask(companion, chestId));
                        player.sendSystemMessage(
                                Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                        .withStyle(net.minecraft.ChatFormatting.GOLD)
                                        .append(Component.literal("Dirigiéndome a guardar objetos en " + entry.getFullLabel() + ".").withStyle(net.minecraft.ChatFormatting.GREEN))
                        );
                    }
                    syncMenu(player, companion);
                }
                case CYCLE_FOOD_POLICY -> {
                    settings.setFoodStoragePolicy(settings.getFoodStoragePolicy().next());
                    syncMenu(player, companion);
                }
                case CYCLE_AUTODEPOSIT_POLICY -> {
                    settings.setAutoDepositPolicy(settings.getAutoDepositPolicy().next());
                    syncMenu(player, companion);
                }
                case CYCLE_AI_AUTONOMY -> {
                    try {
                        String cur = com.aicompanion.AICompanionConfig.COMMON.aiAutonomyLevel.get();
                        String next = switch (cur != null ? cur.toUpperCase() : "PROACTIVE") {
                            case "OBEDIENT" -> "SEMI_AUTONOMOUS";
                            case "SEMI_AUTONOMOUS" -> "PROACTIVE";
                            default -> "OBEDIENT";
                        };
                        com.aicompanion.AICompanionConfig.COMMON.aiAutonomyLevel.set(next);
                    } catch (Exception ignored) {}
                    syncMenu(player, companion);
                }
                case CYCLE_AI_PERSONALITY -> {
                    try {
                        String cur = com.aicompanion.AICompanionConfig.COMMON.aiPersonality.get();
                        String next = switch (cur != null ? cur.toUpperCase() : "SERIOUS") {
                            case "SERIOUS" -> "FRIENDLY";
                            case "FRIENDLY" -> "ROBOTIC";
                            default -> "SERIOUS";
                        };
                        com.aicompanion.AICompanionConfig.COMMON.aiPersonality.set(next);
                    } catch (Exception ignored) {}
                    syncMenu(player, companion);
                }
                case CLEAR_DANGER_ZONES -> {
                    if (companion.getCompanionBrain() != null) {
                        companion.getCompanionBrain().clearDangerZones();
                        if (companion.getCompanionBrain().getMemory() != null && companion.getCompanionBrain().getMemory().getWorldModel() != null) {
                            companion.getCompanionBrain().getMemory().getWorldModel().clearResourceDeposits();
                        }
                    }
                    player.sendSystemMessage(
                            Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                    .withStyle(net.minecraft.ChatFormatting.GOLD)
                                    .append(Component.literal("Zonas de peligro y caché temporal de vetas reiniciadas.").withStyle(net.minecraft.ChatFormatting.GREEN))
                    );
                    syncMenu(player, companion);
                }
            }

            // Save persistent data
            CompanionSavedData.get(player.serverLevel()).saveCompanion(companion);
        });
        ctx.get().setPacketHandled(true);
    }

    private static void syncMenu(ServerPlayer player, CompanionEntity companion) {
        if (player.containerMenu instanceof CompanionMenu menu) {
            menu.syncDataToClient(player);
        } else if (companion != null) {
            companion.syncToOwner();
        }
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
