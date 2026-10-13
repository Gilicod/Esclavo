package com.aicompanion.ai.coordination;

import com.aicompanion.AICompanionMod;
import com.aicompanion.ai.CompanionTask;
import com.aicompanion.ai.planning.Goal;
import com.aicompanion.ai.planning.PrerequisitePlanner;
import com.aicompanion.entity.CompanionEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe multi-companion coordination and cooperative work distribution engine for Esclavo.
 * Enables multiple companions in the same world to cooperatively share work,
 * reserve blocks (ores, trees, build locations) and entities to prevent race conditions,
 * decompose large multi-companion orders into dependency-tracked subtasks,
 * dynamically distribute jobs among available companions, and share spatial memories.
 */
public class CompanionCoordinator {
    private record Claim(UUID companionId, long expiresAtTick) {}

    private static final Map<BlockPos, Claim> CLAIMS = new ConcurrentHashMap<>();
    private static final Map<UUID, Claim> ENTITY_CLAIMS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<UUID>> OWNER_COMPANIONS = new ConcurrentHashMap<>();
    private static final Map<String, SharedMission> SHARED_MISSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<ResourceLocation, Set<BlockPos>>> SHARED_RESOURCES = new ConcurrentHashMap<>();
    private static final AtomicInteger MISSION_COUNTER = new AtomicInteger(100);
    private static final long DEFAULT_CLAIM_DURATION = 600L; // 30 seconds

    public static String nextMissionId() {
        return "M-" + MISSION_COUNTER.incrementAndGet();
    }

    // ==========================================
    // BLOCK & ENTITY CLAIMS
    // ==========================================

    public static boolean claimBlock(UUID companionId, BlockPos pos, long currentTick) {
        return claimBlock(companionId, pos, DEFAULT_CLAIM_DURATION, currentTick);
    }

    public static boolean claimBlock(UUID companionId, BlockPos pos, long durationTicks, long currentTick) {
        if (pos == null || companionId == null) return false;
        BlockPos immutable = pos.immutable();
        cleanExpired(currentTick);

        Claim existing = CLAIMS.get(immutable);
        if (existing == null || existing.companionId().equals(companionId) || existing.expiresAtTick() < currentTick) {
            CLAIMS.put(immutable, new Claim(companionId, currentTick + durationTicks));
            return true;
        }
        return false;
    }

    public static boolean isBlockClaimedByOther(UUID companionId, BlockPos pos, long currentTick) {
        if (pos == null) return false;
        Claim existing = CLAIMS.get(pos);
        if (existing == null) return false;
        if (existing.expiresAtTick() < currentTick) {
            CLAIMS.remove(pos);
            return false;
        }
        return companionId == null || !existing.companionId().equals(companionId);
    }

    public static void releaseBlock(UUID companionId, BlockPos pos) {
        if (pos == null || companionId == null) return;
        Claim existing = CLAIMS.get(pos);
        if (existing != null && existing.companionId().equals(companionId)) {
            CLAIMS.remove(pos);
        }
    }

    public static boolean claimEntity(UUID companionId, UUID entityId, long currentTick) {
        return claimEntity(companionId, entityId, DEFAULT_CLAIM_DURATION, currentTick);
    }

    public static boolean claimEntity(UUID companionId, UUID entityId, long durationTicks, long currentTick) {
        if (entityId == null || companionId == null) return false;
        cleanExpired(currentTick);

        Claim existing = ENTITY_CLAIMS.get(entityId);
        if (existing == null || existing.companionId().equals(companionId) || existing.expiresAtTick() < currentTick) {
            ENTITY_CLAIMS.put(entityId, new Claim(companionId, currentTick + durationTicks));
            return true;
        }
        return false;
    }

    public static boolean isEntityClaimedByOther(UUID companionId, UUID entityId, long currentTick) {
        if (entityId == null) return false;
        Claim existing = ENTITY_CLAIMS.get(entityId);
        if (existing == null) return false;
        if (existing.expiresAtTick() < currentTick) {
            ENTITY_CLAIMS.remove(entityId);
            return false;
        }
        return companionId == null || !existing.companionId().equals(companionId);
    }

    public static void releaseEntity(UUID companionId, UUID entityId) {
        if (entityId == null || companionId == null) return;
        Claim existing = ENTITY_CLAIMS.get(entityId);
        if (existing != null && existing.companionId().equals(companionId)) {
            ENTITY_CLAIMS.remove(entityId);
        }
    }

    // ==========================================
    // COMPANION REGISTRATION
    // ==========================================

    public static void registerCompanion(UUID ownerUUID, UUID companionUUID) {
        if (ownerUUID == null || companionUUID == null) return;
        OWNER_COMPANIONS.computeIfAbsent(ownerUUID, k -> ConcurrentHashMap.newKeySet()).add(companionUUID);
    }

    public static void unregisterCompanion(UUID ownerUUID, UUID companionUUID) {
        if (ownerUUID == null || companionUUID == null) return;
        Set<UUID> set = OWNER_COMPANIONS.get(ownerUUID);
        if (set != null) {
            set.remove(companionUUID);
        }
        releaseAll(companionUUID);
        // Release any assigned subtasks
        for (SharedMission mission : SHARED_MISSIONS.values()) {
            if (mission.getOwnerId().equals(ownerUUID)) {
                for (SubTaskRecord sub : mission.getSubtasks()) {
                    if (companionUUID.equals(sub.getAssignedCompanionId()) && sub.getStatus() != SubTaskStatus.COMPLETED) {
                        sub.setStatus(SubTaskStatus.PENDING);
                        sub.setAssignedCompanionId(null);
                    }
                }
            }
        }
    }

    public static Set<UUID> getCompanionsForOwner(UUID ownerUUID) {
        if (ownerUUID == null) return Collections.emptySet();
        Set<UUID> set = OWNER_COMPANIONS.get(ownerUUID);
        return set != null ? Collections.unmodifiableSet(set) : Collections.emptySet();
    }

    /**
     * Defensive team cooperation alert: notifies nearby idle/guarding teammates of the same owner
     * when a companion is attacked, coordinating defensive assistance without friendly fire.
     */
    public static void broadcastThreat(UUID ownerUUID, UUID victimId, net.minecraft.world.entity.LivingEntity threat, ServerLevel level) {
        if (ownerUUID == null || threat == null || !threat.isAlive() || level == null) return;
        if (!com.aicompanion.ai.perception.RiskEvaluator.isInherentlyHostile(threat)) return;

        Set<UUID> companionUuids = getCompanionsForOwner(ownerUUID);
        for (UUID compUuid : companionUuids) {
            if (compUuid.equals(victimId)) continue;
            CompanionEntity teammate = com.aicompanion.data.CompanionManager.getCompanionByUUID(level.getServer(), compUuid);
            if (teammate == null || !teammate.isAlive() || teammate.isRemoved()) continue;
            if (teammate.level() != level) continue;

            double distSq = teammate.distanceToSqr(threat);
            if (distSq > 16.0 * 16.0) continue; // Only assist if within 16 blocks

            var brain = teammate.getCompanionBrain();
            if (brain == null) continue;

            CompanionTask current = brain.getTaskManager().getCurrentTask();
            // Don't disrupt teammates actively fighting or fleeing
            if (current instanceof com.aicompanion.ai.tasks.FightTask || current instanceof com.aicompanion.ai.tasks.FleeTask) {
                continue;
            }

            // Route to dedicated onThreatAlert: handles priorities, civil task preservation, and never re-broadcasts
            brain.onThreatAlert(threat, victimId, level);
        }
    }

    public static void releaseAll(UUID companionId) {
        if (companionId == null) return;
        CLAIMS.entrySet().removeIf(entry -> entry.getValue().companionId().equals(companionId));
        ENTITY_CLAIMS.entrySet().removeIf(entry -> entry.getValue().companionId().equals(companionId));
    }

    public static void releaseAllBlocks(UUID companionId) {
        releaseAll(companionId);
    }

    public static void cleanExpired(long currentTick) {
        CLAIMS.entrySet().removeIf(entry -> entry.getValue().expiresAtTick() < currentTick);
        ENTITY_CLAIMS.entrySet().removeIf(entry -> entry.getValue().expiresAtTick() < currentTick);
    }

    // ==========================================
    // SHARED SPATIAL RESOURCE CACHE
    // ==========================================

    public static void rememberSharedResource(UUID ownerId, ResourceLocation blockId, BlockPos pos) {
        if (ownerId == null || blockId == null || pos == null) return;
        SHARED_RESOURCES.computeIfAbsent(ownerId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(blockId, k -> ConcurrentHashMap.newKeySet())
                .add(pos.immutable());
    }

    public static Optional<BlockPos> findSharedResource(UUID ownerId, ResourceLocation blockId, BlockPos origin, double maxRadius) {
        if (ownerId == null || blockId == null || origin == null) return Optional.empty();
        Map<ResourceLocation, Set<BlockPos>> map = SHARED_RESOURCES.get(ownerId);
        if (map == null) return Optional.empty();
        Set<BlockPos> set = map.get(blockId);
        if (set == null || set.isEmpty()) return Optional.empty();

        double maxDistSq = maxRadius * maxRadius;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos p : set) {
            double d = origin.distSqr(p);
            if (d <= maxDistSq && d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return Optional.ofNullable(best);
    }

    // ==========================================
    // COOPERATIVE SHARED MISSIONS
    // ==========================================

    public enum SubTaskStatus {
        PENDING,
        ASSIGNED,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    public static class SubTaskRecord {
        private final String id;
        private final Goal goal;
        private final String label;
        private final Set<String> dependencyIds = new HashSet<>();
        private UUID assignedCompanionId = null;
        private SubTaskStatus status = SubTaskStatus.PENDING;
        private long assignedTick = 0;
        private long lastProgressTick = 0;

        public SubTaskRecord(String id, Goal goal, String label) {
            this.id = id;
            this.goal = goal;
            this.label = label;
        }

        public String getId() { return id; }
        public Goal getGoal() { return goal; }
        public String getLabel() { return label; }
        public Set<String> getDependencyIds() { return dependencyIds; }
        public UUID getAssignedCompanionId() { return assignedCompanionId; }
        public SubTaskStatus getStatus() { return status; }
        public void setStatus(SubTaskStatus status) { this.status = status; }
        public void setAssignedCompanionId(UUID id) { this.assignedCompanionId = id; }
        public void setAssignedTick(long tick) { this.assignedTick = tick; }
        public void setLastProgressTick(long tick) { this.lastProgressTick = tick; }
    }

    public static class SharedMission {
        private final String missionId;
        private final UUID ownerId;
        private final String description;
        private final List<SubTaskRecord> subtasks = new ArrayList<>();
        private final long createdTick;
        private boolean finished = false;

        public SharedMission(String missionId, UUID ownerId, String description, long createdTick) {
            this.missionId = missionId;
            this.ownerId = ownerId;
            this.description = description;
            this.createdTick = createdTick;
        }

        public String getMissionId() { return missionId; }
        public UUID getOwnerId() { return ownerId; }
        public String getDescription() { return description; }
        public List<SubTaskRecord> getSubtasks() { return subtasks; }
        public boolean isFinished() { return finished; }
        public void setFinished(boolean finished) { this.finished = finished; }
    }

    public static SharedMission createSharedMission(UUID ownerId, String description, List<Goal> goals, long currentTick) {
        if (ownerId == null || goals == null || goals.isEmpty()) return null;
        String mId = "M-" + MISSION_COUNTER.incrementAndGet();
        SharedMission mission = new SharedMission(mId, ownerId, description, currentTick);

        int subIdx = 1;
        for (Goal g : goals) {
            String sId = mId + "-S" + (subIdx++);
            String label = g.getType().name() + " " + (g.getTargetResource() != null ? g.getTargetResource().getPath() : "");
            SubTaskRecord record = new SubTaskRecord(sId, g, label);
            mission.getSubtasks().add(record);
        }

        SHARED_MISSIONS.put(mId, mission);
        AICompanionMod.LOGGER.info("[ESCLAVO-COOP] Created SharedMission '{}': {} with {} subtasks", mId, description, mission.getSubtasks().size());
        return mission;
    }

    public static void tickCooperativeMissions(ServerLevel level) {
        if (level == null || SHARED_MISSIONS.isEmpty()) return;
        long currentTick = level.getGameTime();

        for (Iterator<Map.Entry<String, SharedMission>> it = SHARED_MISSIONS.entrySet().iterator(); it.hasNext(); ) {
            SharedMission mission = it.next().getValue();
            if (mission.isFinished()) continue;

            UUID ownerId = mission.getOwnerId();
            Player owner = level.getServer().getPlayerList().getPlayer(ownerId);

            boolean allDone = true;
            for (SubTaskRecord sub : mission.getSubtasks()) {
                if (sub.getStatus() != SubTaskStatus.COMPLETED) {
                    allDone = false;
                    break;
                }
            }

            if (allDone) {
                mission.setFinished(true);
                AICompanionMod.LOGGER.info("[ESCLAVO-COOP] SharedMission '{}' COMPLETADA con éxito!", mission.getMissionId());
                if (owner != null && owner.isAlive()) {
                    owner.sendSystemMessage(
                            Component.literal("[Equipo de Esclavos] ").withStyle(ChatFormatting.GOLD)
                                    .append(Component.literal("¡Misión completada con éxito: " + mission.getDescription() + "!").withStyle(ChatFormatting.GREEN))
                    );
                }
                it.remove();
                continue;
            }

            // Check stalled subtasks
            for (SubTaskRecord sub : mission.getSubtasks()) {
                if (sub.getStatus() == SubTaskStatus.ASSIGNED || sub.getStatus() == SubTaskStatus.IN_PROGRESS) {
                    UUID assignedId = sub.getAssignedCompanionId();
                    CompanionEntity worker = com.aicompanion.data.CompanionManager.getCompanionByUUID(level.getServer(), assignedId);
                    if (worker == null || !worker.isAlive() || worker.isRemoved()) {
                        AICompanionMod.LOGGER.warn("[ESCLAVO-COOP] Worker for subtask '{}' is missing/dead. Reassigning to pool.", sub.getId());
                        sub.setStatus(SubTaskStatus.PENDING);
                        sub.setAssignedCompanionId(null);
                    } else if (sub.lastProgressTick > 0 && (currentTick - sub.lastProgressTick > 1600)) { // 80 seconds without progress
                        AICompanionMod.LOGGER.warn("[ESCLAVO-COOP] Subtask '{}' stalled on companion {}. Reassigning.", sub.getId(), worker.getCustomCompanionName());
                        sub.setStatus(SubTaskStatus.PENDING);
                        sub.setAssignedCompanionId(null);
                    }
                }
            }

            // Distribute pending subtasks to idle companions
            Set<UUID> companionUuids = getCompanionsForOwner(ownerId);
            for (UUID compUuid : companionUuids) {
                CompanionEntity worker = com.aicompanion.data.CompanionManager.getCompanionByUUID(level.getServer(), compUuid);
                if (worker == null || !worker.isAlive() || worker.isRemoved()) continue;
                if (worker.getCompanionBrain() == null || worker.getCompanionBrain().getTaskManager().isBusy()) continue;

                // Find next ready subtask
                SubTaskRecord readySubtask = null;
                for (SubTaskRecord sub : mission.getSubtasks()) {
                    if (sub.getStatus() == SubTaskStatus.PENDING) {
                        // Check if dependencies are satisfied
                        boolean depsDone = true;
                        for (String depId : sub.getDependencyIds()) {
                            SubTaskRecord dep = findSubtaskInMission(mission, depId);
                            if (dep != null && dep.getStatus() != SubTaskStatus.COMPLETED) {
                                depsDone = false;
                                break;
                            }
                        }
                        if (depsDone) {
                            readySubtask = sub;
                            break;
                        }
                    }
                }

                if (readySubtask != null) {
                    readySubtask.setStatus(SubTaskStatus.IN_PROGRESS);
                    readySubtask.setAssignedCompanionId(compUuid);
                    readySubtask.setAssignedTick(currentTick);
                    readySubtask.setLastProgressTick(currentTick);

                    AICompanionMod.LOGGER.info("[ESCLAVO-COOP] Assigning subtask '{}' ({}) to companion '{}'",
                            readySubtask.getId(), readySubtask.getLabel(), worker.getCustomCompanionName());

                    List<CompanionTask> tasks = PrerequisitePlanner.resolvePlanWithPrerequisites(worker, readySubtask.getGoal());
                    if (!tasks.isEmpty()) {
                        for (CompanionTask t : tasks) {
                            t.setMissionId(readySubtask.getId());
                        }
                        worker.getCompanionBrain().applyTasks(tasks);
                    } else {
                        readySubtask.setStatus(SubTaskStatus.COMPLETED);
                    }
                }
            }
        }
    }

    public static void markSubtaskProgress(UUID companionId, boolean completed, boolean failed, long currentTick) {
        if (companionId == null) return;
        for (SharedMission mission : SHARED_MISSIONS.values()) {
            for (SubTaskRecord sub : mission.getSubtasks()) {
                if (companionId.equals(sub.getAssignedCompanionId())) {
                    sub.setLastProgressTick(currentTick);
                    if (completed) {
                        sub.setStatus(SubTaskStatus.COMPLETED);
                        AICompanionMod.LOGGER.info("[ESCLAVO-COOP] Subtask '{}' completed by {}", sub.getId(), companionId);
                    } else if (failed) {
                        sub.setStatus(SubTaskStatus.FAILED);
                        AICompanionMod.LOGGER.warn("[ESCLAVO-COOP] Subtask '{}' failed by {}", sub.getId(), companionId);
                    }
                }
            }
        }
    }

    private static SubTaskRecord findSubtaskInMission(SharedMission mission, String id) {
        for (SubTaskRecord sub : mission.getSubtasks()) {
            if (sub.getId().equals(id)) return sub;
        }
        return null;
    }

    public static List<SharedMission> getMissionsForOwner(UUID ownerId) {
        if (ownerId == null) return Collections.emptyList();
        List<SharedMission> result = new ArrayList<>();
        for (SharedMission m : SHARED_MISSIONS.values()) {
            if (m.getOwnerId().equals(ownerId)) {
                result.add(m);
            }
        }
        return result;
    }

    public static void cancelMission(String missionId) {
        if (missionId != null) {
            SHARED_MISSIONS.remove(missionId);
        }
    }
}
