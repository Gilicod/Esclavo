package com.aicompanion.ai.tasks;

import com.aicompanion.AICompanionMod;
import com.aicompanion.ai.CompanionTask;
import com.aicompanion.ai.equipment.ToolSelectionPolicy;
import com.aicompanion.ai.memory.ShortTermMemory;
import com.aicompanion.ai.nav.InteractionPositionPlanner;
import com.aicompanion.ai.nav.MovementPolicy;
import com.aicompanion.ai.nav.ScaffoldBlockHelper;
import com.aicompanion.ai.planning.PrerequisitePlanner;
import com.aicompanion.ai.spatial.SpatialClusterManager;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import com.aicompanion.internal.baritone.goals.GoalBlock;
import com.aicompanion.internal.baritone.goals.GoalExploreRegion;
import com.aicompanion.internal.baritone.goals.GoalInteractBlock;
import com.aicompanion.inventory.CompanionInventoryHandler;
import com.aicompanion.inventory.EquipmentManager;
import com.aicompanion.inventory.InventoryRequirementTracker;
import com.aicompanion.util.BlockBreakHelper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ChopTask extends CompanionTask {
   private String woodType;
   private int amountNeeded;
   private final boolean handBreakOverride;
   private int startInventoryCount = -1;
   private BlockPos currentLog = null;
   private BlockPos lastBrokenPos = null;
   private int chopTicks = 0;
   private int navRepathCooldown = 0;
   private int searchTicks = 0;
   private final MissionDropTracker dropTracker = new MissionDropTracker();
   private final Set<BlockPos> unreachableLogs = new HashSet<>();
   private final List<BlockPos> currentTreeLogs = new ArrayList<>();
   private final List<BlockPos> placedScaffolds = new ArrayList<>();
   private BlockPos currentTreeBase = null;
   // 1.13.6: Y del tronco más bajo al bloquear el árbol (no cambia al talar, a diferencia del cluster)
   private int treeGroundY = Integer.MIN_VALUE;
   private int pillarFailures = 0;
   private BlockPos pillarFailLog = null;
   private BlockPos pillarBase = null;
   private int pillarWaitTicks = 0;
   private int dropPhaseTicks = 0;
   private BlockPos deferredDropPos = null;
   private BlockPos lastStandPos = null;
   private int standOutOfReachTicks = 0;
   // 1.13.6: memoria de posiciones de aproximación que no funcionaron (para no repetir el mismo error)
   private final Set<BlockPos> failedStands = new HashSet<>();
   private int standNavTicks = 0;
   private double standBestDistSq = Double.MAX_VALUE;
   private static final int STAND_NO_PROGRESS_TICKS = 80;
   private static final int MAX_FAILED_STANDS_PER_LOG = 6;

   public ChopTask(CompanionEntity var1, String var2, int var3) {
      this(var1, var2, var3, false);
   }

   public ChopTask(CompanionEntity var1, String var2, int var3, boolean var4) {
      super(var1);
      this.woodType = var2.toLowerCase();
      this.amountNeeded = var3;
      this.handBreakOverride = var4;
      this.stage = "SEARCHING";
      this.substate = var4 ? "Recolección manual de emergencia (" + var2 + ")" : "Buscando árboles de " + var2;
   }

   public boolean isHandBreakOverride() {
      return this.handBreakOverride;
   }

   @Override
   public List<CompanionTask> checkPreconditions() {
      if (this.handBreakOverride) {
         return Collections.emptyList();
      }

      if (ToolSelectionPolicy.ensureAxeEquipped(this.companion)) {
         return Collections.emptyList();
      }

      if (!EquipmentManager.hasAxeEquipped(this.companion) && EquipmentManager.findBestAxeSlot(this.companion) == -1) {
         return PrerequisitePlanner.resolveAxePrerequisites(this.companion, this.amountNeeded);
      }

      return Collections.emptyList();
   }

   @Override
   public void start() {
      this.companion.setCurrentState(CompanionState.SEARCHING);
      if (this.startInventoryCount < 0) {
         this.startInventoryCount = InventoryRequirementTracker.countWood(this.companion, this.woodType);
      }

      this.dropTracker.reset();
      this.unreachableLogs.clear();
      this.currentTreeLogs.clear();
      this.currentTreeBase = null;
      this.markProgress();
   }

   @Override
   public void pause() {
      super.pause();
      if (this.currentLog != null && this.companion.level() instanceof ServerLevel sl) {
         BlockBreakHelper.cancelBreaking(this.companion, sl, this.currentLog);
      }
      this.companion.getNavManager().stop();
      logEvent("PAUSA_TRABAJO civil=CHOP log=" + (this.currentLog != null ? this.currentLog.toShortString() : "none"));
   }

   @Override
   public void onEmergencyFlee(FleeTask fleeTask) {
      if (this.currentLog != null) {
         this.unreachableLogs.add(this.currentLog.immutable());
         if (this.companion.level() instanceof ServerLevel sl) {
            BlockBreakHelper.cancelBreaking(this.companion, sl, this.currentLog);
         }
         this.currentLog = null;
      }
      this.currentTreeBase = null;
      this.currentTreeLogs.clear();
      this.lastStandPos = null;
      this.failedStands.clear();
      if (this.companion.getMemory() != null) {
         this.companion.getMemory().getShortTermMemory().clearLockedCluster();
      }
      this.standNavTicks = 0;
      this.standBestDistSq = Double.MAX_VALUE;
      this.navRepathCooldown = 0;
      this.chopTicks = 0;
   }

   @Override
   public void resume() {
      super.resume();
      if (!this.handBreakOverride) {
         ToolSelectionPolicy.ensureAxeEquipped(this.companion);
      }
      // 1.13.9: tras una huida el NPC está más lejos que antes; si no se reinicia, el detector de progreso
      // marcaba la posición de aproximación como fallida en ~1,5 s.
      this.standNavTicks = 0;
      this.standBestDistSq = Double.MAX_VALUE;
      this.navRepathCooldown = 0;
      this.chopTicks = 0;
      this.dropPhaseTicks = 0;
      logEvent("REANUDAR_TRABAJO civil=CHOP log=" + (this.currentLog != null ? this.currentLog.toShortString() : "none"));
      var brain = this.companion.getCompanionBrain();
      if (brain != null && this.currentLog != null && 
          (brain.isNearDangerZone(this.currentLog, 20.0, 1) || brain.isPathNearDangerZone(this.companion.blockPosition(), this.currentLog, 16.0, 1))) {
         AICompanionMod.LOGGER.info("[ESCLAVO-SURVIVAL] ChopTask: el árbol en {} está junto a una zona de peligro, busco otro árbol", this.currentLog);
         this.unreachableLogs.add(this.currentLog.immutable());
         this.currentLog = null;
         this.currentTreeBase = null;
         this.currentTreeLogs.clear();
         this.lastStandPos = null;
         this.failedStands.clear();
         this.companion.getMemory().getShortTermMemory().clearLockedCluster();
         Player owner = this.companion.getOwnerPlayer();
         if (owner != null && owner.isAlive() && !owner.isDeadOrDying()) {
            owner.sendSystemMessage(
               Component.literal("[" + this.companion.getCustomCompanionName() + "] ")
                  .withStyle(ChatFormatting.GOLD)
                  .append(Component.literal("Hay un peligro junto a ese árbol; voy a buscar otro más seguro.").withStyle(ChatFormatting.YELLOW))
            );
         }
      }
   }

   @Override
   public void tick() {
      if (!this.paused) {
         int var1 = InventoryRequirementTracker.countWood(this.companion, this.woodType);
         if (this.startInventoryCount < 0) {
            this.startInventoryCount = var1;
         }

         int var2 = Math.max(0, var1 - this.startInventoryCount);
         if (var2 >= this.amountNeeded && this.currentTreeLogs.isEmpty() && this.currentLog == null) {
            BlockPos checkPos = this.lastBrokenPos != null ? this.lastBrokenPos : this.deferredDropPos;
            if (checkPos != null) {
               List varDrops = this.dropTracker.scanDrops(this.companion, checkPos, this::isMatchingLogItem);
               if (!varDrops.isEmpty()) {
                  this.dropTracker.tickCollection(this.companion, checkPos, this::isMatchingLogItem);
                  this.stage = "COLLECTING_DROPS";
                  this.substate = "Recogiendo madera restante del suelo...";
                  this.companion.setCurrentState(CompanionState.NAVIGATING);
                  return;
               }
            }
            this.complete = true;
            this.stage = "COMPLETE";
            this.substate = "Tala completada (" + var2 + "/" + this.amountNeeded + ")";
            this.companion.getNavManager().stop();
            this.companion.addGatheringXP(this.amountNeeded * 4);
            this.companion.incrementTasksCompleted();
            Player var25 = this.companion.getOwnerPlayer();
            if (var25 != null && var25.isAlive() && !var25.isDeadOrDying()) {
               var25.sendSystemMessage(
                  Component.literal("[" + this.companion.getCustomCompanionName() + "] ")
                     .withStyle(ChatFormatting.GOLD)
                     .append(Component.literal("¡Tala completada con éxito (" + var2 + "/" + this.amountNeeded + ")!").withStyle(ChatFormatting.GREEN))
               );
            }
         } else {
            if (!this.handBreakOverride) {
               ToolSelectionPolicy.ensureAxeEquipped(this.companion);
               if (!EquipmentManager.hasAxeEquipped(this.companion) && EquipmentManager.findBestAxeSlot(this.companion) == -1) {
                  int var3 = Math.max(0, var1 - this.startInventoryCount);
                  int var4 = Math.max(1, this.amountNeeded - var3);
                  List var5 = PrerequisitePlanner.resolveAxePrerequisites(this.companion, var4);
                  if (!var5.isEmpty() && this.companion.getCompanionBrain() != null && this.companion.getCompanionBrain().getTaskManager() != null) {
                     this.companion.getCompanionBrain().getTaskManager().pushSubtasks(var5);
                     return;
                  }
               }
            }

            if (this.dropTracker.isBusyCollecting()) {
               this.stage = "COLLECTING_DROPS";
               this.substate = "Recogiendo madera del suelo...";
               this.companion.setCurrentState(CompanionState.NAVIGATING);
               DropCollectionResult var22 = this.dropTracker.tickCollection(this.companion, this.lastBrokenPos, this::isMatchingLogItem);
               if (var22 == DropCollectionResult.INVENTORY_FULL) {
                  int freed = this.companion.getInventoryHandler().freeSpaceForMission(this.companion, this::isAnyLogItem);
                  if (freed > 0) {
                     return;
                  }
                  this.stage = "INVENTORY_FULL";
                  this.substate = "Inventario lleno";
                  this.companion.setCurrentState(CompanionState.INVENTORY_FULL);
                  return;
               }

               if (var22 != DropCollectionResult.COMPLETE && var22 != DropCollectionResult.NO_DROPS_FOUND && var22 != DropCollectionResult.DROP_UNREACHABLE) {
                  return;
               }
            }

            if (this.lastBrokenPos != null) {
               this.dropPhaseTicks++;
               List var23 = this.dropPhaseTicks <= 240
                  ? this.dropTracker.scanDrops(this.companion, this.lastBrokenPos, this::isAnyLogItem)
                  : Collections.emptyList();
               if (!var23.isEmpty()) {
                  if (!this.dropTracker.isBusyCollecting()) {
                     this.dropTracker.startGracePeriod(0);
                  }

                  this.dropTracker.tickCollection(this.companion, this.lastBrokenPos, this::isAnyLogItem);
                  this.stage = "COLLECTING_DROPS";
                  this.substate = "Recogiendo madera del suelo...";
                  this.companion.setCurrentState(CompanionState.NAVIGATING);
                  return;
               } else if (this.dropPhaseTicks < 6) {
                  return;
               }

               this.lastBrokenPos = null;
               this.dropPhaseTicks = 0;
            }

            if (this.companion.level() instanceof ServerLevel var24) {
               if (this.currentLog == null || var24.getBlockState(this.currentLog).isAir()) {
                  this.currentLog = null;
                  ShortTermMemory var27 = this.companion.getMemory().getShortTermMemory();
                  if (var27.hasLockedCluster() && "TREE".equals(var27.getActiveClusterType())) {
                     // 1.13.6: elegir el siguiente tronco como un jugador (alcanzables primero, luego de abajo hacia arriba).
                     // El orden Top-Down obligaba a ir a por el tronco más alto desde el suelo y generaba metas en el aire.
                     this.currentLog = this.selectNextLog(var24, var27);
                  }

                  if (this.currentLog != null) {
                     AICompanionMod.LOGGER.info("[ESCLAVO-SPATIAL] ChopTask: Continuing locked tree cluster (Bottom-Up), next log at {}", this.currentLog);
                  } else {
                     if (this.deferredDropPos != null) {
                        AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] ChopTask: Tree finished, collecting all deferred drops around {}", this.deferredDropPos);
                        this.lastBrokenPos = this.deferredDropPos;
                        this.deferredDropPos = null;
                        this.dropPhaseTicks = 0;
                        this.dropTracker.startGracePeriod(10);
                        return;
                     }

                     if (this.currentTreeBase != null) {
                        this.replantSaplingAtBase(var24, this.currentTreeBase);
                        this.currentTreeBase = null;
                     }

                     this.cleanupPlacedScaffolds(var24);
                     if (this.tickDescendPillar()) {
                        return;
                     }

                     var27.clearLockedCluster();
                     BlockPos var30 = this.findNearestLog(var24);
                     if (var30 == null) {
                        this.stage = "SEARCHING";
                        this.companion.setCurrentState(CompanionState.SEARCHING);
                        this.searchTicks++;

                        BlockPos curPos = this.companion.blockPosition();
                        boolean isUnderground = curPos.getY() < 55 && !var24.canSeeSky(curPos) && var24.dimension() == net.minecraft.world.level.Level.OVERWORLD;

                        if (isUnderground) {
                           this.substate = "Subiendo a la superficie en busca de árboles (" + this.woodType + ")...";
                           if (this.companion.getNavManager().isDone() || this.searchTicks > 100) {
                              this.searchTicks = 0;
                              BlockPos surfacePos = var24.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, curPos);
                              AICompanionMod.LOGGER.info("[ChopTask] Esclavo está bajo tierra en y={}, subiendo a la superficie en {}", curPos.getY(), surfacePos);
                              this.companion.getNavManager().navigateTo(new GoalBlock(surfacePos), MovementPolicy.sprint());
                           }
                        } else {
                           this.substate = "Explorando terreno en busca de árboles (" + this.woodType + ")...";
                           if (this.companion.getNavManager().isDone() || this.searchTicks > 200) {
                              this.searchTicks = 0;
                              AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] ChopTask: No trees found in immediate radius, exploring region for woodType='{}'", this.woodType);
                              this.companion.getNavManager().navigateTo(new GoalExploreRegion(this.companion.blockPosition(), 32.0), MovementPolicy.sprint());
                           }
                        }

                        return;
                     }

                     this.currentTreeBase = var30;
                     List var6 = SpatialClusterManager.scanTreeCluster(var24, var30, var1x -> this.isMatchingLog(var1x, false));
                     this.companion.getMemory().getWorldModel().rememberResourceDeposit(this.currentTreeBase, var24.getBlockState(this.currentTreeBase).getBlock(), var6.size(), var24.getGameTime());
                     var27.lockCluster("TREE", this.currentTreeBase, var6);
                     this.failedStands.clear();
                     this.pillarFailures = 0;
                     this.pillarFailLog = null;
                     this.treeGroundY = Integer.MAX_VALUE;
                     for (Object var29 : var6) {
                        this.treeGroundY = Math.min(this.treeGroundY, ((BlockPos)var29).getY());
                     }

                     if (this.treeGroundY == Integer.MAX_VALUE) {
                        this.treeGroundY = Integer.MIN_VALUE;
                     }
                     this.lastStandPos = null;
                     this.currentLog = this.selectNextLog(var24, var27);
                     if (this.currentLog == null) {
                        this.currentLog = var27.peekNextClusterBlock();
                     }
                     AICompanionMod.LOGGER
                        .info("[ESCLAVO-SPATIAL] ChopTask: Target Lock engaged on new tree with {} logs at base {}", var6.size(), this.currentTreeBase);
                  }
               }

               this.searchTicks = 0;
               Vec3 var28 = this.companion.getEyePosition();
               Vec3 var31 = Vec3.atCenterOf(this.currentLog);
               double var32 = var28.distanceToSqr(var31);
               double var8 = this.currentLog.getX() + 0.5 - this.companion.getX();
               double var10 = this.currentLog.getZ() + 0.5 - this.companion.getZ();
               double var12 = var8 * var8 + var10 * var10;
               double var14 = var12 <= 9.0 ? 27.0 : 20.25;
               if (this.pillarBase != null) {
                  if (this.companion.getY() >= this.pillarBase.getY() + 1.0 && var24.getBlockState(this.pillarBase).canBeReplaced()) {
                     int var33 = ScaffoldBlockHelper.findBestScaffoldSlot(this.companion);
                     ItemStack var36 = var33 >= 0 ? this.companion.getInventoryHandler().getStackInSlot(var33) : ItemStack.EMPTY;
                     if (var36.getItem() instanceof BlockItem var38) {
                        var24.setBlock(this.pillarBase, var38.getBlock().defaultBlockState(), 3);
                        var24.playSound(null, this.pillarBase, var38.getBlock().defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                        this.companion.swing(InteractionHand.MAIN_HAND);
                        this.placedScaffolds.add(this.pillarBase.immutable());
                        this.companion.getTemporaryBlocks().track(this.pillarBase, var38.getBlock());
                        var36.shrink(1);
                        if (var36.isEmpty()) {
                           this.companion.getInventoryHandler().setStackInSlot(var33, ItemStack.EMPTY);
                        }

                        this.pillarFailures = 0;
                        AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] ChopTask: Pillar block {} placed at {} (subiendo hacia {})", var38.getBlock().getDescriptionId(), this.pillarBase, this.currentLog);
                     } else {
                        AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] ChopTask: Pillar failed at {}: no usable scaffold block (slot={})", this.pillarBase, var33);
                        this.registerPillarFailure();
                     }

                     this.pillarBase = null;
                     return;
                  }

                  if (++this.pillarWaitTicks <= 12 && (!this.companion.onGround() || this.pillarWaitTicks <= 3)) {
                     return;
                  }

                  AICompanionMod.LOGGER.info(
                     "[ESCLAVO-DEBUG] ChopTask: Pillar jump failed at {} (y={}, bloque='{}', techo='{}')",
                     this.pillarBase,
                     String.format("%.2f", this.companion.getY()),
                     var24.getBlockState(this.pillarBase).getBlock().getDescriptionId(),
                     var24.getBlockState(this.pillarBase.above(2)).getBlock().getDescriptionId()
                  );
                  this.registerPillarFailure();
                  this.pillarBase = null;
               }

               boolean var16 = this.lastStandPos != null
                  && this.companion.blockPosition().distSqr(this.lastStandPos) <= 2.0
                  && this.companion.getNavManager().isDone();
               if (var32 > var14 && var16 && var32 <= 36.0) {
                  var14 = var32;
               }

               if (var32 > var14 && var16) {
                  if (++this.standOutOfReachTicks > 40) {
                     AICompanionMod.LOGGER
                        .info("[ESCLAVO-DEBUG] ChopTask: Reached stand spot {} but log {} still out of reach, skipping it", this.lastStandPos, this.currentLog);
                     this.unreachableLogs.add(this.currentLog);
                     this.currentLog = null;
                     this.standOutOfReachTicks = 0;
                     this.lastStandPos = null;
                     return;
                  }
               } else {
                  this.standOutOfReachTicks = 0;
               }

               if (var32 > var14) {
                  boolean onTrackedPillar = this.companion.getTemporaryBlocks().isStandingOnTracked(this.companion);
                  int heightDiff = this.currentLog.getY() - this.companion.blockPosition().getY();
                  if (var12 <= 4.0 && (heightDiff >= 4 || (onTrackedPillar && heightDiff >= 2)) && this.companion.onGround() && !this.isPillarBlocked()) {
                     int var17 = ScaffoldBlockHelper.findBestScaffoldSlot(this.companion);
                     if (var17 == -1) {
                        ScaffoldBlockHelper.acquireScaffoldBlockAround(this.companion, var24);
                        var17 = ScaffoldBlockHelper.findBestScaffoldSlot(this.companion);
                     }

                     if (var17 != -1 && this.companion.getSettings().isAllowBlockPlacement()) {
                        BlockPos var18 = this.companion.blockPosition();
                        if (var24.getBlockState(var18.above(2)).isAir()) {
                           this.companion.getNavManager().stop();
                           this.companion.getJumpControl().jump();
                           this.pillarBase = var18.immutable();
                           this.pillarWaitTicks = 0;
                           AICompanionMod.LOGGER.debug("[ESCLAVO-SPATIAL] ChopTask: Jump-pillaring at {} to reach high log {}", var18, this.currentLog);
                           return;
                        }
                     }
                  }

                  if (onTrackedPillar && heightDiff > 0) {
                     return;
                  }

                  this.stage = "NAVIGATING";
                  this.substate = "Aproximándose al árbol (" + (int)Math.sqrt(var32) + "m)";
                  this.companion.setCurrentState(CompanionState.NAVIGATING);
                  if (this.tickStandProgress(var24)) {
                     return;
                  }

                  if (!var16 && (--this.navRepathCooldown <= 0 || this.companion.getNavManager().isDone())) {
                     BlockPos var34 = this.findGroundStand(var24, this.currentLog);
                     if (var34 != null) {
                        if (!var34.equals(this.lastStandPos)) {
                           this.standNavTicks = 0;
                           this.standBestDistSq = Double.MAX_VALUE;
                           AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] ChopTask: Navigating to tree at {} standPos={} (suelo, fallidos={})", this.currentLog, var34, this.failedStands.size());
                        }

                        this.lastStandPos = var34;
                        this.companion.getNavManager().navigateTo(new GoalBlock(var34), MovementPolicy.sprint());
                     } else {
                        // Sin hueco de suelo válido: dejar que el A* busque cualquier posición desde la que alcanzar el tronco.
                        this.lastStandPos = null;
                        AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] ChopTask: No ground stand for {}, using GoalInteractBlock", this.currentLog);
                        this.companion.getNavManager().navigateTo(new GoalInteractBlock(this.currentLog, 4.5), MovementPolicy.sprint());
                     }

                     this.navRepathCooldown = 20;
                  }
               } else {
                  this.stage = "CHOPPING";
                  this.substate = "Talando tronco...";
                  this.companion.getNavManager().stop();
                  this.companion.setCurrentState(CompanionState.CHOPPING);
                  BlockState var35 = var24.getBlockState(this.currentLog);
                  if (var35.isAir()) {
                     this.currentLog = null;
                     this.chopTicks = 0;
                     return;
                  }

                  if (var35.is(BlockTags.LEAVES)) {
                     var24.destroyBlock(this.currentLog, true, this.companion);
                     this.chopTicks = 0;
                     return;
                  }

                  SpatialClusterManager.clearObstructingLeaves(var24, this.currentLog, this.companion.blockPosition().above(), this.companion);
                  if (!this.handBreakOverride) {
                     ToolSelectionPolicy.ensureAxeEquipped(this.companion);
                  }
                  int var37 = BlockBreakHelper.calculateBreakTicks(this.companion, this.currentLog, var35);
                  if (var37 <= 0) {
                     this.currentLog = null;
                     this.chopTicks = 0;
                     return;
                  }

                  this.chopTicks++;
                  if (this.chopTicks % 10 == 0) {
                     AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] ChopTask: Chopping {} progress: {}/{} ticks", this.currentLog, this.chopTicks, var37);
                  }

                  boolean var19 = BlockBreakHelper.tickBreaking(this.companion, var24, this.currentLog, this.chopTicks, var37);
                  if (var19) {
                     this.lastBrokenPos = this.currentLog;
                     boolean var20 = BlockBreakHelper.finishBreaking(this.companion, var24, this.currentLog);
                     AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] ChopTask: Finished breaking at {}: success={}", this.currentLog, var20);
                     if (var20) {
                        if (this.companion.getMemory() != null) {
                           this.companion.getMemory().getShortTermMemory().removeClusterBlock(this.currentLog);
                        }

                        this.dropPhaseTicks = 0;
                        ShortTermMemory var21 = this.companion.getMemory() != null ? this.companion.getMemory().getShortTermMemory() : null;
                        this.deferredDropPos = this.currentLog;
                        this.lastBrokenPos = this.currentLog;
                        this.dropTracker.startGracePeriod(4);

                        this.markProgress();
                     }

                     this.chopTicks = 0;
                     this.currentLog = null;
                  }
               }
            }
         }
      }
   }

   /**
    * 1.13.6 — Elige el siguiente tronco del árbol bloqueado como lo haría un jugador:
    * primero los que ya están al alcance, y si no, el más bajo (de abajo hacia arriba).
    */
   private BlockPos selectNextLog(ServerLevel level, ShortTermMemory stm) {
      List<BlockPos> cluster = stm.getLockedCluster();
      if (cluster == null || cluster.isEmpty()) {
         return null;
      }

      Vec3 eye = this.companion.getEyePosition();
      BlockPos best = null;
      boolean bestReachable = false;
      double bestScore = Double.MAX_VALUE;
      for (BlockPos pos : new ArrayList<>(cluster)) {
         if (this.unreachableLogs.contains(pos)) {
            continue;
         }

         BlockState state = level.getBlockState(pos);
         if (state.isAir() || !this.isMatchingLog(state, false)) {
            continue;
         }

         double distSq = eye.distanceToSqr(Vec3.atCenterOf(pos));
         boolean reachable = distSq <= 20.25;
         double score = pos.getY() * 10.0 + Math.sqrt(distSq);
         if (best == null || reachable && !bestReachable || reachable == bestReachable && score < bestScore) {
            best = pos;
            bestReachable = reachable;
            bestScore = score;
         }
      }

      return best;
   }

   /**
    * 1.13.6 — Busca una posición de SUELO junto al tronco (nunca encima de hojas ni en la copa).
    * Prioriza: alcanza el tronco > pegado al tronco > cerca del NPC. Ignora posiciones que ya fallaron.
    */
   private BlockPos findGroundStand(ServerLevel level, BlockPos log) {
      int groundY = log.getY();
      List<BlockPos> cluster = this.companion.getMemory() != null ? this.companion.getMemory().getShortTermMemory().getLockedCluster() : null;
      if (cluster != null) {
         for (BlockPos p : cluster) {
            if (Math.abs(p.getX() - log.getX()) <= 3 && Math.abs(p.getZ() - log.getZ()) <= 3 && p.getY() < groundY) {
               groundY = p.getY();
            }
         }
      }

      if (this.currentTreeBase != null && this.currentTreeBase.getY() < groundY) {
         groundY = this.currentTreeBase.getY();
      }

      if (this.treeGroundY != Integer.MIN_VALUE && this.treeGroundY < groundY) {
         groundY = this.treeGroundY;
      }

      // Bajar por la columna del tronco (aire/troncos/hojas ya talados) hasta encontrar el suelo real.
      BlockPos.MutableBlockPos probe = log.mutable();
      for (int i = 0; i < 12 && probe.getY() > level.getMinBuildHeight(); i++) {
         probe.move(0, -1, 0);
         BlockState ps = level.getBlockState(probe);
         if (!ps.isAir() && !ps.is(BlockTags.LOGS) && !ps.is(BlockTags.LEAVES) && ps.blocksMotion()) {
            groundY = Math.min(groundY, probe.getY() + 1);
            break;
         }
      }

      int yLow = Math.min(groundY, this.companion.blockPosition().getY()) - 3;
      int yHigh = groundY + 2;

      BlockPos me = this.companion.blockPosition();
      Vec3 logCenter = Vec3.atCenterOf(log);
      BlockPos best = null;
      double bestScore = Double.MAX_VALUE;
      for (int dx = -3; dx <= 3; dx++) {
         for (int dz = -3; dz <= 3; dz++) {
            for (int y = yLow; y <= yHigh; y++) {
               BlockPos stand = new BlockPos(log.getX() + dx, y, log.getZ() + dz);
               if (stand.equals(log) || this.failedStands.contains(stand)) {
                  continue;
               }

               if (!InteractionPositionPlanner.isWalkableStand(level, stand)) {
                  continue;
               }

               BlockState floor = level.getBlockState(stand.below());
               if (floor.is(BlockTags.LEAVES)) {
                  continue;
               }

               Vec3 eye = new Vec3(stand.getX() + 0.5, stand.getY() + 1.62, stand.getZ() + 0.5);
               double horiz = dx * dx + dz * dz;
               boolean reachable = eye.distanceToSqr(logCenter) <= (horiz <= 9.0 ? 27.0 : 20.25);
               double score = (reachable ? 0.0 : 100.0) + horiz * 2.0 + Math.abs(y - groundY) * 3.0 + Math.sqrt(stand.distSqr(me)) * 0.5;
               if (score < bestScore) {
                  bestScore = score;
                  best = stand;
               }
            }
         }
      }

      return best;
   }

   /** 1.13.6 — cuenta saltos de pilar fallidos para el tronco actual. */
   private void registerPillarFailure() {
      if (this.currentLog == null || !this.currentLog.equals(this.pillarFailLog)) {
         this.pillarFailLog = this.currentLog == null ? null : this.currentLog.immutable();
         this.pillarFailures = 0;
      }

      this.pillarFailures++;
      if (this.pillarFailures >= 3) {
         AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] ChopTask: Pillar failed {} times for log {}, switching strategy (otra posición / otro tronco)", this.pillarFailures, this.currentLog);
      }
   }

   private boolean isPillarBlocked() {
      return this.currentLog != null && this.currentLog.equals(this.pillarFailLog) && this.pillarFailures >= 3;
   }

   /**
    * 1.13.6 — Si el NPC no se acerca a la posición elegida en ~4 s, la marca como fallida y prueba otra.
    * Tras varias posiciones fallidas para el mismo tronco, lo descarta (sin abandonar la misión).
    * @return true si se ha descartado el tronco actual en este tick.
    */
   private boolean tickStandProgress(ServerLevel level) {
      if (this.lastStandPos == null) {
         return false;
      }

      double distSq = this.companion.position().distanceToSqr(Vec3.atBottomCenterOf(this.lastStandPos));
      if (distSq < this.standBestDistSq - 0.25) {
         this.standBestDistSq = distSq;
         this.standNavTicks = 0;
         return false;
      }

      if (++this.standNavTicks < STAND_NO_PROGRESS_TICKS) {
         return false;
      }

      AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] ChopTask: No progress towards stand {} for log {}, trying another approach", this.lastStandPos, this.currentLog);
      this.failedStands.add(this.lastStandPos);
      this.lastStandPos = null;
      this.standNavTicks = 0;
      this.standBestDistSq = Double.MAX_VALUE;
      this.navRepathCooldown = 0;
      long failedNearLog = this.failedStands.stream().filter(p -> p.distManhattan(this.currentLog) <= 12).count();
      if (failedNearLog >= MAX_FAILED_STANDS_PER_LOG) {
         AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] ChopTask: {} approaches failed for log {}, marking unreachable", failedNearLog, this.currentLog);
         this.unreachableLogs.add(this.currentLog.immutable());
         this.currentLog = null;
         return true;
      }

      return false;
   }

   private boolean isMatchingLogItem(ItemStack var1) {
      if (!var1.is(ItemTags.LOGS) && !var1.is(ItemTags.WARPED_STEMS) && !var1.is(ItemTags.CRIMSON_STEMS)) {
         return false;
      } else if (!this.woodType.equals("any")
         && !this.woodType.equals("all")
         && !this.woodType.equals("madera")
         && !this.woodType.equals("wood")
         && !this.woodType.isEmpty()) {
         String var2 = var1.getItem().getDescriptionId().toLowerCase();
         return InventoryRequirementTracker.isWoodTypeMatch(var2, this.woodType);
      } else {
         return true;
      }
   }

   private boolean isAnyLogItem(ItemStack var1) {
      return var1.is(ItemTags.LOGS) || var1.is(ItemTags.WARPED_STEMS) || var1.is(ItemTags.CRIMSON_STEMS);
   }

   private boolean isMatchingLog(BlockState var1, boolean var2) {
      if (!var1.is(BlockTags.LOGS) && !var1.is(BlockTags.WARPED_STEMS) && !var1.is(BlockTags.CRIMSON_STEMS)) {
         return false;
      }

      if (!var2) {
         return true;
      }

      String var3 = var1.getBlock().getDescriptionId().toLowerCase();
      return InventoryRequirementTracker.isWoodTypeMatch(var3, this.woodType);
   }

   private static boolean hasLeavesNear(ServerLevel var0, BlockPos var1) {
      for (int var2 = 0; var2 <= 7; var2++) {
         for (int var3 = -2; var3 <= 2; var3++) {
            for (int var4 = -2; var4 <= 2; var4++) {
               if (var0.getBlockState(var1.offset(var3, var2, var4)).is(BlockTags.LEAVES)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   /** 1.13.51: tronco junto a una zona de peligro o cuyo camino cruza zona de peligro. */
   private boolean isInDangerZone(BlockPos pos) {
      var brain = this.companion.getCompanionBrain();
      return brain != null && (brain.isNearDangerZone(pos, 20.0, 1) || brain.isPathNearDangerZone(this.companion.blockPosition(), pos, 16.0, 1));
   }

   private BlockPos findNearestLog(ServerLevel var1) {
      BlockPos var2 = this.companion.blockPosition();
      BlockPos var3 = null;
      double var4 = Double.MAX_VALUE;
      if (this.unreachableLogs.size() > 50) {
         this.unreachableLogs.clear();
      }

      for (net.minecraft.world.level.block.Block b : new net.minecraft.world.level.block.Block[]{net.minecraft.world.level.block.Blocks.OAK_LOG, net.minecraft.world.level.block.Blocks.BIRCH_LOG, net.minecraft.world.level.block.Blocks.SPRUCE_LOG, net.minecraft.world.level.block.Blocks.JUNGLE_LOG, net.minecraft.world.level.block.Blocks.ACACIA_LOG, net.minecraft.world.level.block.Blocks.DARK_OAK_LOG, net.minecraft.world.level.block.Blocks.MANGROVE_LOG, net.minecraft.world.level.block.Blocks.CHERRY_LOG}) {
         var rem = this.companion.getMemory().getWorldModel().findClosestResourceDeposit(b, var2, 32.0);
         if (rem.isPresent() && var1.isLoaded(rem.get())) {
            BlockPos rp = rem.get();
            if (!this.unreachableLogs.contains(rp) && this.isMatchingLog(var1.getBlockState(rp), false) && !this.isInDangerZone(rp)) {
               if (Math.abs(rp.getY() - var2.getY()) <= 5) {
                  return rp;
               }
            }
         }
      }

      for (int var6 = -4; var6 <= 24; var6++) {
         for (int var7 = -24; var7 <= 24; var7++) {
            for (int var8 = -24; var8 <= 24; var8++) {
               BlockPos var9 = var2.offset(var7, var6, var8);
               if (!this.unreachableLogs.contains(var9)) {
                  BlockState var10 = var1.getBlockState(var9);
                  if (this.isMatchingLog(var10, true) && !this.isInDangerZone(var9)) {
                     double var11 = var2.distSqr(var9);
                     double dy = Math.abs(var9.getY() - var2.getY());
                     if (dy > 3.0) {
                        var11 += dy * dy * 4.0;
                     }
                     if (var9.getY() > var2.getY() + 5) {
                        var11 += 300.0;
                     }
                     BlockState var13 = var1.getBlockState(var9.below());
                     if (!hasLeavesNear(var1, var9)) {
                        var11 *= 6.0;
                     } else if (var13.isAir() && var11 <= 144.0) {
                        var11 *= 0.2;
                     } else if (var13.is(BlockTags.DIRT)) {
                        var11 *= 0.6;
                     }

                     if (var11 < var4) {
                        var4 = var11;
                        var3 = var9;
                     }
                  }
               }
            }
         }
      }

      if (var3 == null) {
         for (int var14 = -4; var14 <= 24; var14++) {
            for (int var15 = -24; var15 <= 24; var15++) {
               for (int var16 = -24; var16 <= 24; var16++) {
                  BlockPos var17 = var2.offset(var15, var14, var16);
                  if (!this.unreachableLogs.contains(var17)) {
                     BlockState var18 = var1.getBlockState(var17);
                     if (this.isMatchingLog(var18, false) && !this.isInDangerZone(var17)) {
                        double var19 = var2.distSqr(var17);
                        double dyFallback = Math.abs(var17.getY() - var2.getY());
                        if (dyFallback > 3.0) {
                           var19 += dyFallback * dyFallback * 4.0;
                        }
                        if (var17.getY() > var2.getY() + 5) {
                           var19 += 300.0;
                        }
                        BlockState var20 = var1.getBlockState(var17.below());
                        if (!hasLeavesNear(var1, var17)) {
                           var19 *= 6.0;
                        } else if (var20.isAir() && var19 <= 144.0) {
                           var19 *= 0.2;
                        } else if (var20.is(BlockTags.DIRT)) {
                           var19 *= 0.6;
                        }

                        if (var19 < var4) {
                           var4 = var19;
                           var3 = var17;
                        }
                     }
                  }
               }
            }
         }

         if (var3 != null) {
            this.woodType = "any";
         }
      }

      if (var3 != null) {
         AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] findNearestLog found {} for woodType='{}'", var3, this.woodType);
      }

      return var3;
   }

   private List<BlockPos> scanTreeStructure(ServerLevel var1, BlockPos var2) {
      ArrayList<BlockPos> var3 = new ArrayList<>();
      HashSet<BlockPos> var4 = new HashSet<>();
      ArrayDeque<BlockPos> var5 = new ArrayDeque<>();
      BlockPos var6 = var2;

      while (var6.getY() > var1.getMinBuildHeight() && this.isMatchingLog(var1.getBlockState(var6.below()), false)) {
         var6 = var6.below();
      }

      var5.add(var6);
      var4.add(var6);

      while (!var5.isEmpty() && var3.size() < 64) {
         BlockPos var7 = (BlockPos)var5.poll();
         BlockState var8 = var1.getBlockState(var7);
         if (this.isMatchingLog(var8, false)) {
            var3.add(var7);

            for (int var9 = -1; var9 <= 3; var9++) {
               for (int var10 = -1; var10 <= 1; var10++) {
                  for (int var11 = -1; var11 <= 1; var11++) {
                     if (var10 != 0 || var9 != 0 || var11 != 0) {
                        BlockPos var12 = var7.offset(var10, var9, var11);
                        if (!var4.contains(var12)) {
                           var4.add(var12);
                           BlockState var13 = var1.getBlockState(var12);
                           if (this.isMatchingLog(var13, false)) {
                              var5.add(var12);
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      var3.sort((var1x, var2x) -> {
         int var3x = Integer.compare(var1x.getY(), var2x.getY());
         return var3x != 0 ? var3x : Double.compare(var1x.distSqr(this.companion.blockPosition()), var2x.distSqr(this.companion.blockPosition()));
      });
      return var3;
   }

   private void replantSaplingAtBase(ServerLevel var1, BlockPos var2) {
      if (var2 != null) {
         BlockPos var3 = var2;

         while (var3.getY() > var1.getMinBuildHeight() && var1.getBlockState(var3).isAir()) {
            var3 = var3.below();
         }

         BlockPos var4 = var3.above();
         BlockState var5 = var1.getBlockState(var3);
         if ((var5.is(BlockTags.DIRT) || var5.is(Blocks.FARMLAND)) && var1.getBlockState(var4).isAir()) {
            CompanionInventoryHandler var6 = this.companion.getInventoryHandler();

            for (int var7 = 0; var7 < 36; var7++) {
               ItemStack var8 = var6.getStackInSlot(var7);
               if (!var8.isEmpty() && var8.is(ItemTags.SAPLINGS) && var8.getItem() instanceof BlockItem var9) {
                  BlockState var11 = var9.getBlock().defaultBlockState();
                  var1.setBlock(var4, var11, 3);
                  var1.playSound(null, var4, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                  var8.shrink(1);
                  if (var8.isEmpty()) {
                     var6.setStackInSlot(var7, ItemStack.EMPTY);
                  }

                  this.companion.swing(InteractionHand.MAIN_HAND);
                  AICompanionMod.LOGGER.info("[ChopTask] Replanted sapling at {}", var4);
                  break;
               }
            }
         }
      }
   }

   @Override
   public void onStuckTargetUnreachable() {
      if (this.currentLog != null) {
         this.unreachableLogs.add(this.currentLog.immutable());
         if (this.companion.getMemory() != null) {
            this.companion.getMemory().getShortTermMemory().removeClusterBlock(this.currentLog);
         }

         AICompanionMod.LOGGER.info("[ESCLAVO-DEBUG] ChopTask: Blacklisting unreachable log at {}", this.currentLog);
         if (this.companion.level() instanceof ServerLevel var1) {
            BlockBreakHelper.cancelBreaking(this.companion, var1, this.currentLog);
         }

         this.currentLog = null;
         this.chopTicks = 0;
         this.navRepathCooldown = 0;
         this.stage = "SEARCHING";
         this.substate = "Buscando ruta alternativa a otro árbol...";
      }
   }

   @Override
   public void stop() {
      if (this.companion.getMemory() != null) {
         this.companion.getMemory().getShortTermMemory().clearLockedCluster();
      }

      if (this.companion.level() instanceof ServerLevel var1) {
         this.cleanupPlacedScaffolds(var1);
         if (this.currentLog != null) {
            BlockBreakHelper.cancelBreaking(this.companion, var1, this.currentLog);
         }
      }

      this.pillarBase = null;
      this.companion.getNavManager().stop();
   }

   private void cleanupPlacedScaffolds(ServerLevel var1) {
      if (!this.placedScaffolds.isEmpty()) {
         for (BlockPos var3 : this.placedScaffolds) {
            BlockState var4 = var1.getBlockState(var3);
            if (!var4.isAir() && !this.companion.getTemporaryBlocks().isTracked(var3)) {
               this.companion.getTemporaryBlocks().track(var3, var4.getBlock());
            }
         }

         this.placedScaffolds.clear();
      }
   }

   private int descendWatchdogTicks = 0;
   private int lastDescendY = Integer.MIN_VALUE;

   private boolean tickDescendPillar() {
      if (!this.companion.getTemporaryBlocks().isStandingOnTracked(this.companion)) {
         this.descendWatchdogTicks = 0;
         this.lastDescendY = Integer.MIN_VALUE;
         return false;
      }

      int currentY = this.companion.blockPosition().getY();
      if (currentY != this.lastDescendY) {
         this.lastDescendY = currentY;
         this.descendWatchdogTicks = 0;
      } else if (++this.descendWatchdogTicks > 60) {
         this.companion.getTemporaryBlocks().untrack(this.companion.blockPosition().below());
         this.descendWatchdogTicks = 0;
         this.lastDescendY = Integer.MIN_VALUE;
         return false;
      }

      this.companion.getNavManager().stop();
      this.companion.getTemporaryBlocks().tickCleanup(this.companion, true, true);
      this.stage = "DESCENDING";
      this.substate = "Bajando del andamio temporal";
      return true;
   }

   @Override
   public String getDescription() {
      if (this.handBreakOverride) {
         return "Recolectar madera manualmente (" + this.amountNeeded + ")";
      } else {
         return !"any".equals(this.woodType) && !"madera".equals(this.woodType) && !this.woodType.isEmpty()
            ? "Talar " + this.amountNeeded + " de madera (" + this.woodType + ")"
            : "Talar " + this.amountNeeded + " de madera";
      }
   }

   @Override
   public String getProgress() {
      int var1 = InventoryRequirementTracker.countWood(this.companion, this.woodType);
      int var2 = this.startInventoryCount >= 0 ? Math.max(0, var1 - this.startInventoryCount) : 0;
      return var2 + "/" + this.amountNeeded + " (" + this.stage + ")";
   }

   @Override
   public String getTaskType() {
      return "CHOP";
   }

   @Override
   public CompoundTag save() {
      CompoundTag var1 = new CompoundTag();
      var1.putString("WoodType", this.woodType);
      var1.putInt("AmountNeeded", this.amountNeeded);
      var1.putInt("StartInventoryCount", this.startInventoryCount);
      var1.putBoolean("HandBreakOverride", this.handBreakOverride);
      if (this.currentLog != null) {
         var1.put("CurrentLog", NbtUtils.writeBlockPos(this.currentLog));
      }

      return var1;
   }

   @Override
   public void load(CompoundTag var1) {
      this.woodType = var1.getString("WoodType");
      this.amountNeeded = var1.getInt("AmountNeeded");
      if (var1.contains("StartInventoryCount")) {
         this.startInventoryCount = var1.getInt("StartInventoryCount");
      }

      if (var1.contains("CurrentLog")) {
         this.currentLog = NbtUtils.readBlockPos(var1.getCompound("CurrentLog"));
      }
   }
}
