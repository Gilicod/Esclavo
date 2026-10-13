package com.aicompanion.ai.chunk;

import com.aicompanion.AICompanionConfig;
import com.aicompanion.AICompanionMod;
import com.aicompanion.entity.CompanionEntity;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.common.world.ForgeChunkManager;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CompanionChunkManager {
    public static final TicketType<UUID> COMPANION_TICKET = TicketType.create("aicompanion", UUID::compareTo);
    private static final Map<UUID, Set<ChunkPos>> companionForcedChunks = new ConcurrentHashMap<>();

    public static void updateChunkTickets(CompanionEntity companion) {
        if (companion == null || companion.isRemoved() || !companion.isAlive()) return;
        if (!Boolean.TRUE.equals(AICompanionConfig.COMMON.enableChunkLoading.get())) return;
        if (!(companion.level() instanceof ServerLevel serverLevel)) return;

        ChunkPos center = new ChunkPos(companion.blockPosition());
        int configRadius = AICompanionConfig.COMMON.chunkLoadingRadius.get();
        int radius = Math.max(2, configRadius);

        Set<ChunkPos> desiredChunks = new HashSet<>();
        for (int dx = -(radius - 1); dx <= (radius - 1); dx++) {
            for (int dz = -(radius - 1); dz <= (radius - 1); dz++) {
                desiredChunks.add(new ChunkPos(center.x + dx, center.z + dz));
            }
        }
        desiredChunks.add(center);

        UUID uuid = companion.getUUID();
        Set<ChunkPos> currentForced = companionForcedChunks.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
        ServerChunkCache chunkSource = serverLevel.getChunkSource();

        // Unforce chunks no longer within companion radius
        for (ChunkPos oldChunk : new HashSet<>(currentForced)) {
            if (!desiredChunks.contains(oldChunk)) {
                try {
                    chunkSource.removeRegionTicket(COMPANION_TICKET, oldChunk, 3, uuid, true);
                    serverLevel.setChunkForced(oldChunk.x, oldChunk.z, false);
                    ForgeChunkManager.forceChunk(serverLevel, "aicompanion", uuid, oldChunk.x, oldChunk.z, false, false);
                } catch (Exception ignored) {}
                currentForced.remove(oldChunk);
            }
        }

        // Force desired chunks with entity-ticking region tickets (distance=3 -> ticket level 30: ENTITY_TICKING)
        for (ChunkPos chunk : desiredChunks) {
            try {
                serverLevel.setChunkForced(chunk.x, chunk.z, true);
                chunkSource.addRegionTicket(COMPANION_TICKET, chunk, 3, uuid, true);
                chunkSource.getChunk(chunk.x, chunk.z, ChunkStatus.FULL, true);
                ForgeChunkManager.forceChunk(serverLevel, "aicompanion", uuid, chunk.x, chunk.z, true, true);
                currentForced.add(chunk);
            } catch (Exception e) {
                AICompanionMod.LOGGER.warn("[CompanionChunkManager] Failed to force entity-ticking chunk ({}, {}): {}", chunk.x, chunk.z, e.getMessage());
            }
        }

        // Keep server dimension awake
        serverLevel.resetEmptyTime();
    }

    public static void releaseTickets(CompanionEntity companion) {
        if (companion == null) return;
        UUID uuid = companion.getUUID();
        Set<ChunkPos> forced = companionForcedChunks.remove(uuid);
        if (forced != null && companion.level() instanceof ServerLevel serverLevel) {
            ServerChunkCache chunkSource = serverLevel.getChunkSource();
            for (ChunkPos chunk : forced) {
                try {
                    chunkSource.removeRegionTicket(COMPANION_TICKET, chunk, 3, uuid, true);
                    serverLevel.setChunkForced(chunk.x, chunk.z, false);
                    ForgeChunkManager.forceChunk(serverLevel, "aicompanion", uuid, chunk.x, chunk.z, false, false);
                } catch (Exception ignored) {}
            }
        }
    }

    public static boolean isChunkEntityTicking(ServerLevel level, ChunkPos pos) {
        if (level == null || pos == null) return false;
        long posLong = pos.toLong();
        return level.areEntitiesLoaded(posLong) || level.getChunkSource().isPositionTicking(posLong);
    }
}
