package com.aicompanion.ai;

import com.aicompanion.AICompanionConfig;
import com.aicompanion.ai.chunk.CompanionChunkManager;
import com.aicompanion.ai.memory.CompanionMemory;
import com.aicompanion.ai.perception.CompanionPerception;
import com.aicompanion.ai.perception.RiskEvaluator;
import com.aicompanion.inventory.EquipmentManager;
import com.aicompanion.ai.planning.Goal;
import com.aicompanion.ai.planning.GoalPlanner;
import com.aicompanion.ai.planning.ReplanManager;
import com.aicompanion.ai.provider.AIProvider;
import com.aicompanion.ai.provider.LocalRuleProvider;
import com.aicompanion.ai.provider.OpenAICompatibleProvider;
import com.aicompanion.ai.stuck.StuckDetector;
import com.aicompanion.ai.tasks.ReturnTask;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class CompanionBrain {
    private final CompanionEntity companion;
    private final TaskManager taskManager;
    private final CompanionMemory memory = new CompanionMemory();
    private final CompanionPerception perception;
    private final StuckDetector stuckDetector;
    private final ReplanManager replanManager;

    private AIProvider provider = new LocalRuleProvider();
    private String lastUserOrderText = "";
    private final List<Goal> lastUserGoals = new ArrayList<>();
    private final java.util.Map<java.util.UUID, Long> discardedItemEntities = new java.util.concurrent.ConcurrentHashMap<>();
    /** Resources of the most recent gathering mission (used by a standalone "tráemelos"). */
    private final List<net.minecraft.resources.ResourceLocation> lastObtainTargets = new ArrayList<>();
    /** Pending interactive house dialogue (null when none). */
    private com.aicompanion.ai.building.BuildDialogSession buildSession = null;
    /** Enterprise permanent build project anchor. */
    private com.aicompanion.ai.building.BuildProject activeBuildProject = null;

    public com.aicompanion.ai.building.BuildProject getActiveBuildProject() {
        return activeBuildProject;
    }

    public void setActiveBuildProject(com.aicompanion.ai.building.BuildProject activeBuildProject) {
        this.activeBuildProject = activeBuildProject;
    }

    public void clearActiveBuildProject() {
        this.activeBuildProject = null;
    }

    private static boolean interactiveBuildEnabled() {
        try {
            return AICompanionConfig.COMMON.interactiveBuild.get();
        } catch (Exception e) {
            return true;
        }
    }

    private void deliverBuildReply(int requestId, com.aicompanion.ai.building.BuildDialogSession.Reply reply,
                                   com.aicompanion.ai.order.OrderSubmitMode submitMode, net.minecraft.server.level.ServerPlayer player) {
        companion.tellOwner(reply.message());
        if (reply.finished()) {
            buildSession = null;
            if (reply.goal() != null) {
                applyGoalsWithMode(requestId, java.util.List.of(reply.goal()), submitMode, player);
                return;
            }
            sendResult(requestId, player, com.aicompanion.ai.order.OrderResultStatus.ACCEPTED, reply.message(), 0, "");
            return;
        }
        String firstLine = reply.message().split("\n", 2)[0];
        sendResult(requestId, player, com.aicompanion.ai.order.OrderResultStatus.NEEDS_CLARIFICATION, firstLine, 0, "");
    }

    public CompanionBrain(CompanionEntity companion) {
        this.companion = companion;
        this.taskManager = new TaskManager(companion);
        this.perception = new CompanionPerception(companion);
        this.stuckDetector = new StuckDetector(companion);
        this.replanManager = new ReplanManager(companion);
        updateProvider();
    }

    public void updateProvider() {
        String mode = AICompanionConfig.COMMON.aiMode.get();
        String prov = AICompanionConfig.COMMON.aiProvider.get();
        if ("LLM_ONLY".equalsIgnoreCase(mode) || "LOCAL_LLM".equalsIgnoreCase(mode) || "HYBRID".equalsIgnoreCase(mode)
                || "OPENAI".equalsIgnoreCase(prov) || "OLLAMA".equalsIgnoreCase(prov)) {
            this.provider = new OpenAICompatibleProvider();
        } else {
            this.provider = new LocalRuleProvider();
        }
    }

    public void tick() {
        taskManager.tick();
        stuckDetector.tick();

        // Pilar 4: Sensory Perception and Survival Daemon (every 10 ticks)
        if (companion.tickCount % 10 == 0 && companion.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            perception.scan(sl);
            RiskEvaluator.RiskLevel risk = RiskEvaluator.evaluate(companion, perception);
            if (risk == RiskEvaluator.RiskLevel.CRITICAL) {
                handleCriticalRisk(sl);
            }
        }

        // Pilar 6: Predictive Inventory Logistics & Capacity Check (every 40 ticks)
        if (companion.tickCount % 40 == 0) {
            checkInventoryCapacity();
        }

        if (companion.tickCount % 100 == 0) {
            CompanionChunkManager.updateChunkTickets(companion);
            if (!discardedItemEntities.isEmpty() && companion.level() != null) {
                long now = companion.level().getGameTime();
                discardedItemEntities.entrySet().removeIf(e -> e.getValue() < now);
            }
        }
    }

    private void checkInventoryCapacity() {
        if (com.aicompanion.AICompanionConfig.COMMON.autoOrganizeInventory.get()) {
            com.aicompanion.inventory.InventoryOrganizer.compact(companion.getInventoryHandler());
        }
        if (com.aicompanion.AICompanionConfig.COMMON.allowAutoDiscard.get() && companion.getInventoryHandler().isCapacityCritical()) {
            companion.getInventoryHandler().purgeTrashItems(companion);
        }
    }

    public void recordDiscardedItem(net.minecraft.world.entity.item.ItemEntity itemEntity) {
        if (itemEntity == null) return;
        long now = companion.level() != null ? companion.level().getGameTime() : 0L;
        discardedItemEntities.put(itemEntity.getUUID(), now + 1200L); // 60 seconds
    }

    public boolean isDiscardedItem(net.minecraft.world.entity.item.ItemEntity itemEntity) {
        if (itemEntity == null || discardedItemEntities.isEmpty()) return false;
        long now = companion.level() != null ? companion.level().getGameTime() : 0L;
        Long expires = discardedItemEntities.get(itemEntity.getUUID());
        if (expires == null) return false;
        if (now > expires) {
            discardedItemEntities.remove(itemEntity.getUUID());
            return false;
        }
        return true;
    }

    public List<Goal> getLastUserGoals() {
        synchronized (lastUserGoals) {
            return new ArrayList<>(lastUserGoals);
        }
    }

    private void handleCriticalRisk(net.minecraft.server.level.ServerLevel sl) {
        CompanionTask current = taskManager.getCurrentTask();
        if (current != null) {
            String type = current.getTaskType();
            if ("FIGHT".equals(type) || "FLEE".equals(type)) {
                return; // Already actively resolving emergency survival
            }
            // 1.13.9: descansando solo se interrumpe si la amenaza está realmente cerca (<8 bloques)
            if ("HEAL".equals(type)) {
                net.minecraft.world.entity.LivingEntity near = perception.getPrimaryThreat();
                if (near == null || companion.distanceToSqr(near) > 64.0) {
                    return;
                }
            }
        }

        net.minecraft.world.entity.LivingEntity threat = perception.getPrimaryThreat();
        if (threat == null) {
            // No living enemy to fight or flee from!
            if (companion.isInLava()) {
                com.aicompanion.ai.stuck.RescueProtocolEngine rescue = stuckDetector != null ? stuckDetector.getRescueEngine() : null;
                if (rescue != null && !rescue.isRescuing()) {
                    rescue.startRescue(sl);
                }
            }
            return;
        }

        boolean hasWeapon = EquipmentManager.hasWeaponEquipped(companion) || EquipmentManager.findBestWeaponSlot(companion) != -1;
        boolean isSwellingCreeper = threat instanceof net.minecraft.world.entity.monster.Creeper creeper && creeper.getSwellDir() > 0;
        boolean isOverpowering = RiskEvaluator.isOverpoweringThreat(threat, companion);
        boolean healthSafe = (companion.getHealth() / companion.getMaxHealth() > 0.35F) && companion.getHealth() > 6.0F;
        boolean canFight = healthSafe && !isSwellingCreeper && !isOverpowering;

        String taskMissionId = current != null ? current.getMissionId() : "S/N";

        if (canFight) {
            if (current != null) {
                current.logEvent("Amenaza detectada: " + threat.getName().getString());
                current.logEvent("Pausando temporalmente tarea en curso para autodefensa...");
            }
            int weaponSlot = EquipmentManager.findBestWeaponSlot(companion);
            if (weaponSlot != -1 && weaponSlot != com.aicompanion.inventory.CompanionInventoryHandler.SLOT_MAINHAND) {
                EquipmentManager.swapMainHandSafely(companion, weaponSlot);
            }
            if (current != null) {
                current.logEvent("Arma/herramienta lista en mano: " + companion.getMainHandItem().getHoverName().getString() + ". Iniciando autodefensa...");
            }
            com.aicompanion.AICompanionMod.LOGGER.warn("[ESCLAVO-INTERRUPT] Critical risk detected! Interrupting with FightTask against {}", threat.getName().getString());
            com.aicompanion.ai.tasks.FightTask fight = new com.aicompanion.ai.tasks.FightTask(companion, threat);
            fight.setMissionId(taskMissionId);
            taskManager.interruptWithEmergency(fight);
        } else {
            if (current != null && threat != null) {
                String reason;
                if (isSwellingCreeper) {
                    reason = "creeper a punto de explotar (" + threat.getName().getString() + ")";
                } else if (isOverpowering) {
                    reason = "enemigo muy superior (" + threat.getName().getString() + ")";
                } else if (!hasWeapon) {
                    reason = "falta de arma adecuada frente a " + threat.getName().getString();
                } else {
                    reason = "salud baja (" + (int)companion.getHealth() + "/" + (int)companion.getMaxHealth() + " HP)";
                }
                current.logEvent("Peligro inminente por " + reason + ". Retirada táctica preventiva...");
            }
            net.minecraft.world.phys.Vec3 center = perception.getThreatCenter();
            int hits = recordDangerZone(center);
            com.aicompanion.AICompanionMod.LOGGER.warn("[ESCLAVO-INTERRUPT] Critical risk detected! Cannot engage directly, interrupting with FleeTask (amenaza={}, HP={}, overpowering={}, zona={} huidas)",
                    threat != null ? threat.getName().getString() : "?", companion.getHealth(), isOverpowering, hits);
            com.aicompanion.ai.tasks.FleeTask flee = new com.aicompanion.ai.tasks.FleeTask(companion, center, 20.0);
            flee.setMissionId(taskMissionId);
            taskManager.interruptWithEmergency(flee);
        }
    }

    /**
     * Instant sensory threat reaction called directly when the companion receives damage.
     * Evaluates the aggressor, pauses the current work without loss of progress, equips a weapon,
     * and engages in self-defense or tactical retreat.
     */
    public void onAttacked(net.minecraft.world.entity.LivingEntity attacker, net.minecraft.world.damagesource.DamageSource source) {
        if (companion == null || !companion.isAlive() || attacker == null || !attacker.isAlive()) return;
        if (!RiskEvaluator.isHostileThreat(attacker, companion)) return;

        perception.recordDirectAttacker(attacker);

        // Defensive team cooperation broadcast: alert nearby companions
        if (companion.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            com.aicompanion.ai.coordination.CompanionCoordinator.broadcastThreat(companion.getOwnerUUID(), companion.getUUID(), attacker, sl);
        }

        CompanionTask current = taskManager.getCurrentTask();
        if (current instanceof com.aicompanion.ai.tasks.FightTask fightTask) {
            if (fightTask.getTarget() == null || !fightTask.getTarget().isAlive() || companion.distanceToSqr(attacker) < companion.distanceToSqr(fightTask.getTarget())) {
                fightTask.setTarget(attacker);
            }
            return;
        }
        if (current instanceof com.aicompanion.ai.tasks.FleeTask) {
            return; // Already actively fleeing/evacuating
        }

        boolean isSwellingCreeper = attacker instanceof net.minecraft.world.entity.monster.Creeper creeper && creeper.getSwellDir() > 0;
        boolean isOverpowering = RiskEvaluator.isOverpoweringThreat(attacker, companion);
        float fleeThreshold = isOverpowering ? (companion.getMaxHealth() * 0.75F) : Math.max(8.0F, companion.getMaxHealth() * 0.4F);
        String taskMissionId = current != null ? current.getMissionId() : "S/N";

        if (companion.getHealth() <= fleeThreshold || isSwellingCreeper || (isOverpowering && !EquipmentManager.hasWeaponEquipped(companion))) {
            com.aicompanion.AICompanionMod.LOGGER.warn("[ESCLAVO-INTERRUPT] Attacked by dangerous threat ({}, HP {}). Initiating tactical retreat!",
                    attacker.getName().getString(), companion.getHealth());
            if (current != null) {
                current.logEvent("Ataque peligroso recibido de " + attacker.getName().getString() + " (Salud: " + (int)companion.getHealth() + " HP). Retirada táctica...");
            }
            net.minecraft.world.phys.Vec3 center = attacker.position();
            recordDangerZone(center);
            com.aicompanion.ai.tasks.FleeTask flee = new com.aicompanion.ai.tasks.FleeTask(companion, center, 20.0);
            flee.setMissionId(taskMissionId);
            taskManager.interruptWithEmergency(flee);
            return;
        }

        if (current != null) {
            current.logEvent("Amenaza detectada: " + attacker.getName().getString());
            current.logEvent("Pausando temporalmente tarea en curso para autodefensa...");
        }

        int weaponSlot = EquipmentManager.findBestWeaponSlot(companion);
        if (weaponSlot != -1 && weaponSlot != com.aicompanion.inventory.CompanionInventoryHandler.SLOT_MAINHAND) {
            EquipmentManager.swapMainHandSafely(companion, weaponSlot);
        }

        if (current != null) {
            current.logEvent("Espada equipada en mano principal: " + companion.getMainHandItem().getHoverName().getString() + ". Iniciando autodefensa...");
        }

        com.aicompanion.ai.tasks.FightTask fight = new com.aicompanion.ai.tasks.FightTask(companion, attacker);
        fight.setMissionId(taskMissionId);
        taskManager.interruptWithEmergency(fight);
    }

    // 1.13.26: Deduplicación y enfriamiento para alertas de auxilio de compañeros
    private java.util.UUID lastDistressThreatUUID = null;
    private long lastDistressAlertTick = 0;

    /**
     * Reacción a alerta de amenaza coordinada de un compañero de equipo.
     * Diseñado para evitar recursión infinita (NO retransmite alertas), aplicar deduplicación
     * y preservar las tareas civiles (minería, tala, construcción, agricultura) salvo que la amenaza
     * esté en proximidad inmediata (<= 6 bloques). Compañeros libres o en guardia asisten hasta 16 bloques.
     */
    public void onThreatAlert(net.minecraft.world.entity.LivingEntity threat, java.util.UUID originCompanionId, net.minecraft.server.level.ServerLevel level) {
        if (companion == null || !companion.isAlive() || threat == null || !threat.isAlive()) return;
        if (!RiskEvaluator.isHostileThreat(threat, companion)) return;

        long now = companion.level().getGameTime();
        if (threat.getUUID().equals(lastDistressThreatUUID) && (now - lastDistressAlertTick) < 40L) {
            return; // Enfriamiento / deduplicación de alerta
        }
        lastDistressThreatUUID = threat.getUUID();
        lastDistressAlertTick = now;

        CompanionTask current = taskManager.getCurrentTask();
        if (current instanceof com.aicompanion.ai.tasks.FightTask || current instanceof com.aicompanion.ai.tasks.FleeTask) {
            return; // Ya está combatiendo o huyendo activamente
        }

        double distSq = companion.distanceToSqr(threat);
        if (distSq > 16.0 * 16.0) {
            return; // Fuera del radio máximo de asistencia en equipo
        }

        boolean isCivilTask = current instanceof com.aicompanion.ai.tasks.MineTask
                || current instanceof com.aicompanion.ai.tasks.ChopTask
                || current instanceof com.aicompanion.ai.tasks.BuildTask
                || current instanceof com.aicompanion.ai.tasks.FarmTask;

        // Tareas civiles protegidas: solo asisten si el mob está a distancia de riesgo inminente (<= 6 bloques)
        if (isCivilTask && distSq > 36.0) {
            return;
        }

        // Si la salud es crítica, no carga al combate defensivo ajeno
        if (companion.getHealth() <= 6.0F) {
            return;
        }

        com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-COOP] Teammate '{}' responding to distress alert from '{}' against threat '{}'",
                companion.getCustomCompanionName(), originCompanionId, threat.getName().getString());

        String taskMissionId = current != null ? current.getMissionId() : "S/N";
        if (current != null) {
            current.logEvent("Asistiendo a compañero en peligro contra: " + threat.getName().getString());
            current.logEvent("Pausando temporalmente tarea en curso para defensa de equipo...");
        }

        int weaponSlot = EquipmentManager.findBestWeaponSlot(companion);
        if (weaponSlot != -1 && weaponSlot != com.aicompanion.inventory.CompanionInventoryHandler.SLOT_MAINHAND) {
            EquipmentManager.swapMainHandSafely(companion, weaponSlot);
        }

        if (current != null) {
            current.logEvent("Espada equipada en mano principal: " + companion.getMainHandItem().getHoverName().getString() + ". Asistiendo a compañero...");
        }

        com.aicompanion.ai.tasks.FightTask fight = new com.aicompanion.ai.tasks.FightTask(companion, threat);
        fight.setMissionId(taskMissionId);
        taskManager.interruptWithEmergency(fight);
        // IMPORTANTE: NO retransmite broadcastThreat() para eliminar cualquier bucle recursivo.
    }

    // ===== 1.13.9: memoria temporal de zonas peligrosas (de dónde tuvo que huir) =====
    private static final class DangerZone {
        net.minecraft.world.phys.Vec3 center;
        int hits;
        long expiresAt;
    }

    private final java.util.List<DangerZone> dangerZones = new java.util.ArrayList<>();
    private static final long DANGER_ZONE_TTL = 2400L; // 2 minutos

    /** Registra una huida desde {@code center}; devuelve cuántas veces ha huido de esa zona. */
    public int recordDangerZone(net.minecraft.world.phys.Vec3 center) {
        if (center == null) return 0;
        long now = companion.level().getGameTime();
        dangerZones.removeIf(z -> z.expiresAt < now);
        for (DangerZone z : dangerZones) {
            // Merge if within 16 blocks (same encounter area / roaming monster)
            if (z.center.distanceToSqr(center) <= 256.0) {
                z.hits++;
                z.center = z.center.add(center).scale(0.5);
                z.expiresAt = now + DANGER_ZONE_TTL;
                return z.hits;
            }
        }
        DangerZone z = new DangerZone();
        z.center = center;
        z.hits = 1;
        z.expiresAt = now + DANGER_ZONE_TTL;
        dangerZones.add(z);
        if (dangerZones.size() > 16) dangerZones.remove(0);
        return 1;
    }

    /** true si {@code pos} está a menos de {@code radius} de una zona de la que ya huyó al menos {@code minHits} veces. */
    public boolean isNearDangerZone(net.minecraft.core.BlockPos pos, double radius, int minHits) {
        if (pos == null || dangerZones.isEmpty()) return false;
        long now = companion.level().getGameTime();
        double r2 = radius * radius;
        net.minecraft.world.phys.Vec3 p = net.minecraft.world.phys.Vec3.atCenterOf(pos);
        for (DangerZone z : dangerZones) {
            if (z.expiresAt >= now && z.hits >= minHits) {
                // Cylindrical check (horizontal radius + vertical range of 36 blocks)
                double dx = z.center.x - p.x;
                double dz = z.center.z - p.z;
                double dy = Math.abs(z.center.y - p.y);
                if (dx * dx + dz * dz <= r2 && dy <= 36.0) {
                    return true;
                }
                // Direct spherical check
                if (z.center.distanceToSqr(p) <= r2) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the travel path between 'from' and 'to' passes within corridorRadius of an active danger zone.
     * Prevents the companion from selecting a target located behind or across a dangerous room/boss.
     */
    public boolean isPathNearDangerZone(net.minecraft.core.BlockPos from, net.minecraft.core.BlockPos to, double corridorRadius, int minHits) {
        if (from == null || to == null || dangerZones.isEmpty()) return false;
        long now = companion.level().getGameTime();
        double r2 = corridorRadius * corridorRadius;
        net.minecraft.world.phys.Vec3 vFrom = net.minecraft.world.phys.Vec3.atCenterOf(from);
        net.minecraft.world.phys.Vec3 vTo = net.minecraft.world.phys.Vec3.atCenterOf(to);
        net.minecraft.world.phys.Vec3 path = vTo.subtract(vFrom);
        double pathLenSq = path.lengthSqr();
        if (pathLenSq < 1.0) return isNearDangerZone(to, corridorRadius, minHits);

        for (DangerZone z : dangerZones) {
            if (z.expiresAt >= now && z.hits >= minHits) {
                net.minecraft.world.phys.Vec3 toThreat = z.center.subtract(vFrom);
                double t = Math.max(0.0, Math.min(1.0, toThreat.dot(path) / pathLenSq));
                net.minecraft.world.phys.Vec3 projection = vFrom.add(path.scale(t));
                if (projection.distanceToSqr(z.center) <= r2) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Computes a cardinal horizontal Direction pointing directly away from the closest active danger zone ("por otro lado").
     */
    public net.minecraft.core.Direction getDirectionAwayFromDanger(net.minecraft.core.BlockPos origin) {
        if (origin == null || dangerZones.isEmpty()) return null;
        long now = companion.level().getGameTime();
        DangerZone closest = null;
        double bestDistSq = Double.MAX_VALUE;
        net.minecraft.world.phys.Vec3 o = net.minecraft.world.phys.Vec3.atCenterOf(origin);
        for (DangerZone z : dangerZones) {
            if (z.expiresAt >= now) {
                double d = z.center.distanceToSqr(o);
                if (d < bestDistSq) {
                    bestDistSq = d;
                    closest = z;
                }
            }
        }
        if (closest == null) return null;
        double dx = o.x - closest.center.x;
        double dz = o.z - closest.center.z;
        if (Math.abs(dx) < 0.1 && Math.abs(dz) < 0.1) return companion.getDirection().getOpposite();
        return net.minecraft.core.Direction.getNearest(dx, 0.0, dz);
    }

    public void executeNaturalOrder(String text) {
        this.lastUserOrderText = text != null ? text.trim() : "";
        com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] executeNaturalOrder: text='{}'", text);
        String mode = AICompanionConfig.COMMON.aiMode.get();
        LocalRuleProvider localRules = new LocalRuleProvider();

        // 0. Instant natural renaming intent
        String lower = text != null ? text.trim().toLowerCase() : "";
        if (lower.startsWith("ahora te llamas ") || lower.startsWith("tu nombre es ") || lower.startsWith("cambiar nombre a ")
                || lower.startsWith("cámbiate el nombre a ") || lower.startsWith("cambiate el nombre a ")
                || lower.startsWith("renómbrate como ") || lower.startsWith("renombrate como ")
                || lower.startsWith("llámate ") || lower.startsWith("llamate ")) {
            String newName = text.trim();
            if (lower.startsWith("ahora te llamas ")) newName = newName.substring(16).trim();
            else if (lower.startsWith("tu nombre es ")) newName = newName.substring(13).trim();
            else if (lower.startsWith("cambiar nombre a ")) newName = newName.substring(17).trim();
            else if (lower.startsWith("cámbiate el nombre a ")) newName = newName.substring(21).trim();
            else if (lower.startsWith("cambiate el nombre a ")) newName = newName.substring(21).trim();
            else if (lower.startsWith("renómbrate como ")) newName = newName.substring(16).trim();
            else if (lower.startsWith("renombrate como ")) newName = newName.substring(16).trim();
            else if (lower.startsWith("llámate ")) newName = newName.substring(8).trim();
            else if (lower.startsWith("llamate ")) newName = newName.substring(8).trim();

            if (!newName.isEmpty()) {
                if (newName.length() > 32) newName = newName.substring(0, 32);
                companion.setCustomCompanionName(newName);
                if (companion.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    com.aicompanion.data.CompanionSavedData.get(sl).saveCompanion(companion);
                }
                companion.syncToOwner();
                companion.tellOwner("¡Entendido! Ahora me llamo " + newName + ".");
                return;
            }
        }

        // If LLM_ONLY is explicitly set, try LLM provider first
        if ("LLM_ONLY".equalsIgnoreCase(mode)) {
            com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] executeNaturalOrder: LLM_ONLY mode, requesting external plan for '{}'", text);
            provider.plan(companion, text).thenAccept(llmGoals -> {
                if (llmGoals != null && !llmGoals.isEmpty()) {
                    applyGoalsOnServerThread(llmGoals);
                } else if (AICompanionConfig.COMMON.aiAllowFallback.get()) {
                    com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] executeNaturalOrder: LLM plan empty, attempting local fallback");
                    List<Goal> fbGoals = localRules.plan(companion, text).join();
                    if (!fbGoals.isEmpty()) {
                        applyGoalsOnServerThread(fbGoals);
                    } else {
                        warnUnrecognizedOrder(text);
                    }
                } else {
                    warnUnrecognizedOrder(text);
                }
            });
            return;
        }

        // 1. Instant deterministic local rules
        List<Goal> localGoals = localRules.plan(companion, text).join();
        com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] executeNaturalOrder parsed {} local goals: {}", localGoals.size(), localGoals);
        if (!localGoals.isEmpty()) {
            processAndDispatchGoals(localGoals, text);
            return;
        }

        // 2. Hybrid / LLM fallback
        String prov = AICompanionConfig.COMMON.aiProvider.get();
        if ("HYBRID".equalsIgnoreCase(mode) || "LOCAL_LLM".equalsIgnoreCase(mode)
                || "OPENAI".equalsIgnoreCase(prov) || "OLLAMA".equalsIgnoreCase(prov)) {
            com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] executeNaturalOrder: Trying LLM fallback for '{}'", text);
            provider.plan(companion, text).thenAccept(llmGoals -> {
                if (llmGoals != null && !llmGoals.isEmpty()) {
                    processAndDispatchGoals(llmGoals, text);
                } else {
                    warnUnrecognizedOrder(text);
                }
            });
        } else {
            warnUnrecognizedOrder(text);
        }
    }

    private void warnUnrecognizedOrder(String text) {
        com.aicompanion.AICompanionMod.LOGGER.warn("[ESCLAVO-DEBUG] executeNaturalOrder: No goals recognized from '{}'", text);
        if (companion.getOwnerPlayer() != null) {
            companion.getOwnerPlayer().sendSystemMessage(
                    Component.literal("[" + companion.getCustomCompanionName() + "] ")
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal("No entendí la orden: \"" + text + "\". Ejemplos: 'madera 64', 'minar 20 hierro', 'sígueme', 'guardar en cofre'.").withStyle(ChatFormatting.YELLOW))
            );
        }
    }

    public void executeGoals(List<Goal> goals) {
        applyGoalsOnServerThread(goals);
    }

    private String activeMissionId = "";

    public String getActiveMissionId() {
        if (activeMissionId == null || activeMissionId.isEmpty()) {
            activeMissionId = "MIS-" + Long.toHexString(System.currentTimeMillis() & 0xFFFFFFF);
        }
        return activeMissionId;
    }

    public void setActiveMissionId(String id) {
        this.activeMissionId = id != null ? id : "";
    }

    public void recordUserMission(List<Goal> goals, String description) {
        if (goals != null && !goals.isEmpty()) {
            this.activeMissionId = "MIS-" + Long.toHexString(System.currentTimeMillis() & 0xFFFFFFF);
            synchronized (lastUserGoals) {
                lastUserGoals.clear();
                lastUserGoals.addAll(goals);
            }
        } else {
            this.activeMissionId = "";
        }
        if (description != null && !description.isEmpty()) {
            this.lastUserOrderText = description;
        }
    }

    public boolean hasLastUserMission() {
        synchronized (lastUserGoals) {
            return !lastUserGoals.isEmpty();
        }
    }

    public String getLastUserOrderText() {
        return lastUserOrderText;
    }

    public void applyTasks(List<CompanionTask> tasks) {
        if (tasks == null || tasks.isEmpty()) return;
        String mId = getActiveMissionId();
        for (CompanionTask t : tasks) {
            if (t != null && (t.getMissionId() == null || t.getMissionId().isEmpty())) {
                t.setMissionId(mId);
            }
        }
        MinecraftServer server = companion.getServer();
        if (server != null) {
            server.execute(() -> {
                taskManager.stopWithoutDefault();
                taskManager.setTask(tasks.get(0));
                for (int i = 1; i < tasks.size(); i++) {
                    taskManager.getTaskQueue().enqueue(tasks.get(i));
                }
                if (companion.getOwnerPlayer() != null) {
                    Component msg = Component.literal("[" + companion.getCustomCompanionName() + "] ")
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal("Plan recibido: " + tasks.size() + " etapas programadas.").withStyle(ChatFormatting.YELLOW));
                    companion.getOwnerPlayer().sendSystemMessage(msg);
                }
            });
        }
    }

    private void processAndDispatchGoals(List<Goal> goals, String text) {
        if (goals == null || goals.isEmpty()) return;

        String lower = text != null ? text.toLowerCase().trim() : "";
        boolean isPlural = lower.startsWith("esclavos") || lower.startsWith("equipo") || lower.startsWith("todos")
                || lower.contains("construyan") || lower.contains("minen") || lower.contains("piquen")
                || lower.contains("tumben") || lower.contains("consigan") || lower.contains("junten");
        Set<UUID> ownerCompanions = com.aicompanion.ai.coordination.CompanionCoordinator.getCompanionsForOwner(companion.getOwnerUUID());

        if ((isPlural || ownerCompanions.size() > 1) && companion.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            List<Goal> distributedGoals = new ArrayList<>();
            if (goals.size() == 1 && ownerCompanions.size() > 1) {
                Goal g = goals.get(0);
                if (g.getAmount() > 1 && (g.getType() == Goal.Type.OBTAIN || g.getType() == Goal.Type.KILL)) {
                    int parts = Math.min(ownerCompanions.size(), g.getAmount());
                    int amountEach = g.getAmount() / parts;
                    int remainder = g.getAmount() % parts;
                    for (int p = 0; p < parts; p++) {
                        int amt = amountEach + (p == 0 ? remainder : 0);
                        if (g.getType() == Goal.Type.KILL) {
                            distributedGoals.add(Goal.kill(g.getTargetResource(), amt));
                        } else {
                            distributedGoals.add(Goal.obtain(g.getTargetResource(), amt, g.getStringParam()));
                        }
                    }
                } else {
                    distributedGoals.addAll(goals);
                }
            } else {
                distributedGoals.addAll(goals);
            }

            if (distributedGoals.size() > 1 || isPlural) {
                com.aicompanion.ai.coordination.CompanionCoordinator.SharedMission sm =
                        com.aicompanion.ai.coordination.CompanionCoordinator.createSharedMission(companion.getOwnerUUID(), text, distributedGoals, sl.getGameTime());
                if (sm != null) {
                    if (companion.getOwnerPlayer() != null) {
                        companion.getOwnerPlayer().sendSystemMessage(
                                Component.literal("[Equipo de Esclavos] ").withStyle(ChatFormatting.GOLD)
                                        .append(Component.literal("¡Misión cooperativa iniciada! (" + sm.getSubtasks().size() + " tareas distribuidas entre " + ownerCompanions.size() + " compañeros)").withStyle(ChatFormatting.GREEN))
                        );
                    }
                    com.aicompanion.ai.coordination.CompanionCoordinator.tickCooperativeMissions(sl);
                    return;
                }
            }
        }

        applyGoalsOnServerThread(goals);
    }

    private void applyGoalsOnServerThread(List<Goal> goals) {
        if (goals == null || goals.isEmpty()) return;
        synchronized (lastUserGoals) {
            lastUserGoals.clear();
            lastUserGoals.addAll(goals);
        }
        MinecraftServer server = companion.getServer();
        if (server != null) {
            server.execute(() -> {
                taskManager.stopWithoutDefault();
                List<CompanionTask> allTasks = new ArrayList<>();
                for (int i = 0; i < goals.size(); i++) {
                    Goal goal = goals.get(i);
                    boolean isLast = (i == goals.size() - 1);
                    List<CompanionTask> tasks = com.aicompanion.ai.planning.PrerequisitePlanner.resolvePlanWithPrerequisites(companion, goal);
                    for (CompanionTask t : tasks) {
                        // Skip intermediate ReturnTask so the bot smoothly transitions across chained sub-goals
                        if (!isLast && t instanceof ReturnTask) {
                            continue;
                        }
                        allTasks.add(t);
                    }
                }
                attachDelivery(goals, allTasks);
                if (!allTasks.isEmpty()) {
                    String singleMissionId = com.aicompanion.ai.coordination.CompanionCoordinator.nextMissionId();
                    for (CompanionTask t : allTasks) {
                        t.setMissionId(singleMissionId);
                    }
                    taskManager.setTask(allTasks.get(0));
                    for (int i = 1; i < allTasks.size(); i++) {
                        taskManager.getTaskQueue().enqueue(allTasks.get(i));
                    }
                    if (companion.getOwnerPlayer() != null) {
                        Component msg = Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                .withStyle(ChatFormatting.GOLD)
                                .append(Component.literal("Plan recibido: " + allTasks.size() + " etapas programadas.").withStyle(ChatFormatting.YELLOW));
                        companion.getOwnerPlayer().sendSystemMessage(msg);
                    }
                }
            });
        }
    }

    /**
     * If the order asks for delivery ("tráemelos", "dámelos"...) or the companion has
     * auto-delivery enabled, the final return-to-owner task becomes a real item delivery of
     * the resources gathered by the OBTAIN goals of this order (or the previous mission).
     */
    private void attachDelivery(List<Goal> goals, List<CompanionTask> allTasks) {
        boolean explicitDelivery = false;
        boolean keepOrDeposit = false;
        List<net.minecraft.resources.ResourceLocation> targets = new ArrayList<>();
        for (Goal g : goals) {
            if (g.isDelivery()) explicitDelivery = true;
            if (g.getType() == Goal.Type.DEPOSIT || g.getType() == Goal.Type.STAY) keepOrDeposit = true;
            if (g.getType() == Goal.Type.OBTAIN && g.getTargetResource() != null) {
                targets.add(g.getTargetResource());
            }
        }
        if (!targets.isEmpty()) {
            lastObtainTargets.clear();
            lastObtainTargets.addAll(targets);
        } else if (explicitDelivery) {
            targets.addAll(lastObtainTargets);
        }

        boolean autoDeliver = !keepOrDeposit && !targets.isEmpty()
                && companion.getSettings() != null && companion.getSettings().isAutoDeliver();
        if (keepOrDeposit) {
            allTasks.removeIf(t -> t instanceof ReturnTask rt && !rt.isReturnToHome());
            return;
        }
        if (!explicitDelivery && !autoDeliver) return;

        ReturnTask finalReturn = null;
        for (int i = allTasks.size() - 1; i >= 0; i--) {
            if (allTasks.get(i) instanceof ReturnTask rt && !rt.isReturnToHome()) {
                finalReturn = rt;
                break;
            }
        }
        if (finalReturn == null) {
            finalReturn = new ReturnTask(companion, false);
            allTasks.add(finalReturn);
        }
        finalReturn.setDelivery(targets);
    }

    public boolean resumeLastOrderIfAny() {
        synchronized (lastUserGoals) {
            if (!lastUserGoals.isEmpty()) {
                if (companion.getOwnerPlayer() != null) {
                    companion.getOwnerPlayer().sendSystemMessage(
                            Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                    .withStyle(ChatFormatting.GOLD)
                                    .append(Component.literal("Reanudando misión anterior: " + (!lastUserOrderText.isEmpty() ? lastUserOrderText : "tarea previa")).withStyle(ChatFormatting.GREEN))
                    );
                }
                applyGoalsOnServerThread(new ArrayList<>(lastUserGoals));
                return true;
            }
        }
        return false;
    }

    public boolean replanCurrentGoal() {
        synchronized (lastUserGoals) {
            if (!lastUserGoals.isEmpty()) {
                com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-GOAP] Replanning mission from active goals ({} goals): {}",
                        lastUserGoals.size(), lastUserOrderText);
                if (companion.getOwnerPlayer() != null) {
                    companion.getOwnerPlayer().sendSystemMessage(
                            Component.literal("[" + companion.getCustomCompanionName() + "] ")
                                    .withStyle(ChatFormatting.GOLD)
                                    .append(Component.literal("Replanificando misión con nuevos prerrequisitos: " + (!lastUserOrderText.isEmpty() ? lastUserOrderText : "misión activa")).withStyle(ChatFormatting.AQUA))
                    );
                }
                applyGoalsOnServerThread(new ArrayList<>(lastUserGoals));
                return true;
            }
        }
        return false;
    }



    public void handleSubmitOrder(int requestId, String rawOrder, com.aicompanion.ai.order.OrderSubmitMode submitMode, net.minecraft.server.level.ServerPlayer player) {
        if (rawOrder == null || rawOrder.trim().isEmpty()) {
            sendResult(requestId, player, com.aicompanion.ai.order.OrderResultStatus.REJECTED, "La orden está vacía.", 0, "");
            return;
        }

        if (companion.getCurrentState() == CompanionState.DEAD_WAITING_RESPAWN || companion.getCurrentState() == CompanionState.RESPAWNING) {
            sendResult(requestId, player, com.aicompanion.ai.order.OrderResultStatus.REJECTED,
                    "No puedo recibir órdenes mientras estoy esperando mi reaparición.", 0, "");
            return;
        }
        if (companion.getCurrentState() == CompanionState.DOWNED) {
            sendResult(requestId, player, com.aicompanion.ai.order.OrderResultStatus.REJECTED,
                    "No puedo moverme ni recibir órdenes mientras estoy derribado/herido.", 0, "");
            return;
        }

        // Interactive house dialogue (answers to the companion's questions)
        String folded = com.aicompanion.ai.parser.NormalizedOrderText.foldAccents(rawOrder).toLowerCase().trim();
        if (buildSession != null && buildSession.isExpired(companion.level().getGameTime())) {
            buildSession = null;
        }
        if (buildSession != null) {
            com.aicompanion.ai.building.BuildDialogSession.Reply reply = buildSession.handle(folded, player);
            if (reply.notUnderstood() && !com.aicompanion.ai.building.BuildOrderDialog.isHouseRequest(folded)
                    && !com.aicompanion.ai.parser.TaskParser.parse(rawOrder).isEmpty()) {
                // The player gave a different order: drop the pending house and execute it
                buildSession = null;
                companion.tellOwner("Dejo pendiente la casa y hago lo que me pides.");
            } else {
                deliverBuildReply(requestId, reply, submitMode, player);
                return;
            }
        } else if (interactiveBuildEnabled() && com.aicompanion.ai.building.BuildOrderDialog.isHouseRequest(folded)) {
            this.lastUserOrderText = rawOrder.trim();
            buildSession = new com.aicompanion.ai.building.BuildDialogSession(companion);
            deliverBuildReply(requestId, buildSession.start(folded, player), submitMode, player);
            return;
        }

        this.lastUserOrderText = rawOrder.trim();
        List<Goal> goals = com.aicompanion.ai.parser.TaskParser.parse(rawOrder);
        if (goals.isEmpty()) {
            String mode = AICompanionConfig.COMMON.aiMode.get();
            String prov = AICompanionConfig.COMMON.aiProvider.get();
            if ("HYBRID".equalsIgnoreCase(mode) || "LOCAL_LLM".equalsIgnoreCase(mode) || "LLM_ONLY".equalsIgnoreCase(mode)
                    || "OPENAI".equalsIgnoreCase(prov) || "OLLAMA".equalsIgnoreCase(prov)) {
                provider.plan(companion, rawOrder).thenAccept(llmGoals -> {
                    if (llmGoals == null || llmGoals.isEmpty()) {
                        sendResult(requestId, player, com.aicompanion.ai.order.OrderResultStatus.REJECTED, "No pude convertir esta orden en una tarea.", 0, "");
                    } else {
                        applyGoalsWithMode(requestId, llmGoals, submitMode, player);
                    }
                });
                return;
            } else {
                sendResult(requestId, player, com.aicompanion.ai.order.OrderResultStatus.REJECTED, "No se reconoció ninguna acción válida en la orden.", 0, "");
                return;
            }
        }

        applyGoalsWithMode(requestId, goals, submitMode, player);
    }

    private void applyGoalsWithMode(int requestId, List<Goal> goals, com.aicompanion.ai.order.OrderSubmitMode mode, net.minecraft.server.level.ServerPlayer player) {
        if (goals != null && !goals.isEmpty()) {
            synchronized (lastUserGoals) {
                lastUserGoals.clear();
                lastUserGoals.addAll(goals);
            }
        }
        MinecraftServer server = companion.getServer();
        if (server != null) {
            server.execute(() -> {
                List<CompanionTask> allTasks = new ArrayList<>();
                StringBuilder summary = new StringBuilder();

                for (int i = 0; i < goals.size(); i++) {
                    Goal goal = goals.get(i);
                    boolean isLast = (i == goals.size() - 1);
                    List<CompanionTask> tasks = com.aicompanion.ai.planning.PrerequisitePlanner.resolvePlanWithPrerequisites(companion, goal);
                    for (CompanionTask t : tasks) {
                        if (!isLast && t instanceof ReturnTask) continue;
                        allTasks.add(t);
                    }
                    if (i > 0) summary.append(", ");
                    summary.append(com.aicompanion.ai.parser.TaskParser.formatGoalDescription(goal));
                }
                attachDelivery(goals, allTasks);

                if (allTasks.isEmpty()) {
                    sendResult(requestId, player, com.aicompanion.ai.order.OrderResultStatus.REJECTED, "No se pudieron generar tareas ejecutables.", 0, "");
                    return;
                }

                if (mode == com.aicompanion.ai.order.OrderSubmitMode.APPEND_QUEUE && taskManager.getCurrentTask() != null) {
                    for (CompanionTask t : allTasks) {
                        taskManager.getTaskQueue().enqueue(t);
                    }
                } else {
                    taskManager.stopWithoutDefault();
                    taskManager.setTask(allTasks.get(0));
                    for (int i = 1; i < allTasks.size(); i++) {
                        taskManager.getTaskQueue().enqueue(allTasks.get(i));
                    }
                }

                String goalSummaryStr = summary.toString();
                sendResult(requestId, player, com.aicompanion.ai.order.OrderResultStatus.ACCEPTED, "Orden aceptada (" + allTasks.size() + " etapas)", allTasks.size(), goalSummaryStr);

                if (player != null) {
                    Component msg = Component.literal("[" + companion.getCustomCompanionName() + "] ")
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal("Plan aceptado: " + allTasks.size() + " etapas programadas.").withStyle(ChatFormatting.GREEN));
                    player.sendSystemMessage(msg);
                }
            });
        }
    }

    public void handleOrderPreviewRequest(int requestId, String rawOrder, net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return;
        List<com.aicompanion.ai.order.PlanPreviewRow> rows = new ArrayList<>();
        if (companion.getCurrentState() == CompanionState.DEAD_WAITING_RESPAWN || companion.getCurrentState() == CompanionState.RESPAWNING) {
            com.aicompanion.network.CompanionNetworking.CHANNEL.sendTo(
                    new com.aicompanion.network.packets.S2C_OrderPreviewPacket(
                            requestId,
                            com.aicompanion.ai.parser.NormalizedOrderText.foldAccents(rawOrder),
                            "Incapacitado",
                            "",
                            rows,
                            false,
                            "El compañero está esperando su reaparición."
                    ),
                    player.connection.connection,
                    net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT
            );
            return;
        }
        if (companion.getCurrentState() == CompanionState.DOWNED) {
            com.aicompanion.network.CompanionNetworking.CHANNEL.sendTo(
                    new com.aicompanion.network.packets.S2C_OrderPreviewPacket(
                            requestId,
                            com.aicompanion.ai.parser.NormalizedOrderText.foldAccents(rawOrder),
                            "Incapacitado",
                            "",
                            rows,
                            false,
                            "El compañero está derribado e incapacitado."
                    ),
                    player.connection.connection,
                    net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT
            );
            return;
        }

        List<Goal> goals = com.aicompanion.ai.parser.TaskParser.parse(rawOrder);
        boolean isValid = !goals.isEmpty();
        String validMsg = isValid ? "Orden válida" : "Escribe una orden completa";
        String goalSummary = "";

        if (isValid) {
            StringBuilder sb = new StringBuilder();
            int stepNum = 1;
            for (int i = 0; i < goals.size(); i++) {
                Goal g = goals.get(i);
                if (i > 0) sb.append(", ");
                sb.append(com.aicompanion.ai.parser.TaskParser.formatGoalDescription(g));

                rows.add(new com.aicompanion.ai.order.PlanPreviewRow(
                        stepNum++ + ". " + com.aicompanion.ai.parser.TaskParser.formatGoalDescription(g),
                        com.aicompanion.ai.order.PlanPreviewRow.PlanRowType.GOAL,
                        null,
                        "LISTO"
                ));

                if (g.getType() == Goal.Type.OBTAIN && g.getTargetResource() != null) {
                    com.aicompanion.ai.mining.MiningTarget target = com.aicompanion.ai.mining.MiningTarget.of(g.getTargetResource().toString());
                    int reqTier = com.aicompanion.ai.planning.RecipeDependencyGraph.getRequiredPickaxeTier(target);
                    int ownedTier = com.aicompanion.ai.planning.RecipeDependencyGraph.getHighestOwnedPickaxeTier(companion);
                    if (reqTier > com.aicompanion.ai.planning.RecipeDependencyGraph.TIER_HAND && ownedTier < reqTier) {
                        rows.add(new com.aicompanion.ai.order.PlanPreviewRow(
                                "  ↳ Prerrequisito: Fabricar pico de nivel requerido",
                                com.aicompanion.ai.order.PlanPreviewRow.PlanRowType.REQUIREMENT,
                                null,
                                "PREPARACIÓN"
                        ));
                    }
                }
            }
            goalSummary = sb.toString();
        }

        com.aicompanion.network.CompanionNetworking.CHANNEL.sendTo(
                new com.aicompanion.network.packets.S2C_OrderPreviewPacket(
                        requestId,
                        com.aicompanion.ai.parser.NormalizedOrderText.foldAccents(rawOrder),
                        isValid ? "Intención identificada" : "Esperando orden...",
                        goalSummary,
                        rows,
                        isValid,
                        validMsg
                ),
                player.connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT
        );
    }

    private void sendResult(int requestId, net.minecraft.server.level.ServerPlayer player, com.aicompanion.ai.order.OrderResultStatus status, String message, int taskCount, String summary) {
        if (player != null) {
            com.aicompanion.network.CompanionNetworking.CHANNEL.sendTo(
                    new com.aicompanion.network.packets.S2C_OrderResultPacket(requestId, companion.getUUID(), status, message, taskCount, summary),
                    player.connection.connection,
                    net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT
            );
        }
    }

    public void replanCurrentTask() {
        if (taskManager.getCurrentTask() != null && replanManager.canReplan()) {
            replanManager.incrementReplan();
            taskManager.getCurrentTask().start();
        }
    }

    public TaskManager getTaskManager() { return taskManager; }
    public StuckDetector getStuckDetector() { return stuckDetector; }
    public CompanionMemory getMemory() { return memory; }
    public CompanionPerception getPerception() { return perception; }
    public AIProvider getProvider() { return provider; }

    public void save(CompoundTag tag) {
        tag.put("Memory", memory.save());
        tag.putString("LastOrderText", lastUserOrderText != null ? lastUserOrderText : "");
        tag.putString("ActiveMissionId", getActiveMissionId());
        net.minecraft.nbt.ListTag goalsTag = new net.minecraft.nbt.ListTag();
        synchronized (lastUserGoals) {
            for (Goal g : lastUserGoals) {
                if (g != null) {
                    goalsTag.add(g.save());
                }
            }
        }
        tag.put("LastUserGoals", goalsTag);
        if (activeBuildProject != null) {
            tag.put("ActiveBuildProject", activeBuildProject.save());
        }
        tag.put("TaskManager", taskManager.save());
    }

    public void load(CompoundTag tag) {
        if (tag.contains("Memory")) memory.load(tag.getCompound("Memory"));
        if (tag.contains("LastOrderText")) this.lastUserOrderText = tag.getString("LastOrderText");
        if (tag.contains("ActiveMissionId")) this.activeMissionId = tag.getString("ActiveMissionId");
        if (tag.contains("LastUserGoals")) {
            net.minecraft.nbt.ListTag list = tag.getList("LastUserGoals", net.minecraft.nbt.Tag.TAG_COMPOUND);
            synchronized (lastUserGoals) {
                lastUserGoals.clear();
                for (int i = 0; i < list.size(); i++) {
                    Goal g = Goal.load(list.getCompound(i));
                    if (g != null) lastUserGoals.add(g);
                }
            }
        }
        if (tag.contains("ActiveBuildProject")) {
            this.activeBuildProject = com.aicompanion.ai.building.BuildProject.load(tag.getCompound("ActiveBuildProject"));
        }
        if (tag.contains("TaskManager")) {
            taskManager.load(tag.getCompound("TaskManager"));
        }
    }
}
