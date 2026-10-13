package com.aicompanion.data;

import com.aicompanion.AICompanionConfig;
import com.aicompanion.AICompanionMod;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import com.aicompanion.entity.DefaultBehavior;
import com.aicompanion.init.ModEntities;
import com.aicompanion.util.SafeSpawnFinder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = AICompanionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CompanionManager {
    private static final Map<UUID, UUID> ownerToCompanion = new ConcurrentHashMap<>();
    private static final Map<UUID, CompanionEntity> activeCompanions = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof CompanionEntity companion) {
            UUID ownerUUID = companion.getOwnerUUID();
            if (ownerUUID == null) return;

            UUID existingCompUUID = ownerToCompanion.get(ownerUUID);
            if (existingCompUUID != null && existingCompUUID.equals(companion.getUUID())) {
                CompanionEntity existingComp = activeCompanions.get(existingCompUUID);
                if (existingComp != null && existingComp != companion) {
                    if (existingComp.level() != companion.level() || existingComp.isRemoved()) {
                        // Dimension transition or old instance removal: replace active instance with the new one
                        activeCompanions.put(existingCompUUID, companion);
                        registerCompanion(companion);
                        AICompanionMod.LOGGER.info("CompanionEntity {} transitioned dimensions or renewed. Updated active tracking.",
                                companion.getCustomCompanionName());
                        return;
                    } else if (existingComp.isAlive()) {
                        // Truly duplicate entity in the same level
                        AICompanionMod.LOGGER.warn("Reconciling duplicate CompanionEntity detected for owner {}. Discarding duplicate entity {}.",
                                ownerUUID, companion.getId());
                        event.setCanceled(true);
                        companion.discard();
                        return;
                    }
                }
            }

            // Register newly loaded chunk entity into active tracking
            registerCompanion(companion);
            com.aicompanion.ai.chunk.CompanionChunkManager.updateChunkTickets(companion);
            AICompanionMod.LOGGER.info("Registered active CompanionEntity {} for owner {} on chunk load/join.",
                    companion.getCustomCompanionName(), ownerUUID);
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof CompanionEntity companion) {
            if (companion.isRemoved()) {
                net.minecraft.world.entity.Entity.RemovalReason reason = companion.getRemovalReason();
                if (reason == net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION) {
                    // Do NOT unregister: entity simply unloaded with chunk or changed dimension!
                    return;
                }
                unregisterCompanion(companion);
            }
        }
    }

    private static final Map<UUID, Integer> lastSeenTickCounts = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> lastAutonomousTicks = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            MinecraftServer server = event.getServer();
            if (server != null) {
                // Autonomous tick driver for active companions (guarantees 20 TPS across all dimensions)
                driveAutonomousCompanionTicks(server);

                if (server.getTickCount() % 5 == 0) {
                    ServerLevel overworld = server.overworld();
                    if (overworld != null) {
                        com.aicompanion.ai.coordination.CompanionCoordinator.tickCooperativeMissions(overworld);
                    }
                }
                if (server.getTickCount() % 10 == 0) {
                    tickPendingRespawns(server);
                }
                if (server.getTickCount() % 20 == 0) {
                    monitorCompanionTicking(server);
                }
            }
        }
    }

    private static void driveAutonomousCompanionTicks(MinecraftServer server) {
        for (CompanionEntity companion : activeCompanions.values()) {
            if (companion == null || !companion.isAlive() || companion.isRemoved()) continue;
            if (!(companion.level() instanceof ServerLevel sl)) continue;

            UUID compUUID = companion.getUUID();
            int currentTick = companion.tickCount;
            int lastTick = lastAutonomousTicks.getOrDefault(compUUID, -1);

            // If vanilla ServerLevel.tick() skipped this companion (e.g. no players in dimension)
            if (currentTick == lastTick) {
                net.minecraft.world.level.ChunkPos cPos = new net.minecraft.world.level.ChunkPos(companion.blockPosition());
                if (sl.hasChunk(cPos.x, cPos.z)) {
                    try {
                        companion.tickCount++;
                        companion.tick();
                    } catch (Throwable t) {
                        AICompanionMod.LOGGER.error("[CompanionManager] Error in autonomous forced tick for {}: {}", compUUID, t.getMessage());
                    }
                } else {
                    com.aicompanion.ai.chunk.CompanionChunkManager.updateChunkTickets(companion);
                    sl.getChunkSource().getChunk(cPos.x, cPos.z, net.minecraft.world.level.chunk.ChunkStatus.FULL, true);
                }
            }
            lastAutonomousTicks.put(compUUID, companion.tickCount);
        }
    }

    private static void monitorCompanionTicking(MinecraftServer server) {
        boolean runDiag = (server.getTickCount() % 100 == 0); // Every 5 seconds

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID ownerUUID = player.getUUID();
            CompanionEntity companion = getCompanionForOwner(server, ownerUUID);
            if (companion != null && companion.isAlive() && !companion.isRemoved()) {
                if (companion.level() instanceof ServerLevel sl) {
                    net.minecraft.world.level.ChunkPos cPos = new net.minecraft.world.level.ChunkPos(companion.blockPosition());
                    boolean chunkLoaded = sl.hasChunk(cPos.x, cPos.z);
                    boolean chunkTicking = com.aicompanion.ai.chunk.CompanionChunkManager.isChunkEntityTicking(sl, cPos);

                    int prevTick = lastSeenTickCounts.getOrDefault(companion.getUUID(), -1);
                    boolean isTicking = (prevTick != companion.tickCount);
                    lastSeenTickCounts.put(companion.getUUID(), companion.tickCount);

                    boolean diffDim = !companion.level().dimension().equals(player.level().dimension());
                    boolean hasActiveTask = (companion.getCompanionBrain() != null && companion.getCompanionBrain().getTaskManager().isBusy());

                    // Force chunk tickets if companion is in another dimension or busy and not receiving ticks
                    if ((diffDim || hasActiveTask) && (!chunkTicking || !isTicking)) {
                        com.aicompanion.ai.chunk.CompanionChunkManager.updateChunkTickets(companion);
                    }

                    if (runDiag) {
                        String taskDesc = "IDLE";
                        String substate = "Ninguna";
                        String progress = "0/0";
                        if (companion.getCompanionBrain() != null && companion.getCompanionBrain().getTaskManager() != null) {
                            var t = companion.getCompanionBrain().getTaskManager().getCurrentTask();
                            if (t != null) {
                                taskDesc = t.getDescription();
                                substate = t.getSubstate();
                                progress = t.getProgress();
                            }
                        }
                        String navStats = companion.getNavManager() != null ? companion.getNavManager().getDebugStats() : "NONE";
                        AICompanionMod.LOGGER.info("[ESCLAVO-DIAG] OwnerDim={} CompDim={} Pos={} ChunkLoaded={} ChunkTicking={} CompTicking={} Task='{}' Substate='{}' Progress='{}' Nav='{}'",
                                player.level().dimension().location(),
                                sl.dimension().location(),
                                companion.blockPosition().toShortString(),
                                chunkLoaded,
                                chunkTicking,
                                isTicking,
                                taskDesc,
                                substate,
                                progress,
                                navStats);
                    }
                }
            }
        }
    }

    public static void registerCompanion(CompanionEntity companion) {
        if (companion == null || companion.getOwnerUUID() == null) return;
        ownerToCompanion.put(companion.getOwnerUUID(), companion.getUUID());
        activeCompanions.put(companion.getUUID(), companion);
        com.aicompanion.ai.coordination.CompanionCoordinator.registerCompanion(companion.getOwnerUUID(), companion.getUUID());
    }

    public static void unregisterCompanion(CompanionEntity companion) {
        if (companion == null) return;
        if (companion.getOwnerUUID() != null) {
            ownerToCompanion.remove(companion.getOwnerUUID(), companion.getUUID());
            com.aicompanion.ai.coordination.CompanionCoordinator.unregisterCompanion(companion.getOwnerUUID(), companion.getUUID());
        }
        activeCompanions.remove(companion.getUUID());
    }

    public static CompanionEntity getCompanionForOwner(MinecraftServer server, UUID ownerUUID) {
        if (ownerUUID == null) return null;
        UUID compUUID = ownerToCompanion.get(ownerUUID);
        if (compUUID != null) {
            CompanionEntity active = activeCompanions.get(compUUID);
            if (active != null && active.isAlive() && !active.isRemoved()) {
                return active;
            }
        }

        // 1. Cross-dimension fallback search across all loaded entities
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity e : level.getAllEntities()) {
                    if (e instanceof CompanionEntity companion && ownerUUID.equals(companion.getOwnerUUID()) && companion.isAlive() && !companion.isRemoved()) {
                        registerCompanion(companion);
                        return companion;
                    }
                }
            }

            // 2. Unloaded chunk recovery from CompanionSavedData
            ServerLevel overworld = server.overworld();
            if (overworld != null) {
                CompanionSavedData savedData = CompanionSavedData.get(overworld);
                CompoundTag tag = savedData.getSavedCompanion(ownerUUID);
                if (tag != null && !tag.getBoolean("IsDismissed")) {
                    float savedHealth = tag.contains("Health") ? tag.getFloat("Health") : 20.0f;
                    boolean isDead = tag.contains("IsAlive") && !tag.getBoolean("IsAlive");
                    if (savedHealth <= 0.0f || isDead) {
                        return null; // Truly dead; opens summon/revive screen
                    }

                    String dimStr = tag.getString("Dimension");
                    ResourceLocation dimLoc = dimStr.isEmpty() ? Level.OVERWORLD.location() : new ResourceLocation(dimStr);
                    ServerLevel targetLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimLoc));
                    if (targetLevel == null) targetLevel = overworld;

                    double x = tag.contains("SavedPosX") ? tag.getDouble("SavedPosX") : 0;
                    double y = tag.contains("SavedPosY") ? tag.getDouble("SavedPosY") : 0;
                    double z = tag.contains("SavedPosZ") ? tag.getDouble("SavedPosZ") : 0;
                    if (x == 0 && y == 0 && z == 0 && tag.contains("Pos", Tag.TAG_LIST)) {
                        ListTag posList = tag.getList("Pos", Tag.TAG_DOUBLE);
                        if (posList.size() >= 3) {
                            x = posList.getDouble(0);
                            y = posList.getDouble(1);
                            z = posList.getDouble(2);
                        }
                    }

                    int chunkX = ((int) Math.floor(x)) >> 4;
                    int chunkZ = ((int) Math.floor(z)) >> 4;

                    AICompanionMod.LOGGER.info("[CompanionManager] Loading chunks around ({}, {}) in {} to wake companion for owner {}",
                            chunkX, chunkZ, targetLevel.dimension().location(), ownerUUID);

                    // Force and load a 3x3 of chunks around companion with entity ticking
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            try {
                                net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(chunkX + dx, chunkZ + dz);
                                targetLevel.setChunkForced(cp.x, cp.z, true);
                                targetLevel.getChunkSource().addRegionTicket(com.aicompanion.ai.chunk.CompanionChunkManager.COMPANION_TICKET, cp, 2, ownerUUID, true);
                                targetLevel.getChunkSource().getChunk(cp.x, cp.z, ChunkStatus.FULL, true);
                            } catch (Exception ignored) {}
                        }
                    }

                    UUID companionUUID = tag.hasUUID("CompanionUUID") ? tag.getUUID("CompanionUUID") : (tag.hasUUID("UUID") ? tag.getUUID("UUID") : compUUID);
                    if (companionUUID != null) {
                        Entity ent = targetLevel.getEntity(companionUUID);
                        if (ent instanceof CompanionEntity companion && companion.isAlive() && !companion.isRemoved()) {
                            registerCompanion(companion);
                            com.aicompanion.ai.chunk.CompanionChunkManager.updateChunkTickets(companion);
                            return companion;
                        }
                    }

                    for (Entity ent : targetLevel.getAllEntities()) {
                        if (ent instanceof CompanionEntity companion && ownerUUID.equals(companion.getOwnerUUID()) && companion.isAlive() && !companion.isRemoved()) {
                            registerCompanion(companion);
                            com.aicompanion.ai.chunk.CompanionChunkManager.updateChunkTickets(companion);
                            return companion;
                        }
                    }

                    // Fail-safe restore: if entity was somehow missing from loaded level, restore it at saved coordinates
                    CompanionEntity restored = new CompanionEntity(ModEntities.COMPANION.get(), targetLevel);
                    restored.load(tag);
                    restored.setPos(x, y, z);
                    restored.setOwnerUUID(ownerUUID);
                    if (restored.getHealth() <= 0.0f) {
                        restored.setHealth(restored.getMaxHealth());
                    }
                    if (targetLevel.addFreshEntity(restored)) {
                        AICompanionMod.LOGGER.info("[CompanionManager] Restored companion at position ({}, {}, {}) from saved data", x, y, z);
                        registerCompanion(restored);
                        com.aicompanion.ai.chunk.CompanionChunkManager.updateChunkTickets(restored);
                        return restored;
                    }
                }
            }
        }
        return null;
    }

    public static CompanionEntity getCompanionByUUID(MinecraftServer server, UUID companionUUID) {
        if (companionUUID == null) return null;
        CompanionEntity active = activeCompanions.get(companionUUID);
        if (active != null && active.isAlive() && !active.isRemoved()) {
            return active;
        }

        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity e = level.getEntity(companionUUID);
                if (e instanceof CompanionEntity companion && companion.isAlive() && !companion.isRemoved()) {
                    registerCompanion(companion);
                    return companion;
                }
            }

            // Unloaded chunk recovery by companion UUID across owners
            ServerLevel overworld = server.overworld();
            if (overworld != null) {
                CompanionSavedData savedData = CompanionSavedData.get(overworld);
                for (UUID ownerUUID : ownerToCompanion.keySet()) {
                    CompoundTag tag = savedData.getSavedCompanion(ownerUUID);
                    if (tag != null) {
                        UUID savedCUUID = tag.hasUUID("CompanionUUID") ? tag.getUUID("CompanionUUID") : (tag.hasUUID("UUID") ? tag.getUUID("UUID") : null);
                        if (companionUUID.equals(savedCUUID)) {
                            return getCompanionForOwner(server, ownerUUID);
                        }
                    }
                }
            }
        }
        return null;
    }

    public static CompanionEntity summonNewCompanion(ServerPlayer player, String name) {
        return summonNewCompanion(player, name, false);
    }

    public static CompanionEntity summonNewCompanion(ServerPlayer player, String name, boolean forceAdmin) {
        ServerLevel level = player.serverLevel();
        CompanionSavedData savedData = CompanionSavedData.get(level);
        CompoundTag tag = savedData.getSavedCompanion(player.getUUID());
        if (!forceAdmin && tag != null && CompanionState.DEAD_WAITING_RESPAWN.name().equals(tag.getString("State"))) {
            long now = level.getServer().overworld().getGameTime();
            long respawnGameTime = tag.getLong("RespawnGameTime");
            long remainingTicks = respawnGameTime - now;
            int remainingSeconds = (int) Math.max(0, Math.ceil(remainingTicks / 20.0));
            if (remainingSeconds > 0 && Boolean.TRUE.equals(AICompanionConfig.COMMON.autoRespawn.get())) {
                String timeStr = String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60);
                String compName = tag.contains("CustomCompanionName") ? tag.getString("CustomCompanionName") : "Esclavo";
                player.sendSystemMessage(Component.literal("[" + compName + "] ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("está en proceso de recuperación (" + timeStr + "). Reaparecerá automáticamente.")
                                .withStyle(ChatFormatting.RED)));
                return null;
            }
        }

        SafeSpawnFinder.SafeSpawnResult safeResult = SafeSpawnFinder.findSafeSpawnNearEntity(level, player, null);
        if (!safeResult.isSuccess()) {
            player.sendSystemMessage(Component.translatable("message.aicompanion.no_safe_spawn"));
            return null;
        }

        CompanionEntity companion = new CompanionEntity(ModEntities.COMPANION.get(), level);
        Vec3 safePos = safeResult.getPosition();
        companion.setPos(safePos.x, safePos.y, safePos.z);
        SafeSpawnFinder.faceEntity(companion, player);
        companion.setOwnerUUID(player.getUUID());
        companion.setCustomCompanionName((name != null && !name.isEmpty()) ? name : "Esclavo");
        companion.setPreferredIdleBehavior(DefaultBehavior.FOLLOW);
        companion.setCurrentState(CompanionState.FOLLOWING);

        boolean spawned = level.addFreshEntity(companion);
        if (!spawned) {
            AICompanionMod.LOGGER.error("addFreshEntity returned false when summoning companion for owner {}", player.getUUID());
            player.sendSystemMessage(Component.translatable("message.aicompanion.spawn_failed"));
            return null;
        }

        registerCompanion(companion);
        CompanionSavedData.get(level).saveCompanion(companion);
        return companion;
    }

    public static CompanionEntity restoreCompanion(ServerPlayer player) {
        return restoreCompanion(player, false);
    }

    public static CompanionEntity restoreCompanion(ServerPlayer player, boolean forceAdmin) {
        UUID ownerUUID = player.getUUID();
        CompanionEntity current = getCompanionForOwner(player.getServer(), ownerUUID);
        if (current != null) {
            return current;
        }

        ServerLevel level = player.serverLevel();
        CompanionSavedData savedData = CompanionSavedData.get(level);
        CompoundTag tag = savedData.getSavedCompanion(ownerUUID);

        if (!forceAdmin && tag != null && CompanionState.DEAD_WAITING_RESPAWN.name().equals(tag.getString("State"))) {
            long now = level.getServer().overworld().getGameTime();
            long respawnGameTime = tag.getLong("RespawnGameTime");
            long remainingTicks = respawnGameTime - now;
            int remainingSeconds = (int) Math.max(0, Math.ceil(remainingTicks / 20.0));
            if (remainingSeconds > 0 && Boolean.TRUE.equals(AICompanionConfig.COMMON.autoRespawn.get())) {
                String timeStr = String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60);
                String compName = tag.contains("CustomCompanionName") ? tag.getString("CustomCompanionName") : "Esclavo";
                player.sendSystemMessage(Component.literal("[" + compName + "] ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("está recuperándose tras caer en combate. Reaparecerá automáticamente en " + timeStr + ".")
                                .withStyle(ChatFormatting.YELLOW)));
                return null;
            }
        }

        SafeSpawnFinder.SafeSpawnResult safeResult = SafeSpawnFinder.findSafeSpawnNearEntity(level, player, null);
        if (!safeResult.isSuccess()) {
            player.sendSystemMessage(Component.translatable("message.aicompanion.no_safe_spawn"));
            return null;
        }

        CompanionEntity companion = new CompanionEntity(ModEntities.COMPANION.get(), level);
        if (tag != null) {
            companion.load(tag);
        } else {
            companion.setCustomCompanionName("Esclavo");
        }

        Vec3 safePos = safeResult.getPosition();
        companion.setPos(safePos.x, safePos.y, safePos.z);
        SafeSpawnFinder.faceEntity(companion, player);
        companion.setOwnerUUID(ownerUUID);
        companion.setHealth(companion.getMaxHealth());
        boolean taskResumed = false;
        if (companion.getCompanionBrain() != null) {
            var bp = companion.getCompanionBrain().getActiveBuildProject();
            var tm = companion.getCompanionBrain().getTaskManager();
            if (bp != null && bp.isActive()) {
                tm.resume();
                taskResumed = true;
            } else if (tm != null && tm.getCurrentTask() != null && !tm.isIdleOrUtilityTask(tm.getCurrentTask())) {
                tm.resume();
                taskResumed = true;
            }
        }
        if (!taskResumed) {
            companion.setPreferredIdleBehavior(DefaultBehavior.FOLLOW);
            companion.setCurrentState(CompanionState.FOLLOWING);
        }

        boolean spawned = level.addFreshEntity(companion);
        if (!spawned) {
            AICompanionMod.LOGGER.error("addFreshEntity returned false when restoring companion for owner {}", ownerUUID);
            player.sendSystemMessage(Component.translatable("message.aicompanion.spawn_failed"));
            return null;
        }

        registerCompanion(companion);
        savedData.markAlive(ownerUUID);
        savedData.saveCompanion(companion);
        companion.syncToOwner();
        return companion;
    }

    public static void tickPendingRespawns(MinecraftServer server) {
        if (server == null) return;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        CompanionSavedData savedData = CompanionSavedData.get(overworld);
        if (savedData == null) return;

        long now = overworld.getGameTime();

        for (Map.Entry<UUID, CompoundTag> entry : new java.util.ArrayList<>(savedData.getAllSavedCompanions().entrySet())) {
            UUID ownerUUID = entry.getKey();
            CompoundTag tag = entry.getValue();
            if (tag == null) continue;

            String stateStr = tag.getString("State");
            if (!CompanionState.DEAD_WAITING_RESPAWN.name().equals(stateStr)) {
                continue;
            }

            long respawnGameTime = tag.getLong("RespawnGameTime");
            long remainingTicks = respawnGameTime - now;
            int remainingSeconds = (int) Math.max(0, Math.ceil(remainingTicks / 20.0));

            ServerPlayer player = server.getPlayerList().getPlayer(ownerUUID);

            // Sync countdown to owner HUD every second (20 ticks)
            if (player != null && server.getTickCount() % 20 == 0 && Boolean.TRUE.equals(AICompanionConfig.COMMON.showRespawnCountdown.get())) {
                UUID compUUID = tag.hasUUID("CompanionUUID") ? tag.getUUID("CompanionUUID") : (tag.hasUUID("UUID") ? tag.getUUID("UUID") : UUID.randomUUID());
                String compName = tag.contains("CustomCompanionName") ? tag.getString("CustomCompanionName") : "Esclavo";
                String timeStr = String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60);

                com.aicompanion.network.packets.S2C_SyncCompanionDataPacket syncPacket = new com.aicompanion.network.packets.S2C_SyncCompanionDataPacket(
                        compUUID, -1, compName,
                        CompanionState.DEAD_WAITING_RESPAWN.name(), DefaultBehavior.FOLLOW.name(),
                        0.0F, tag.contains("MaxHealth") ? tag.getFloat("MaxHealth") : 20.0F,
                        tag.contains("Level") ? tag.getInt("Level") : 1,
                        0, 0, 0, 0, 0, 0, 0, 0,
                        "Reaparición en: " + timeStr, "", "", "", "", "", 0L,
                        java.util.Collections.emptyList(),
                        "", tag.contains("DeathDim") ? tag.getString("DeathDim") : overworld.dimension().location().toString(),
                        tag.contains("DeathPosX") ? (int) tag.getDouble("DeathPosX") : player.getBlockX(),
                        tag.contains("DeathPosY") ? (int) tag.getDouble("DeathPosY") : player.getBlockY(),
                        tag.contains("DeathPosZ") ? (int) tag.getDouble("DeathPosZ") : player.getBlockZ(),
                        true, true, true, true, true, true, true, true, true
                ).withRespawnCountdown(remainingSeconds);

                com.aicompanion.network.CompanionNetworking.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                        syncPacket
                );
            }

            // Check if countdown expired and auto-respawn is enabled
            if (remainingTicks <= 0 && Boolean.TRUE.equals(AICompanionConfig.COMMON.autoRespawn.get())) {
                // If player is offline, pause until player reconnects so companion spawns safely at player location
                if (player == null) {
                    continue;
                }

                executeAutomaticRespawn(server, ownerUUID, tag, player);
            }
        }
    }

    public static CompanionEntity executeAutomaticRespawn(MinecraftServer server, UUID ownerUUID, CompoundTag tag, ServerPlayer player) {
        if (server == null || ownerUUID == null || tag == null || player == null) return null;

        CompanionSavedData savedData = CompanionSavedData.get(server.overworld());
        savedData.markRespawning(ownerUUID);

        // Deduplication guard: Check if an active instance already exists in memory or world
        if (tag.hasUUID("CompanionUUID")) {
            CompanionEntity existing = activeCompanions.get(tag.getUUID("CompanionUUID"));
            if (existing != null && existing.isAlive() && !existing.isRemoved()) {
                AICompanionMod.LOGGER.warn("[CompanionManager] Active companion already exists for owner {} during respawn. Re-using existing.", ownerUUID);
                savedData.markAlive(ownerUUID);
                return existing;
            }
        }

        String locationMode = AICompanionConfig.COMMON.respawnLocationMode.get();
        ServerLevel targetLevel;
        BlockPos targetPos;

        SafeSpawnFinder.SafeSpawnResult safeResult;
        if ("DEATH_LOCATION".equalsIgnoreCase(locationMode) && tag.contains("DeathDim")) {
            String deathDimStr = tag.getString("DeathDim");
            ResourceLocation dimLoc = deathDimStr.isEmpty() ? Level.OVERWORLD.location() : new ResourceLocation(deathDimStr);
            targetLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimLoc));
            if (targetLevel == null) targetLevel = player.serverLevel();
            targetPos = new BlockPos((int) tag.getDouble("DeathPosX"), (int) tag.getDouble("DeathPosY"), (int) tag.getDouble("DeathPosZ"));
            safeResult = SafeSpawnFinder.findSafeSpawnResult(targetLevel, targetPos, null, true);
        } else {
            targetLevel = player.serverLevel();
            targetPos = player.blockPosition();
            safeResult = SafeSpawnFinder.findSafeSpawnNearEntity(targetLevel, player, null);
        }

        Vec3 spawnVec;
        if (safeResult.isSuccess()) {
            spawnVec = safeResult.getPosition();
        } else {
            targetLevel = player.serverLevel();
            spawnVec = new Vec3(player.getX() + 1.2, player.getY(), player.getZ() + 1.2);
        }

        CompanionEntity companion = new CompanionEntity(ModEntities.COMPANION.get(), targetLevel);
        companion.load(tag);
        companion.setOwnerUUID(ownerUUID);
        companion.setPos(spawnVec.x, spawnVec.y, spawnVec.z);
        if (player.level() == targetLevel) {
            SafeSpawnFinder.faceEntity(companion, player);
        }
        companion.setHealth(companion.getMaxHealth());
        boolean taskResumed = false;
        if (companion.getCompanionBrain() != null) {
            var bp = companion.getCompanionBrain().getActiveBuildProject();
            var tm = companion.getCompanionBrain().getTaskManager();
            if (bp != null && bp.isActive()) {
                tm.resume();
                taskResumed = true;
            } else if (tm != null && tm.getCurrentTask() != null && !tm.isIdleOrUtilityTask(tm.getCurrentTask())) {
                tm.resume();
                taskResumed = true;
            }
        }
        if (!taskResumed) {
            companion.setCurrentState(CompanionState.FOLLOWING);
            companion.setPreferredIdleBehavior(DefaultBehavior.FOLLOW);
        }

        // Auto-organize inventory on respawn
        if (companion.getInventoryHandler() != null) {
            com.aicompanion.inventory.InventoryOrganizer.organize(companion.getInventoryHandler());
        }

        boolean added = targetLevel.addFreshEntity(companion);
        if (!added) {
            AICompanionMod.LOGGER.error("[CompanionManager] addFreshEntity failed during auto-respawn for owner {}. Reverting state to DEAD_WAITING_RESPAWN.", ownerUUID);
            savedData.markDead(ownerUUID);
            return null;
        }

        registerCompanion(companion);
        savedData.markAlive(ownerUUID);
        savedData.saveCompanion(companion);

        String compName = companion.getCustomCompanionName();
        player.sendSystemMessage(Component.literal("[" + compName + "] ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Ha reaparecido con éxito a tu lado con su inventario 100% conservado.")
                        .withStyle(ChatFormatting.GREEN)));

        companion.syncToOwner();
        AICompanionMod.LOGGER.info("[CompanionManager] Automatic respawn completed for companion {} of owner {}", compName, ownerUUID);
        return companion;
    }

    public static boolean dismissCompanion(ServerPlayer player) {
        CompanionEntity companion = getCompanionForOwner(player.getServer(), player.getUUID());
        if (companion == null) return false;

        CompanionSavedData savedData = CompanionSavedData.get(player.serverLevel());
        savedData.saveCompanion(companion);
        savedData.markDismissed(player.getUUID());
        unregisterCompanion(companion);
        companion.discard();
        return true;
    }

    public static boolean recallCompanion(ServerPlayer player) {
        CompanionEntity companion = getCompanionForOwner(player.getServer(), player.getUUID());
        if (companion == null) return false;

        companion.recallToOwnerWalking();
        return true;
    }
}
