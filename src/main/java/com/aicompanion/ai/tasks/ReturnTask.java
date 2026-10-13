package com.aicompanion.ai.tasks;

import com.aicompanion.ai.CompanionTask;
import com.aicompanion.ai.mining.MiningTarget;
import com.aicompanion.ai.nav.MovementPolicy;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import com.aicompanion.inventory.CompanionInventoryHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReturnTask extends CompanionTask {
    private boolean returnToHome;
    private int repathCooldown = 0;
    private int returnTicks = 0;
    private int stuckTicks = 0;
    private double lastDistSq = Double.MAX_VALUE;
    private BlockPos lastPos = null;
    private int portalBuildAttempts = 0;

    /** Resources gathered by the mission that must be handed to the owner on arrival. */
    private final List<ResourceLocation> deliveryTargets = new ArrayList<>();
    private boolean deliver = false;
    private boolean delivered = false;

    public ReturnTask(CompanionEntity companion, boolean returnToHome) {
        super(companion);
        this.returnToHome = returnToHome;
        this.stage = "RETURNING";
        this.substate = returnToHome ? "Viajando a la base" : "Regresando con el propietario";
    }

    /** Marks this return as a delivery of the given mission resources. */
    public void setDelivery(List<ResourceLocation> targets) {
        this.deliver = true;
        this.deliveryTargets.clear();
        if (targets != null) {
            for (ResourceLocation rl : targets) {
                if (rl != null && !deliveryTargets.contains(rl)) deliveryTargets.add(rl);
            }
        }
    }

    private final Map<ResourceLocation, Integer> deliveredItemsMap = new HashMap<>();

    public int getDeliveredCount(ResourceLocation itemId) {
        if (itemId == null) return 0;
        int sum = deliveredItemsMap.getOrDefault(itemId, 0);
        if (itemId.getPath().endsWith("_log") || itemId.getPath().equals("log")) {
            for (Map.Entry<ResourceLocation, Integer> entry : deliveredItemsMap.entrySet()) {
                if (entry.getKey().getPath().endsWith("_log")) sum += entry.getValue();
            }
        }
        return sum;
    }

    public boolean isDelivery() { return deliver; }
    public boolean isReturnToHome() { return returnToHome; }

    @Override
    public void start() {
        companion.setCurrentState(CompanionState.RETURNING);
        markProgress();
        this.repathCooldown = 0;
        this.portalSearchCooldown = 0;
        if (companion.level() instanceof net.minecraft.server.level.ServerLevel) {
            com.aicompanion.ai.chunk.CompanionChunkManager.updateChunkTickets(companion);
        }
    }

    @Override
    public void tick() {
        if (paused) return;

        if (returnToHome) {
            companion.setCurrentState(CompanionState.RETURNING);
            BlockPos home = companion.getHomePos();
            if (home != null) {
                if (--repathCooldown <= 0) {
                    repathCooldown = 15;
                    companion.getNavManager().moveTo(home, MovementPolicy.walk());
                }
                double homeDistSq = companion.blockPosition().distSqr(home);
                if (homeDistSq < 6.25 || (companion.getNavManager().isDone() && homeDistSq < 16.0)) {
                    complete = true;
                    companion.getNavManager().stop();
                    companion.setCurrentState(CompanionState.IDLE);
                }
            } else {
                complete = true;
            }
        } else {
            Player owner = companion.getOwnerPlayer();
            if (owner != null && owner.isAlive() && !owner.isDeadOrDying()) {
                // If in different dimensions, travel through return portal back to owner's dimension
                if (!companion.level().dimension().equals(owner.level().dimension())) {
                    tickCrossDimensionReturn(owner);
                    return;
                }

                companion.setCurrentState(CompanionState.RETURNING);
                this.substate = deliver ? "Llevando los recursos al propietario" : "Regresando con el propietario a pie";
                companion.getLookControl().setLookAt(owner, 10.0F, (float) companion.getMaxHeadXRot());
                double distSq = companion.distanceToSqr(owner);

                returnTicks++;
                if (returnTicks % 20 == 0) {
                    BlockPos currentPos = companion.blockPosition();
                    boolean moved = lastPos != null && currentPos.distSqr(lastPos) >= 4.0;
                    boolean gotCloser = distSq < lastDistSq - 1.0;

                    if (moved || gotCloser) {
                        stuckTicks = 0;
                        if (gotCloser) {
                            lastDistSq = distSq;
                        }
                    } else {
                        stuckTicks += 20;
                    }
                    lastPos = currentPos;
                }

                // Stuck watchdog & catch-up logic:
                // Strictly on foot. Only rescue if physically stuck/immobile with zero progress for 30 consecutive seconds
                boolean shouldRescue = (stuckTicks >= 20 * 30);

                if (shouldRescue && companion.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    com.aicompanion.util.SafeSpawnFinder.SafeSpawnResult res =
                            com.aicompanion.util.SafeSpawnFinder.findSafeSpawnNearEntity(sl, owner, companion);
                    if (res.isSuccess()) {
                        net.minecraft.world.phys.Vec3 safe = res.getPosition();
                        companion.teleportTo(safe.x, safe.y, safe.z);
                        com.aicompanion.util.SafeSpawnFinder.faceEntity(companion, owner);
                        companion.getNavManager().stop();
                        companion.setSprinting(false);
                        complete = true;
                        companion.setCurrentState(CompanionState.IDLE);
                        if (deliver && !delivered && owner instanceof ServerPlayer sp) {
                            delivered = true;
                            deliverTo(sp);
                        } else {
                            companion.tellOwner("¡Estaba atascado en el camino y no podía avanzar a pie! Me he reunido contigo.");
                        }
                        return;
                    }
                }

                // Physical walking navigation
                if (--repathCooldown <= 0) {
                    repathCooldown = 15;
                    double speed = MovementPolicy.sprint();
                    companion.setSprinting(MovementPolicy.sprintingAllowed());
                    companion.getNavManager().moveTo(owner, speed);
                }

                double arriveDist = deliver ? Math.max(1.5, MovementPolicy.deliveryDistance()) : 3.0;
                if (distSq <= arriveDist * arriveDist) {
                    complete = true;
                    companion.setSprinting(false);
                    companion.getNavManager().stop();
                    companion.setCurrentState(CompanionState.IDLE);
                    if (deliver && !delivered && owner instanceof ServerPlayer sp) {
                        delivered = true;
                        deliverTo(sp);
                    } else {
                        companion.tellOwner("¡He regresado contigo!");
                    }
                }
            } else {
                // Owner is dead, dying, or currently respawning:
                // DO NOT abort/complete the mission! Pause and wait patiently until player respawns.
                companion.getNavManager().stop();
                companion.setCurrentState(CompanionState.WAITING);
                this.substate = "Esperando que el propietario reaparezca...";
            }
        }
    }

    private BlockPos returnPortalPos = null;
    private int portalWaitTicks = 0;
    private int portalSearchCooldown = 0;

    private void tickCrossDimensionReturn(Player owner) {
        companion.setCurrentState(CompanionState.TRAVELLING_DIMENSION);
        if (!(companion.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;

        if (returnPortalPos == null || --portalSearchCooldown <= 0) {
            portalSearchCooldown = 25;
            returnPortalPos = com.aicompanion.ai.dimension.PortalHelper.findPortal(companion, owner.level().dimension());
        }

        if (returnPortalPos == null) {
            if (sl.dimension().equals(net.minecraft.world.level.Level.NETHER)) {
                portalBuildAttempts++;
                if (portalBuildAttempts <= 2 && companion.getCompanionBrain() != null) {
                    com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-RETURN] No se encontró portal de retorno activo en Nether. Planificando construcción de portal.");
                    companion.tellOwner("No encuentro un portal de regreso cercano. Voy a construir uno para regresar.");
                    companion.getCompanionBrain().getTaskManager().pushSubtasks(java.util.List.of(new BuildNetherPortalTask(companion)));
                    return;
                }
            }

            companion.getNavManager().stop();
            companion.setCurrentState(CompanionState.WAITING);
            this.substate = "Buscando portal para regresar a la dimensión del propietario...";
            return;
        }

        this.substate = "Caminando al portal para regresar con el propietario";
        boolean inside = com.aicompanion.ai.dimension.PortalHelper.isEntityInPortal(companion);

        if (!inside) {
            portalWaitTicks = Math.max(0, portalWaitTicks - 1);
            returnTicks++;
            if (returnTicks % 20 == 0) {
                BlockPos currentPos = companion.blockPosition();
                double distToPortalSq = currentPos.distSqr(returnPortalPos);
                boolean moved = lastPos != null && currentPos.distSqr(lastPos) >= 4.0;
                boolean gotCloser = distToPortalSq < lastDistSq - 1.0;
                if (moved || gotCloser) {
                    stuckTicks = 0;
                    if (gotCloser) lastDistSq = distToPortalSq;
                } else {
                    stuckTicks += 20;
                }
                lastPos = currentPos;
            }

            if (stuckTicks >= 20 * 20) {
                stuckTicks = 0;
                repathCooldown = 0;
                com.aicompanion.AICompanionMod.LOGGER.warn("[ESCLAVO-RETURN] Companion stuck on terrain on way to portal at {}. Recalculating path...", returnPortalPos);
            }

            double distToPortalSq = companion.blockPosition().distSqr(returnPortalPos);
            if (distToPortalSq <= 9.0D) {
                // Must steer every tick for smooth fluid entry into the portal
                repathCooldown = 0;
                com.aicompanion.ai.dimension.PortalHelper.guideIntoPortal(companion, returnPortalPos);
            } else {
                if (--repathCooldown <= 0) {
                    repathCooldown = 15;
                    com.aicompanion.ai.dimension.PortalHelper.guideIntoPortal(companion, returnPortalPos);
                }
            }
        } else {
            portalWaitTicks++;
            com.aicompanion.ai.dimension.PortalHelper.holdInPortalCenter(companion, returnPortalPos);
            this.substate = "Cruzando el portal hacia la dimensión del propietario... (" + portalWaitTicks + "/10)";
            if (portalWaitTicks >= 10) {
                if (companion.getServer() != null && owner.level() instanceof net.minecraft.server.level.ServerLevel targetLevel) {
                    com.aicompanion.AICompanionMod.LOGGER.info("[ESCLAVO-RETURN] Returning companion across dimensions to {} via portal at {}", targetLevel.dimension().location(), returnPortalPos);
                    CompanionEntity newCompanion = com.aicompanion.ai.dimension.PortalHelper.executeTransition(companion, targetLevel, returnPortalPos);
                    if (newCompanion != null) {
                        returnPortalPos = null;
                        portalWaitTicks = 0;
                    } else {
                        com.aicompanion.AICompanionMod.LOGGER.error("[ESCLAVO-RETURN] Fallo en transición dimensional de retorno. Reintentando...");
                        portalWaitTicks = 0;
                    }
                }
            }
        }
    }

    /**
     * Server-side, owner-only transfer of the mission items. Only main inventory slots are
     * touched (never armour or held tools). Whatever does not fit in the player's inventory
     * stays with the companion — nothing is dropped or destroyed, nothing is duplicated.
     */
    private void deliverTo(ServerPlayer player) {
        if (companion.level().isClientSide()) return;
        if (!player.getUUID().equals(companion.getOwnerUUID())) return;
        if (!player.level().dimension().equals(companion.level().dimension())) return;

        if (deliveryTargets.isEmpty()) {
            companion.tellOwner("He regresado, pero no tengo recursos de una misión para entregarte.");
            return;
        }

        // Auto-craft fragile drops into requested goal blocks before delivery (e.g. 4 glowstone dust -> 1 glowstone block)
        for (ResourceLocation rl : deliveryTargets) {
            if ("minecraft:glowstone".equals(rl.toString()) || "glowstone".equals(rl.getPath())) {
                while (com.aicompanion.inventory.InventoryRequirementTracker.countItem(companion, Items.GLOWSTONE_DUST) >= 4) {
                    boolean crafted = com.aicompanion.util.EmergencyCrafting.craftOnce(companion, Items.GLOWSTONE);
                    if (!crafted) break;
                }
            }
        }

        CompanionInventoryHandler inv = companion.getInventoryHandler();
        Map<String, Integer> deliveredCounts = new LinkedHashMap<>();
        int kept = 0;

        for (int slot = 0; slot < CompanionInventoryHandler.MAIN_SLOTS; slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (stack.isEmpty() || !matchesDelivery(stack)) continue;

            int before = stack.getCount();
            ItemStack moving = stack.copy();
            player.getInventory().add(moving); // shrinks 'moving' by what fit
            int given = before - moving.getCount();
            if (given <= 0) {
                kept += before;
                continue;
            }
            inv.setStackInSlot(slot, moving.isEmpty() ? ItemStack.EMPTY : moving);
            kept += moving.getCount();
            String name = stack.getHoverName().getString();
            deliveredCounts.merge(name, given, Integer::sum);
            ResourceLocation rId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            deliveredItemsMap.merge(rId, given, Integer::sum);
        }

        if (deliveredCounts.isEmpty()) {
            if (kept > 0) {
                companion.tellOwner("Tu inventario está lleno: conservo " + kept + " objetos de la misión hasta que tengas espacio.");
            } else {
                companion.tellOwner("He regresado, pero no tengo objetos de la misión para entregarte.");
            }
            return;
        }

        player.containerMenu.broadcastChanges();
        StringBuilder sb = new StringBuilder("Te entregué ");
        boolean first = true;
        for (Map.Entry<String, Integer> e : deliveredCounts.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(e.getValue()).append(" x ").append(e.getKey());
            first = false;
        }
        sb.append('.');
        if (kept > 0) {
            sb.append(" Tu inventario se llenó: conservo ").append(kept).append(" objetos restantes.");
        }
        companion.tellOwner(sb.toString());
    }

    private boolean matchesDelivery(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (ResourceLocation rl : deliveryTargets) {
            if (rl.equals(itemId)) return true;
            String path = rl.getPath();
            // Wood goals: ChopTask may fall back to any tree species, so any log counts
            if (path.endsWith("_log") || path.equals("log") || path.equals("logs")) {
                if (stack.is(ItemTags.LOGS)) return true;
                continue;
            }
            // Ore / block goals: use the exact goal item match of the mining target
            if (BuiltInRegistries.BLOCK.containsKey(rl) && BuiltInRegistries.BLOCK.get(rl) != Blocks.AIR) {
                if (MiningTarget.of(rl.toString()).isGoalItem(stack)) return true;
            } else {
                Item item = BuiltInRegistries.ITEM.get(rl);
                if (item != null && item == stack.getItem()) return true;
            }
        }
        return false;
    }

    @Override
    public void stop() {
        if (companion != null) {
            companion.setSprinting(false);
            companion.getNavManager().stop();
        }
    }

    @Override
    public String getDescription() {
        if (returnToHome) return "Regresar a casa";
        return deliver ? "Entregar recursos al propietario" : "Regresar con el propietario";
    }
    @Override
    public String getProgress() { return "En camino"; }
    @Override
    public String getTaskType() { return "RETURN"; }
    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("ReturnToHome", returnToHome);
        tag.putBoolean("Deliver", deliver);
        tag.putBoolean("Delivered", delivered);
        ListTag list = new ListTag();
        for (ResourceLocation rl : deliveryTargets) list.add(StringTag.valueOf(rl.toString()));
        tag.put("DeliveryTargets", list);

        CompoundTag delMapTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, Integer> entry : deliveredItemsMap.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                delMapTag.putInt(entry.getKey().toString(), entry.getValue());
            }
        }
        tag.put("DeliveredItemsMap", delMapTag);
        return tag;
    }
    @Override
    public void load(CompoundTag tag) {
        this.returnToHome = tag.getBoolean("ReturnToHome");
        this.deliver = tag.getBoolean("Deliver");
        this.delivered = tag.getBoolean("Delivered");
        this.deliveryTargets.clear();
        ListTag list = tag.getList("DeliveryTargets", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation rl = ResourceLocation.tryParse(list.getString(i));
            if (rl != null) deliveryTargets.add(rl);
        }

        this.deliveredItemsMap.clear();
        if (tag.contains("DeliveredItemsMap")) {
            CompoundTag delMapTag = tag.getCompound("DeliveredItemsMap");
            for (String key : delMapTag.getAllKeys()) {
                ResourceLocation rl = ResourceLocation.tryParse(key);
                if (rl != null) {
                    this.deliveredItemsMap.put(rl, delMapTag.getInt(key));
                }
            }
        }
    }
}
