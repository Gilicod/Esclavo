package com.aicompanion.ai.tasks;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.ai.planning.PrerequisitePlanner;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import com.aicompanion.inventory.EquipmentManager;
import com.aicompanion.ai.nav.MovementPolicy;
import com.aicompanion.internal.baritone.goals.GoalEntity;
import com.aicompanion.internal.baritone.goals.GoalExploreRegion;
import com.aicompanion.internal.baritone.goals.GoalNear;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import com.aicompanion.inventory.InventoryRequirementTracker;
import net.minecraft.core.BlockPos;
import java.util.List;

public class KillTask extends CompanionTask {
    private ResourceLocation targetEntityId;
    private ResourceLocation targetItemId = null;
    private int amountNeeded;
    private int amountKilled = 0;
    private int startInventoryCount = -1;
    private LivingEntity currentTarget = null;
    private BlockPos lastKillPos = null;
    private final MissionDropTracker dropTracker = new MissionDropTracker();
    private int attackCooldown = 0;
    private int navRepathCooldown = 0;
    private int searchTicks = 0;

    // Systematic terrain exploration
    private BlockPos exploreOrigin = null;
    private double exploreAngle = 0.0;
    private double exploreRadius = 35.0;
    private int exploreTicks = 0;
    private int fullSweeps = 0;
    private boolean notifiedNoTarget = false;
    private BlockPos currentExploreWaypoint = null;

    public KillTask(CompanionEntity companion, ResourceLocation targetEntityId, int amountNeeded) {
        this(companion, targetEntityId, amountNeeded, null);
    }

    public KillTask(CompanionEntity companion, ResourceLocation targetEntityId, int amountNeeded, ResourceLocation targetItemId) {
        super(companion);
        this.targetEntityId = targetEntityId;
        this.targetItemId = targetItemId;
        this.amountNeeded = amountNeeded;
        this.stage = "SEARCHING";
        this.substate = targetItemId != null
                ? "Cazando " + getTargetDisplayName().toLowerCase() + " para conseguir " + (targetItemId.getPath().equals("porkchop") ? "tocino" : targetItemId.getPath())
                : "Rastreando " + getTargetDisplayName().toLowerCase();
    }

    @Override
    public List<CompanionTask> checkPreconditions() {
        if (!EquipmentManager.hasWeaponEquipped(companion) && EquipmentManager.findBestWeaponSlot(companion) == -1) {
            logEvent("Preparando arma para combate...");
            return PrerequisitePlanner.resolveWeaponPrerequisites(companion);
        }
        return java.util.Collections.emptyList();
    }

    @Override
    public void start() {
        companion.setCurrentState(CompanionState.SEARCHING);
        dropTracker.reset();
        if (targetItemId != null && startInventoryCount < 0) {
            startInventoryCount = InventoryRequirementTracker.countResource(companion, targetItemId);
        }
        logEvent("Iniciando: Cazar " + amountNeeded + " " + getTargetDisplayName());
        markProgress();
    }

    @Override
    public void tick() {
        if (paused) return;

        // 1. Physical drop collection phase MUST run before task completion!
        if (dropTracker.isBusyCollecting()) {
            stage = "COLLECTING_DROPS";
            substate = "Recogiendo botín del suelo...";
            companion.setCurrentState(CompanionState.NAVIGATING);
            DropCollectionResult res = dropTracker.tickCollection(companion, lastKillPos,
                    stack -> targetItemId == null || stack.is(BuiltInRegistries.ITEM.get(targetItemId)));
            if (res == DropCollectionResult.PICKED_UP || res == DropCollectionResult.COMPLETE) {
                logEvent("Botín recogido del suelo");
            }
            if (res != DropCollectionResult.COMPLETE && res != DropCollectionResult.NO_DROPS_FOUND && res != DropCollectionResult.DROP_UNREACHABLE) {
                return;
            }
        }

        // Check for remaining drops near last kill pos before concluding
        if (lastKillPos != null) {
            var nearbyDrops = dropTracker.scanDrops(companion, lastKillPos,
                    stack -> targetItemId == null || stack.is(BuiltInRegistries.ITEM.get(targetItemId)));
            if (!nearbyDrops.isEmpty()) {
                stage = "COLLECTING_DROPS";
                substate = "Recogiendo botín del suelo...";
                companion.setCurrentState(CompanionState.NAVIGATING);
                DropCollectionResult res = dropTracker.tickCollection(companion, lastKillPos,
                        stack -> targetItemId == null || stack.is(BuiltInRegistries.ITEM.get(targetItemId)));
                if (res == DropCollectionResult.PICKED_UP || res == DropCollectionResult.COMPLETE) {
                    logEvent("Botín recogido del suelo");
                }
                return;
            } else {
                lastKillPos = null;
            }
        }

        // 2. Completion condition: evaluated only AFTER drops are collected or none remain
        if (targetItemId != null) {
            int currentCount = InventoryRequirementTracker.countResource(companion, targetItemId);
            if (startInventoryCount < 0) {
                startInventoryCount = currentCount;
            }
            int gathered = Math.max(0, currentCount - startInventoryCount);
            if (gathered >= amountNeeded) {
                complete = true;
                stage = "COMPLETE";
                substate = "Botín conseguido con éxito (" + gathered + "/" + amountNeeded + ")";
                logEvent("Misión completada: Botín conseguido (" + gathered + "/" + amountNeeded + " de " + targetItemId.getPath() + ")");
                companion.getNavManager().stop();
                companion.addCombatXP(amountNeeded * 10);
                companion.incrementTasksCompleted();
                if (companion.getOwnerPlayer() != null) {
                    companion.getOwnerPlayer().sendSystemMessage(
                            Component.literal("[" + companion.getCustomCompanionName() + "] ").withStyle(ChatFormatting.GOLD)
                                    .append(Component.literal("¡Objetivo conseguido (" + gathered + "/" + amountNeeded + " de " + targetItemId.getPath() + ")!").withStyle(ChatFormatting.GREEN))
                    );
                }
                return;
            }
        } else if (amountKilled >= amountNeeded) {
            complete = true;
            stage = "COMPLETE";
            substate = "Misión de combate completada";
            logEvent("Misión completada: Caza finalizada (" + amountKilled + "/" + amountNeeded + " " + getTargetDisplayName() + ")");
            companion.getNavManager().stop();
            companion.addCombatXP(amountNeeded * 10);
            companion.incrementMobsKilled(amountNeeded);
            companion.incrementTasksCompleted();
            if (companion.getOwnerPlayer() != null) {
                companion.getOwnerPlayer().sendSystemMessage(
                        Component.literal("[" + companion.getCustomCompanionName() + "] ").withStyle(ChatFormatting.GOLD)
                                .append(Component.literal("¡Misión de combate completada! (" + amountKilled + "/" + amountNeeded + " " + getTargetDisplayName().toLowerCase() + ")").withStyle(ChatFormatting.GREEN))
                );
            }
            return;
        }

        // 3. Auto-equip best weapon
        int bestWeaponSlot = EquipmentManager.findBestWeaponSlot(companion);
        if (bestWeaponSlot != -1 && bestWeaponSlot != com.aicompanion.inventory.CompanionInventoryHandler.SLOT_MAINHAND) {
            EquipmentManager.swapMainHandSafely(companion, bestWeaponSlot);
            logEvent("Espada equipada en mano principal: " + companion.getMainHandItem().getHoverName().getString());
        } else if (!EquipmentManager.hasWeaponEquipped(companion)) {
            int slot = EquipmentManager.findBestWeaponSlot(companion);
            if (slot != -1) {
                EquipmentManager.swapMainHandSafely(companion, slot);
                logEvent("Espada equipada en mano principal: " + companion.getMainHandItem().getHoverName().getString());
            }
        }

        // 4. Target tracking & combat
        if (currentTarget == null || !currentTarget.isAlive() || currentTarget.isRemoved()) {
            if (currentTarget != null) {
                com.aicompanion.ai.coordination.CompanionCoordinator.releaseEntity(companion.getUUID(), currentTarget.getUUID());
                if (!currentTarget.isAlive()) {
                    amountKilled++;
                    lastKillPos = currentTarget.blockPosition();
                    dropTracker.startGracePeriod(20);
                    markProgress();
                    logEvent("Objetivo eliminado en " + lastKillPos.toShortString() + ". Esperando y rastreando botín...");
                    if (companion.getOwnerPlayer() != null) {
                        String progressMsg = targetItemId != null
                                ? "Enemigo eliminado. Recogiendo botín..."
                                : "Objetivo eliminado: " + amountKilled + "/" + amountNeeded;
                        companion.getOwnerPlayer().sendSystemMessage(
                                Component.literal("[" + companion.getCustomCompanionName() + "] ").withStyle(ChatFormatting.RED)
                                        .append(Component.literal(progressMsg).withStyle(ChatFormatting.WHITE))
                        );
                    }
                }
            }
            currentTarget = findTarget();
            if (currentTarget != null) {
                com.aicompanion.ai.coordination.CompanionCoordinator.claimEntity(companion.getUUID(), currentTarget.getUUID(), companion.level().getGameTime());
                logEvent("Objetivo encontrado en " + currentTarget.blockPosition().toShortString() + ". Iniciando aproximación.");
            } else {
                stage = "SEARCHING";
                substate = "Rastreando " + getTargetDisplayName().toLowerCase() + "...";
                companion.setCurrentState(CompanionState.SEARCHING);
                searchTicks++;

                net.minecraft.world.entity.player.Player owner = companion.getOwnerPlayer();
                if (exploreOrigin == null || companion.distanceToSqr(exploreOrigin.getX(), exploreOrigin.getY(), exploreOrigin.getZ()) > 20000.0) {
                    exploreOrigin = companion.blockPosition();
                    exploreAngle = companion.getYRot();
                    exploreRadius = 35.0;
                    exploreTicks = 0;
                    fullSweeps = 0;
                    currentExploreWaypoint = null;
                }

                // If finished sweep around this origin and found nothing, regroup near owner if owner is far
                if (fullSweeps >= 2) {
                    complete = true;
                    stage = "FAILED";
                    substate = "No se encontraron " + getTargetDisplayName().toLowerCase();
                    companion.getNavManager().stop();
                    if (owner != null && owner.isAlive()) {
                        owner.sendSystemMessage(
                            Component.literal("[" + companion.getCustomCompanionName() + "] ").withStyle(ChatFormatting.GOLD)
                                .append(Component.literal("No pude encontrar ningún " + getTargetDisplayName().toLowerCase() + " después de buscar exhaustivamente por la zona.").withStyle(ChatFormatting.RED))
                        );
                    }
                    return;
                }

                if (fullSweeps >= 1 && owner != null && owner.isAlive()) {
                    double distOwner = companion.distanceTo(owner);
                    if (distOwner > 18.0) {
                        substate = "Regrupando con dueño para explorar nueva zona...";
                        if (!notifiedNoTarget) {
                            notifiedNoTarget = true;
                            owner.sendSystemMessage(
                                Component.literal("[" + companion.getCustomCompanionName() + "] ").withStyle(ChatFormatting.GOLD)
                                    .append(Component.literal("No encontré ningún " + getTargetDisplayName().toLowerCase() + " en este sector (110m). Me acerco a ti para explorar otra zona.").withStyle(ChatFormatting.YELLOW))
                            );
                        }
                        if (companion.getNavManager().isDone() || exploreTicks % 40 == 0) {
                            companion.getNavManager().navigateTo(new GoalNear(owner.blockPosition(), 8.0), MovementPolicy.sprint());
                        }
                        exploreTicks++;
                        return;
                    } else {
                        // Reached near owner: re-center exploration origin on owner's new position!
                        exploreOrigin = owner.blockPosition();
                        exploreAngle = owner.getYRot();
                        exploreRadius = 35.0;
                        exploreTicks = 0;
                        fullSweeps = 0;
                        notifiedNoTarget = false;
                        currentExploreWaypoint = null;
                    }
                }

                exploreTicks++;
                boolean needWaypoint = currentExploreWaypoint == null
                        || companion.getNavManager().isDone()
                        || exploreTicks > 240
                        || companion.blockPosition().distSqr(currentExploreWaypoint) < 16.0;

                if (needWaypoint) {
                    exploreTicks = 0;
                    double rad = Math.toRadians(exploreAngle);
                    int targetX = exploreOrigin.getX() + (int) (Math.cos(rad) * exploreRadius);
                    int targetZ = exploreOrigin.getZ() + (int) (Math.sin(rad) * exploreRadius);
                    int targetY = companion.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ);
                    currentExploreWaypoint = new BlockPos(targetX, targetY, targetZ);

                    exploreAngle = (exploreAngle + 70.0) % 360.0;
                    if (exploreAngle < 70.0) {
                        if (exploreRadius >= 100.0) {
                            fullSweeps++;
                        }
                        exploreRadius = Math.min(110.0, exploreRadius + 25.0);
                    }

                    com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-HUNT] Exploring waypoint at {} (radius={}, angle={}, sweep={})", currentExploreWaypoint, (int) exploreRadius, (int) exploreAngle, fullSweeps);
                    companion.getNavManager().navigateTo(new GoalNear(currentExploreWaypoint, 3.5), MovementPolicy.sprint());
                }
                return;
            }
        }

        exploreOrigin = null;
        currentExploreWaypoint = null;
        exploreTicks = 0;
        searchTicks = 0;
        fullSweeps = 0;
        notifiedNoTarget = false;
        stage = "FIGHTING";
        substate = "Combatiendo " + currentTarget.getName().getString();
        companion.setCurrentState(CompanionState.FIGHTING);
        companion.getLookControl().setLookAt(currentTarget, 60.0F, 60.0F);

        // Creeper safety: back away if swelling!
        if (currentTarget instanceof Creeper creeper && creeper.getSwellDir() > 0) {
            companion.getNavManager().moveTo(companion.blockPosition().offset(
                    (int) -(creeper.getX() - companion.getX()),
                    0,
                    (int) -(creeper.getZ() - companion.getZ())
            ), MovementPolicy.sprint());
            return;
        }

        double distSq = companion.distanceToSqr(currentTarget);

        // Immediate melee attack: reach in Minecraft is ~3.25m (distSq <= 10.56)
        if (distSq <= 10.5 && companion.hasLineOfSight(currentTarget)) {
            if (attackCooldown <= 0) {
                companion.swing(InteractionHand.MAIN_HAND);
                companion.doHurtTarget(currentTarget);
                ItemStack weapon = companion.getMainHandItem();
                if (weapon.isDamageableItem()) {
                    weapon.hurtAndBreak(1, companion, e -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));
                    EquipmentManager.checkAndPreserveEndangeredTools(companion);
                }
                attackCooldown = 12; // Vanilla 1.20.1 weapon cadence (~0.6s)
                markProgress();
                logEvent("Combatiendo objetivo (distancia=" + String.format(java.util.Locale.ROOT, "%.1f", Math.sqrt(distSq)) + ", cooldown=" + attackCooldown + ")");
            }
        }

        // Fluid pursuit without freezing: sprint toward target until within close striking range
        if (distSq > 3.24) {
            if (--navRepathCooldown <= 0 || companion.getNavManager().isDone()) {
                companion.getNavManager().navigateTo(new GoalEntity(currentTarget, 0.8, 2.0), MovementPolicy.sprint());
                navRepathCooldown = 8;
            }
        } else if (distSq > 1.5) {
            // Within striking distance, maintain orientation and close tracking
            if (companion.getNavManager().isDone() || --navRepathCooldown <= 0) {
                companion.getNavManager().navigateTo(new GoalEntity(currentTarget, 0.8, 1.8), MovementPolicy.sprint());
                navRepathCooldown = 15;
            }
        } else {
            // Very close (<1.2m), stop pushing against target
            companion.getNavManager().stop();
        }

        if (attackCooldown > 0) attackCooldown--;
    }

    private LivingEntity findTarget() {
        AABB area = companion.getBoundingBox().inflate(64.0, 24.0, 64.0);
        List<LivingEntity> list = companion.level().getEntitiesOfClass(LivingEntity.class, area);
        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (LivingEntity e : list) {
            if (!e.isAlive() || e == companion || e == companion.getOwnerPlayer()) continue;
            if (com.aicompanion.ai.coordination.CompanionCoordinator.isEntityClaimedByOther(companion.getUUID(), e.getUUID(), companion.level().getGameTime())) continue;

            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
            if (entityId.equals(targetEntityId) || (targetEntityId.getPath().equals("monster") && !e.getType().getCategory().isFriendly())) {
                double dist = companion.distanceToSqr(e);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = e;
                }
            }
        }
        return closest;
    }

    @Override
    public void stop() {
        if (currentTarget != null && companion != null) {
            com.aicompanion.ai.coordination.CompanionCoordinator.releaseEntity(companion.getUUID(), currentTarget.getUUID());
        }
        if (companion != null) {
            companion.setSprinting(false);
            companion.getNavManager().stop();
        }
    }

    public String getTargetDisplayName() {
        if (targetEntityId == null) return "objetivo";
        String path = targetEntityId.getPath().toLowerCase();
        return switch (path) {
            case "pig" -> "Cerdo";
            case "cow" -> "Vaca";
            case "sheep" -> "Oveja";
            case "chicken" -> "Pollo";
            case "zombie" -> "Zombi";
            case "skeleton" -> "Esqueleto";
            case "spider" -> "Araña";
            case "creeper" -> "Creeper";
            case "enderman" -> "Enderman";
            case "witch" -> "Bruja";
            case "squid" -> "Calamar";
            case "glow_squid" -> "Calamar brillante";
            default -> Character.toUpperCase(path.charAt(0)) + path.substring(1);
        };
    }

    @Override
    public String getDescription() { return "Cazar " + amountNeeded + "x " + getTargetDisplayName(); }
    @Override
    public String getProgress() { return amountKilled + "/" + amountNeeded + " (" + stage + ")"; }
    @Override
    public String getTaskType() { return "KILL"; }
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("TargetEntity", targetEntityId.toString());
        if (targetItemId != null) tag.putString("TargetItem", targetItemId.toString());
        tag.putInt("AmountNeeded", amountNeeded);
        tag.putInt("AmountKilled", amountKilled);
        tag.putInt("StartInventoryCount", startInventoryCount);
        return tag;
    }
    @Override
    public void load(CompoundTag tag) {
        this.targetEntityId = new ResourceLocation(tag.getString("TargetEntity"));
        if (tag.contains("TargetItem")) this.targetItemId = new ResourceLocation(tag.getString("TargetItem"));
        this.amountNeeded = tag.getInt("AmountNeeded");
        this.amountKilled = tag.getInt("AmountKilled");
        if (tag.contains("StartInventoryCount")) this.startInventoryCount = tag.getInt("StartInventoryCount");
    }
}
