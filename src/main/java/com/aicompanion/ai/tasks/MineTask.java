package com.aicompanion.ai.tasks;

import com.aicompanion.AICompanionMod;
import com.aicompanion.ai.CompanionTask;
import com.aicompanion.ai.equipment.ToolSelectionPolicy;
import com.aicompanion.ai.memory.ShortTermMemory;
import com.aicompanion.ai.mining.MiningTarget;
import com.aicompanion.ai.mining.SearchRegionManager;
import com.aicompanion.ai.nav.MovementPolicy;
import com.aicompanion.ai.planning.PrerequisitePlanner;
import com.aicompanion.ai.planning.RecipeDependencyGraph;
import com.aicompanion.ai.spatial.SpatialClusterManager;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.entity.CompanionState;
import com.aicompanion.internal.baritone.goals.GoalExploreRegion;
import com.aicompanion.internal.baritone.goals.GoalInteractBlock;
import com.aicompanion.internal.baritone.goals.GoalNear;
import com.aicompanion.inventory.CompanionInventoryHandler;
import com.aicompanion.inventory.InventoryRequirementTracker;
import com.aicompanion.util.BlockBreakHelper;
import com.aicompanion.util.TerrainPolicy;
import java.util.Collections;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class MineTask extends CompanionTask {
   private MiningTarget miningTarget;
   private int amountNeeded;
   private int startInventoryCount = -1;
   private int obtainedCount = 0;
   private BlockPos currentTargetBlock = null;
   private BlockPos lastBrokenPos = null;
   private int miningTicks = 0;
   private final SearchRegionManager searchManager = new SearchRegionManager();
   private final MissionDropTracker dropTracker = new MissionDropTracker();
   private int searchCooldown = 0;
   private int searchAttempts = 0;
   private int navRepathCooldown = 0;
   private boolean descendingMode = false;
   private int targetDescentY = -53;
   private Direction staircaseDir = Direction.NORTH;
   private int descentCooldown = 0;
   private int descentTicks = 0;
   private int lastDescentY = 0;
   private int lastYProgressTick = 0;
   private BlockPos obstructionPos = null;
   private int obstructionTicks = 0;
   private int losFailures = 0;
   private int dropPhaseTicks = 0;
   private BlockPos navTargetPos = null;
   private int navStuckTicks = 0;
   private double lastNavDistSq = Double.MAX_VALUE;
   private int consecutiveFlees = 0;
   private int consecutiveNavStuckFailures = 0;

   public MineTask(CompanionEntity var1, ResourceLocation var2, int var3) {
      this(var1, MiningTarget.of(var2.toString()), var3);
   }

   public MineTask(CompanionEntity var1, MiningTarget var2, int var3) {
      super(var1);
      this.miningTarget = var2;
      this.amountNeeded = var3;
      this.stage = "CHECK_TOOL";
      this.substate = "Preparando herramientas";
   }

   @Override
   public List<CompanionTask> checkPreconditions() {
      int var1 = RecipeDependencyGraph.getRequiredPickaxeTier(this.miningTarget);
      if (var1 <= -1) {
         if (this.miningTarget != null && this.miningTarget.getPrimaryBlock() != null) {
            BlockState var3 = this.miningTarget.getPrimaryBlock().defaultBlockState();
            ToolSelectionPolicy.ensureToolForBlock(this.companion, var3);
         }
         return Collections.emptyList();
      } else {
         int var2 = RecipeDependencyGraph.getHighestOwnedPickaxeTier(this.companion);
         if (var2 >= var1) {
            BlockState var3 = this.miningTarget.getPrimaryBlock().defaultBlockState();
            ToolSelectionPolicy.ensurePickaxeEquipped(this.companion, var3);
            return Collections.emptyList();
         } else {
            if (com.aicompanion.util.EmergencyCrafting.ensurePickaxe(this.companion)) {
               var2 = RecipeDependencyGraph.getHighestOwnedPickaxeTier(this.companion);
               if (var2 >= var1) {
                  BlockState var3 = this.miningTarget.getPrimaryBlock().defaultBlockState();
                  ToolSelectionPolicy.ensurePickaxeEquipped(this.companion, var3);
                  return Collections.emptyList();
               }
            }
            return PrerequisitePlanner.resolvePickaxePrerequisites(this.companion, var2, var1);
         }
      }
   }

   @Override
   public void start() {
      this.companion.setCurrentState(CompanionState.SEARCHING);
      this.searchManager.clearCache();
      this.dropTracker.reset();
      if (this.startInventoryCount < 0) {
         this.startInventoryCount = InventoryRequirementTracker.countMiningTarget(this.companion, this.miningTarget);
      }

      this.stage = "SEARCHING";
      this.substate = "Iniciando búsqueda de " + this.miningTarget.getName();
      this.markProgress();
   }

   @Override
   public void tick() {
      if (!this.paused) {
         // Auto-crafting: When mining fragile blocks that dropped raw components (e.g. Glowstone Dust -> Glowstone Block)
         if (this.miningTarget != null && ("Piedra Luminosa".equalsIgnoreCase(this.miningTarget.getName()) || "glowstone".equalsIgnoreCase(this.miningTarget.getQuery()))) {
            while (InventoryRequirementTracker.countItem(this.companion, Items.GLOWSTONE_DUST) >= 4
                    && InventoryRequirementTracker.countMiningTarget(this.companion, this.miningTarget) < this.amountNeeded) {
               boolean crafted = com.aicompanion.util.EmergencyCrafting.craftOnce(this.companion, Items.GLOWSTONE);
               if (!crafted) break;
            }
         }

         int var1 = InventoryRequirementTracker.countMiningTarget(this.companion, this.miningTarget);
         if (this.startInventoryCount < 0) {
            this.startInventoryCount = Math.max(0, var1 - this.obtainedCount);
         }

         int var2 = Math.max(this.obtainedCount, Math.max(0, var1 - this.startInventoryCount));
         if (var2 > this.obtainedCount) {
            this.obtainedCount = var2;
         }
         if (var2 >= this.amountNeeded) {
            if (this.lastBrokenPos != null) {
               List varDrops = this.dropTracker.scanDrops(this.companion, this.lastBrokenPos, this.miningTarget::isTargetDrop);
               if (!varDrops.isEmpty()) {
                  this.dropTracker.tickCollection(this.companion, this.lastBrokenPos, this.miningTarget::isTargetDrop);
                  this.stage = "COLLECTING_DROPS";
                  this.substate = "Recogiendo mineral restante del suelo...";
                  this.companion.setCurrentState(CompanionState.NAVIGATING);
                  return;
               }
            }
            this.complete = true;
            this.stage = "COMPLETE";
            this.substate = "Completado (" + var2 + "/" + this.amountNeeded + ")";
            this.companion.getNavManager().stop();
            this.companion.addMiningXP(this.amountNeeded * 5);
            this.companion.incrementBlocksMined();
            this.companion.incrementTasksCompleted();
            Player var3 = this.companion.getOwnerPlayer();
            if (var3 != null && var3.isAlive() && !var3.isDeadOrDying()) {
               var3.sendSystemMessage(
                  Component.literal("[" + this.companion.getCustomCompanionName() + "] ")
                     .withStyle(ChatFormatting.GOLD)
                     .append(
                        Component.literal("¡Minería de " + this.miningTarget.getName() + " completada con éxito (" + var2 + "/" + this.amountNeeded + ")!")
                           .withStyle(ChatFormatting.GREEN)
                     )
               );
            }
         } else if (!this.hasInventorySpace()) {
            int freed = this.companion.getInventoryHandler().freeSpaceForMission(this.companion, this.miningTarget::isTargetDrop);
            if (freed > 0 || this.hasInventorySpace()) {
               return;
            }
            this.companion.setCurrentState(CompanionState.INVENTORY_FULL);
            this.stage = "INVENTORY_FULL";
            this.substate = "Inventario lleno";
            this.companion.getNavManager().stop();
            Player var14 = this.companion.getOwnerPlayer();
            if (this.companion.tickCount % 1200 == 0 && var14 != null && var14.isAlive() && !var14.isDeadOrDying()) {
               if (com.aicompanion.AICompanionConfig.COMMON.showInventoryManagementAlerts.get()) {
                  var14.sendSystemMessage(
                     Component.literal("[" + this.companion.getCustomCompanionName() + "] ")
                        .withStyle(ChatFormatting.RED)
                        .append(Component.literal("Mi inventario está lleno y no pude liberar más espacio de forma segura.").withStyle(ChatFormatting.WHITE))
                  );
               }
            }
         } else {
            BlockState var15 = this.miningTarget.getPrimaryBlock().defaultBlockState();
            boolean var4 = ToolSelectionPolicy.ensureToolForBlock(this.companion, var15);
            if (!var4 && var15.requiresCorrectToolForDrops()) {
               int var18 = RecipeDependencyGraph.getRequiredPickaxeTier(this.miningTarget);
               int var20 = RecipeDependencyGraph.getHighestOwnedPickaxeTier(this.companion);
               List var23 = PrerequisitePlanner.resolvePickaxePrerequisites(this.companion, var20, var18);
               if (!var23.isEmpty() && this.companion.getCompanionBrain() != null && this.companion.getCompanionBrain().getTaskManager() != null) {
                  this.companion.getCompanionBrain().getTaskManager().pushSubtasks(var23);
               } else {
                  this.fail("No tengo una herramienta adecuada en el inventario para minar " + this.miningTarget.getName() + ".");
                  this.companion.getNavManager().stop();
               }
            } else {
               if (this.dropTracker.isBusyCollecting()) {
                  this.stage = "COLLECTING_DROPS";
                  this.substate = "Recogiendo mineral del suelo...";
                  this.companion.setCurrentState(CompanionState.NAVIGATING);
                  DropCollectionResult var5 = this.dropTracker.tickCollection(this.companion, this.lastBrokenPos, this.miningTarget::isTargetDrop);
                  if (var5 == DropCollectionResult.INVENTORY_FULL) {
                     int freed = this.companion.getInventoryHandler().freeSpaceForMission(this.companion, this.miningTarget::isTargetDrop);
                     if (freed > 0 || this.hasInventorySpace()) {
                        return;
                     }
                     this.stage = "INVENTORY_FULL";
                     this.substate = "Inventario lleno";
                     this.companion.setCurrentState(CompanionState.INVENTORY_FULL);
                     return;
                  }

                  if (var5 != DropCollectionResult.COMPLETE && var5 != DropCollectionResult.NO_DROPS_FOUND && var5 != DropCollectionResult.DROP_UNREACHABLE) {
                     return;
                  }
               }

               if (this.lastBrokenPos != null) {
                  this.dropPhaseTicks++;
                  List var16 = this.dropPhaseTicks <= 240
                     ? this.dropTracker.scanDrops(this.companion, this.lastBrokenPos, this.miningTarget::isTargetDrop)
                     : Collections.emptyList();
                  if (!var16.isEmpty()) {
                     if (!this.dropTracker.isBusyCollecting()) {
                        this.dropTracker.startGracePeriod(0);
                     }

                     this.dropTracker.tickCollection(this.companion, this.lastBrokenPos, this.miningTarget::isTargetDrop);
                     this.stage = "COLLECTING_DROPS";
                     this.substate = "Recogiendo " + this.miningTarget.getName() + " del suelo...";
                     this.companion.setCurrentState(CompanionState.NAVIGATING);
                     return;
                  } else if (this.dropPhaseTicks < 6) {
                     return;
                  }

                  this.lastBrokenPos = null;
                  this.dropPhaseTicks = 0;
               }

               if (this.companion.level() instanceof ServerLevel var17) {
                  if (this.descendingMode) {
                     this.tickDescent(var17);
                  } else {
                     ShortTermMemory var19 = this.companion.getMemory().getShortTermMemory();
                     if (this.currentTargetBlock == null || !this.miningTarget.matches(var17.getBlockState(this.currentTargetBlock))) {
                        this.currentTargetBlock = null;
                        if (var19.hasLockedCluster() && "ORE".equals(var19.getActiveClusterType())) {
                           while (var19.hasLockedCluster()) {
                              BlockPos var7 = var19.popNextClusterBlock();
                              if (var17.isLoaded(var7) && this.miningTarget.matches(var17.getBlockState(var7))) {
                                 if (this.companion.getCompanionBrain() != null && this.companion.getCompanionBrain().isNearDangerZone(var7, 16.0, 1)) {
                                    this.searchManager.blacklistBlock(var7);
                                    var19.clearLockedCluster();
                                    this.searchManager.setInhibited(false);
                                    break;
                                 }
                                 this.currentTargetBlock = var7;
                                 break;
                              }
                           }
                        }

                        if (this.currentTargetBlock != null) {
                           AICompanionMod.LOGGER.info("[ESCLAVO-SPATIAL] MineTask: Continuing locked ore vein, next block at {}", this.currentTargetBlock);
                        } else {
                           var19.clearLockedCluster();
                           this.searchManager.setInhibited(false);
                           this.stage = "SEARCHING";
                           this.substate = "Buscando " + this.miningTarget.getName() + " (radio " + this.searchManager.getCurrentRadius() + "m)";
                           if (this.searchCooldown <= 0) {
                              BlockPos var21 = null;
                              var rememberedOpt = this.companion.getMemory().getWorldModel().findClosestResourceDeposit(this.miningTarget.getPrimaryBlock(), this.companion.blockPosition(), 64.0);
                              if (rememberedOpt.isPresent()) {
                                 BlockPos rPos = rememberedOpt.get();
                                 boolean isBlacklisted = this.searchManager.isBlacklisted(rPos);
                                 boolean isDanger = this.companion.getCompanionBrain() != null && 
                                     (this.companion.getCompanionBrain().isNearDangerZone(rPos, 20.0, 1) ||
                                      this.companion.getCompanionBrain().isPathNearDangerZone(this.companion.blockPosition(), rPos, 16.0, 1));
                                 boolean isAccessible = SearchRegionManager.isPhysicallyAccessible(var17, rPos, this.companion.blockPosition());
                                 if (!isBlacklisted && !isDanger && isAccessible && var17.isLoaded(rPos) && this.miningTarget.matches(var17.getBlockState(rPos)) && !com.aicompanion.ai.coordination.CompanionCoordinator.isBlockClaimedByOther(this.companion.getUUID(), rPos, var17.getGameTime())) {
                                    var21 = rPos;
                                    AICompanionMod.LOGGER.info("[ESCLAVO-SPATIAL] MineTask: Reusing remembered resource deposit at {}", var21);
                                 } else {
                                    this.companion.getMemory().getWorldModel().removeResourceDeposit(rPos);
                                 }
                              }
                              if (var21 == null) {
                                 var21 = this.searchManager.findTargetBlock(var17, this.companion.blockPosition(), this.miningTarget, this.companion);
                                 if (var21 != null && this.companion.getCompanionBrain() != null && 
                                     (this.companion.getCompanionBrain().isNearDangerZone(var21, 20.0, 1) ||
                                      this.companion.getCompanionBrain().isPathNearDangerZone(this.companion.blockPosition(), var21, 16.0, 1))) {
                                    this.searchManager.blacklistBlock(var21);
                                    this.searchManager.blacklistArea(var21, 24.0, var17.getGameTime(), 2400L);
                                    var21 = null;
                                 }
                              }
                              this.searchCooldown = 15;
                              if (var21 != null && !com.aicompanion.ai.coordination.CompanionCoordinator.isBlockClaimedByOther(this.companion.getUUID(), var21, var17.getGameTime())) {
                                 this.searchAttempts = 0;
                                 this.markProgress();
                                 List var8 = SpatialClusterManager.scanOreVeinCluster(var17, var21, this.miningTarget::matches, this.companion.blockPosition());
                                 this.companion.getMemory().getWorldModel().rememberResourceDeposit(var21, var17.getBlockState(var21).getBlock(), var8.size(), var17.getGameTime());
                                 var19.lockCluster("ORE", var21, var8);
                                 this.searchManager.setInhibited(true);
                                 this.currentTargetBlock = var19.popNextClusterBlock();
                                 if (this.currentTargetBlock != null) {
                                    com.aicompanion.ai.coordination.CompanionCoordinator.claimBlock(this.companion.getUUID(), this.currentTargetBlock, var17.getGameTime());
                                 }
                                 AICompanionMod.LOGGER
                                    .info("[ESCLAVO-SPATIAL] MineTask: Target Lock engaged on ore vein with {} blocks at {}", var8.size(), var21);
                              } else {
                                 this.searchAttempts++;
                                 if (this.searchAttempts >= 10 || (this.consecutiveFlees > 0 && this.searchAttempts >= 4)) {
                                    if (SearchRegionManager.isTargetTooDeep(this.companion.blockPosition(), this.miningTarget) || this.consecutiveFlees > 0 || this.searchAttempts >= 12) {
                                       AICompanionMod.LOGGER.info("[MineTask] Terreno superficial/cueva inseguro o agotado tras {} intentos. Iniciando descenso minero protegido.", this.searchAttempts);
                                       this.initiateDescent(var17);
                                       return;
                                    }

                                    this.fail("No se encontraron vetas de " + this.miningTarget.getName() + " accesibles en este terreno (radio 48m).");
                                    this.companion.getNavManager().stop();
                                    return;
                                 }
                              }
                           } else {
                              this.searchCooldown--;
                           }

                           if (this.currentTargetBlock == null) {
                              this.companion.setCurrentState(CompanionState.SEARCHING);
                              if (this.companion.getNavManager().isDone() || --this.navRepathCooldown <= 0) {
                                 this.navRepathCooldown = 160;
                                 this.companion.getNavManager().navigateTo(new GoalExploreRegion(this.companion.blockPosition(), 32.0), MovementPolicy.sprint());
                              }

                              return;
                           }
                        }
                     }

                     if (this.currentTargetBlock != null && this.companion.getCompanionBrain() != null && 
                         (this.companion.getCompanionBrain().isNearDangerZone(this.currentTargetBlock, 20.0, 1) ||
                          this.companion.getCompanionBrain().isPathNearDangerZone(this.companion.blockPosition(), this.currentTargetBlock, 16.0, 1))) {
                        AICompanionMod.LOGGER.warn("[ESCLAVO-SAFETY] MineTask: target block {} or path to it is near active danger zone. Aborting cluster to seek safer deposit.", this.currentTargetBlock);
                        this.searchManager.blacklistBlock(this.currentTargetBlock);
                        this.searchManager.blacklistArea(this.currentTargetBlock, 24.0, var17.getGameTime(), 2400L);
                        this.currentTargetBlock = null;
                        var19.clearLockedCluster();
                        this.searchManager.setInhibited(false);
                        this.stage = "SEARCHING";
                        this.substate = "Buscando veta segura de " + this.miningTarget.getName() + "...";
                        return;
                     }

                     double var22 = this.companion.blockPosition().distSqr(this.currentTargetBlock);
                     if (var22 > 11.0) {
                        if (this.navTargetPos == null || !this.navTargetPos.equals(this.currentTargetBlock)) {
                           this.navTargetPos = this.currentTargetBlock;
                           this.navStuckTicks = 0;
                           this.lastNavDistSq = var22;
                           this.consecutiveNavStuckFailures = 0;
                        }

                        boolean makingProgress = this.companion.getNavManager().isMakingProgress();
                        if (makingProgress || var22 < this.lastNavDistSq - 0.75) {
                           if (var22 < this.lastNavDistSq - 0.75) {
                              this.lastNavDistSq = var22;
                           }
                           this.navStuckTicks = 0;
                           this.consecutiveNavStuckFailures = 0;
                           this.markProgress();
                        } else {
                           this.navStuckTicks++;
                        }

                        // Watchdog: only trigger when truly stationary/idle for an extended period
                        if (this.navStuckTicks >= 160 || (this.navStuckTicks >= 80 && this.companion.getNavManager().isStuck())) {
                           if (this.consecutiveNavStuckFailures < 2) {
                              this.consecutiveNavStuckFailures++;
                              this.navStuckTicks = 0;
                              AICompanionMod.LOGGER.info("[Esclavo] MineTask: Navigation retry {} for target at {}. Recomputing 3D path.",
                                 this.consecutiveNavStuckFailures, this.currentTargetBlock);
                              this.companion.getNavManager().recomputePath();
                              return;
                           }
                           AICompanionMod.LOGGER.warn("[Esclavo] MineTask: Navigation to target at {} stuck for {} ticks (distSq={}). Target is unreachable, switching.",
                              this.currentTargetBlock, this.navStuckTicks, var22);
                           this.consecutiveNavStuckFailures = 0;
                           this.onStuckTargetUnreachable();
                           return;
                        }

                        // Close-range obstruction: break immediate blocks in front only when close and not making progress
                        if (this.navStuckTicks >= 20 && !makingProgress) {
                           BlockPos obsPos = this.findObstruction(var17, this.currentTargetBlock);
                           if (obsPos != null) {
                              double obsDistSq = this.companion.blockPosition().distSqr(obsPos);
                              if (obsDistSq <= 20.25) {
                                 if (this.tryBreakObstruction(var17, obsPos, var22)) {
                                    return;
                                 }
                              }
                           }
                        }

                        this.stage = "NAVIGATING";
                        this.substate = "Aproximándose al mineral (" + (int)Math.sqrt(var22) + "m)";
                        this.companion.setCurrentState(CompanionState.NAVIGATING);
                        if (--this.navRepathCooldown <= 0 || this.companion.getNavManager().isDone()) {
                           GoalInteractBlock var9 = new GoalInteractBlock(this.currentTargetBlock, 3.2);
                           this.companion.getNavManager().navigateTo(var9, MovementPolicy.sprint());
                           this.navRepathCooldown = 20;
                        }
                     } else {
                        this.navStuckTicks = 0;
                        this.navTargetPos = null;
                        this.lastNavDistSq = Double.MAX_VALUE;

                        BlockPos var24 = this.findObstruction(var17, this.currentTargetBlock);
                        if (var24 != null) {
                           if (this.tryBreakObstruction(var17, var24, var22)) {
                              return;
                           } else {
                              this.losFailures++;
                              if (this.losFailures > 3) {
                                 this.losFailures = 0;
                                 this.onStuckTargetUnreachable();
                              } else {
                                 this.companion.getNavManager().navigateTo(new GoalNear(var24, 2), MovementPolicy.sprint());
                              }
                              return;
                           }
                        }

                        this.losFailures = 0;
                        this.stage = "MINING";
                        this.substate = "Extrayendo " + this.miningTarget.getName();
                        this.companion.getNavManager().stop();
                        this.companion.setCurrentState(CompanionState.MINING);
                        BlockState var10 = var17.getBlockState(this.currentTargetBlock);
                        if (var10.isAir()) {
                           this.currentTargetBlock = null;
                           this.miningTicks = 0;
                           return;
                        }

                        ToolSelectionPolicy.ensureToolForBlock(this.companion, var10);
                        int var11 = BlockBreakHelper.calculateBreakTicks(this.companion, this.currentTargetBlock, var10);
                        if (var11 <= 0) {
                           this.currentTargetBlock = null;
                           this.miningTicks = 0;
                           return;
                        }

                        this.miningTicks++;
                        boolean var12 = BlockBreakHelper.tickBreaking(this.companion, var17, this.currentTargetBlock, this.miningTicks, var11);
                        if (var12) {
                           this.lastBrokenPos = this.currentTargetBlock;
                           this.dropPhaseTicks = 0;
                           boolean var13 = BlockBreakHelper.finishBreaking(this.companion, var17, this.currentTargetBlock);
                           if (var13) {
                              this.consecutiveFlees = 0;
                              this.dropTracker.startGracePeriod(4);
                              this.markProgress();
                           }

                           this.miningTicks = 0;
                           this.currentTargetBlock = null;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean tryBreakObstruction(ServerLevel level, BlockPos obsPos, double targetDistSq) {
      if (obsPos == null) return false;
      double obsDistSq = this.companion.blockPosition().distSqr(obsPos);
      if (obsDistSq > 20.25) {
         return false;
      }

      BlockState obsState = level.getBlockState(obsPos);
      boolean canBreak = this.companion.getSettings().isAllowBlockBreaking()
         && (this.miningTarget.matches(obsState) || TerrainPolicy.mayBreakImplicitly(level, obsPos, obsState, this.companion.getTemporaryBlocks()));

      if (!canBreak) {
         return false;
      }

      this.stage = "MINING";
      int distM = (int)Math.sqrt(targetDistSq);
      this.substate = "Abriendo paso hacia " + this.miningTarget.getName() + (distM > 3 ? " (" + distM + "m)" : "");
      this.companion.getNavManager().stop();
      this.companion.setCurrentState(CompanionState.MINING);

      if (!obsPos.equals(this.obstructionPos)) {
         if (this.obstructionPos != null) {
            BlockBreakHelper.cancelBreaking(this.companion, level, this.obstructionPos);
         }
         this.obstructionPos = obsPos;
         this.obstructionTicks = 0;
      }

      ToolSelectionPolicy.ensureToolForBlock(this.companion, obsState);
      int breakTicks = BlockBreakHelper.calculateBreakTicks(this.companion, obsPos, obsState);
      if (breakTicks <= 0) {
         this.onStuckTargetUnreachable();
         return true;
      }

      this.obstructionTicks++;
      if (BlockBreakHelper.tickBreaking(this.companion, level, obsPos, this.obstructionTicks, breakTicks)) {
         BlockBreakHelper.finishBreaking(this.companion, level, obsPos);
         this.obstructionPos = null;
         this.obstructionTicks = 0;
         this.navStuckTicks = 0;
         this.markProgress();
      }
      return true;
   }

   private BlockPos findObstruction(ServerLevel var1, BlockPos var2) {
      Vec3 eye = this.companion.getEyePosition();
      Vec3 target = Vec3.atCenterOf(var2);
      BlockHitResult hitEye = var1.clip(new ClipContext(eye, target, Block.COLLIDER, Fluid.NONE, this.companion));
      if (hitEye.getType() == Type.BLOCK && !hitEye.getBlockPos().equals(var2)) {
         BlockPos pos = hitEye.getBlockPos();
         if (!var1.getBlockState(pos).isAir()) {
            return pos;
         }
      }

      Vec3 body = this.companion.position().add(0.0, 0.6, 0.0);
      BlockHitResult hitBody = var1.clip(new ClipContext(body, target, Block.COLLIDER, Fluid.NONE, this.companion));
      if (hitBody.getType() == Type.BLOCK && !hitBody.getBlockPos().equals(var2)) {
         BlockPos pos = hitBody.getBlockPos();
         if (!var1.getBlockState(pos).isAir()) {
            return pos;
         }
      }

      return null;
   }

   private boolean hasInventorySpace() {
      CompanionInventoryHandler var1 = this.companion.getInventoryHandler();

      for (int var2 = 0; var2 < 36; var2++) {
         ItemStack var3 = var1.getStackInSlot(var2);
         if (var3.isEmpty() || (var3.getCount() < var3.getMaxStackSize() && this.miningTarget.isTargetDrop(var3))) {
            return true;
         }
      }

      // Try freeing space by discarding trash/low-value items
      int freed = var1.freeSpaceForMission(this.companion, this.miningTarget::isTargetDrop);
      if (freed > 0) {
         for (int var2 = 0; var2 < 36; var2++) {
            ItemStack var3 = var1.getStackInSlot(var2);
            if (var3.isEmpty() || (var3.getCount() < var3.getMaxStackSize() && this.miningTarget.isTargetDrop(var3))) {
               return true;
            }
         }
      }

      return false;
   }

   @Override
   public void onStuckTargetUnreachable() {
      if (this.currentTargetBlock != null) {
         long gameTime = this.companion.level() != null ? this.companion.level().getGameTime() : 0L;
         this.searchManager.blacklistBlock(this.currentTargetBlock, 600L, gameTime);
         if (this.companion.getMemory() != null) {
            com.aicompanion.ai.memory.ShortTermMemory stm = this.companion.getMemory().getShortTermMemory();
            if (stm.getActiveClusterOrigin() != null) {
               this.searchManager.blacklistBlock(stm.getActiveClusterOrigin(), 600L, gameTime);
            }
            for (BlockPos p : stm.getLockedCluster()) {
               this.searchManager.blacklistBlock(p, 600L, gameTime);
            }
            stm.clearLockedCluster();
         }
         this.searchManager.setInhibited(false);

         AICompanionMod.LOGGER.info("[Esclavo] MineTask: Blacklisted unreachable target {} and cleared vein cluster.", this.currentTargetBlock);
         if (this.companion.level() instanceof ServerLevel var1) {
            BlockBreakHelper.cancelBreaking(this.companion, var1, this.currentTargetBlock);
            if (this.obstructionPos != null) {
               BlockBreakHelper.cancelBreaking(this.companion, var1, this.obstructionPos);
            }
         }

         this.currentTargetBlock = null;
         this.obstructionPos = null;
         this.obstructionTicks = 0;
         this.miningTicks = 0;
         this.navRepathCooldown = 0;
         this.navStuckTicks = 0;
         this.navTargetPos = null;
         this.lastNavDistSq = Double.MAX_VALUE;
         this.stage = "SEARCHING";
         this.substate = "Buscando veta alternativa de " + this.miningTarget.getName();
      }
   }

   private void initiateDescent(ServerLevel var1) {
      this.descendingMode = true;
      int targetY;
      if (var1.dimension().equals(net.minecraft.world.level.Level.NETHER)) {
          if (this.miningTarget != null && (this.miningTarget.getName().toLowerCase().contains("netherite") 
                  || this.miningTarget.getName().toLowerCase().contains("debris"))) {
              targetY = 15; // Optimal ancient debris layer in the Nether (Y=15)
          } else if (this.miningTarget != null) {
              int mid = (this.miningTarget.getMinRecommendedY() + this.miningTarget.getMaxRecommendedY()) / 2;
              targetY = Math.max(var1.getMinBuildHeight() + 10, Math.min(mid, 115));
          } else {
              targetY = 15;
          }
      } else {
          if (this.miningTarget != null && this.miningTarget.getName().toLowerCase().contains("diamond")) {
              targetY = -53; // Optimal diamond layer in the Overworld (Y=-53)
          } else if (this.miningTarget != null) {
              int mid = (this.miningTarget.getMinRecommendedY() + this.miningTarget.getMaxRecommendedY()) / 2;
              targetY = Math.max(var1.getMinBuildHeight() + 5, Math.min(mid, var1.getMaxBuildHeight() - 10));
          } else {
              targetY = -53;
          }
      }
      this.targetDescentY = targetY;
      Direction safeDir = Direction.NORTH;
      if (this.companion.getCompanionBrain() != null) {
         safeDir = this.companion.getCompanionBrain().getDirectionAwayFromDanger(this.companion.blockPosition());
      } else {
         safeDir = this.companion.getDirection();
      }
      this.staircaseDir = safeDir;
      this.stage = "DESCENDING";
      this.substate = "Excavando escalera minera hacia Y=" + this.targetDescentY;
      this.companion.tellOwner("Iniciando descenso minero hacia Y=" + this.targetDescentY + " para encontrar vetas de " + (this.miningTarget != null ? this.miningTarget.getName() : "mineral") + ".");
      this.searchAttempts = 0;
      this.descentCooldown = 0;
      this.descentTicks = 0;
      this.lastDescentY = this.companion.getBlockY();
      this.lastYProgressTick = 0;
   }

   private void tickDescent(ServerLevel var1) {
      this.descentTicks++;
      int currentY = this.companion.getBlockY();
      if (this.descentTicks % 20 == 0) {
         if (currentY < this.lastDescentY) {
            this.lastDescentY = currentY;
            this.lastYProgressTick = this.descentTicks;
         }
      }

      // Watchdog: If no vertical progress is made for 45s, or total descent exceeds 90s, transition to searching
      if ((this.descentTicks - this.lastYProgressTick) > 20 * 45 || this.descentTicks > 20 * 90) {
         AICompanionMod.LOGGER.warn("[Esclavo] MineTask: Watchdog activado tras {} ticks en descenso sin progreso vertical (Y={}). Transicionando a búsqueda horizontal.",
                 this.descentTicks, currentY);
         this.descendingMode = false;
         this.stage = "SEARCHING";
         this.substate = "Transición a búsqueda horizontal tras descenso";
         this.companion.tellOwner("Descenso minero pausado. Buscando vetas en el nivel actual Y=" + currentY + ".");
         this.searchManager.clearCache();
         return;
      }

      if (currentY <= this.targetDescentY) {
         this.descendingMode = false;
         this.stage = "SEARCHING";
         this.substate = "Buscando vetas en profundidad Y=" + currentY;
         this.companion
            .tellOwner("Llegué a profundidad Y=" + currentY + ". Comenzando búsqueda y extracción de " + this.miningTarget.getName() + ".");
         this.searchManager.clearCache();
      } else {
         if (this.companion.tickCount % 20 == 0) {
            BlockPos var2 = this.searchManager.findTargetBlock(var1, this.companion.blockPosition(), this.miningTarget, this.companion);
            if (var2 != null) {
               this.descendingMode = false;
               this.stage = "SEARCHING";
               this.substate = "Veta localizada durante el descenso";
               this.companion.tellOwner("¡Veta de " + this.miningTarget.getName() + " localizada a profundidad Y=" + var2.getY() + "!");
               return;
            }
         }

         if (--this.descentCooldown <= 0) {
            BlockPos var8 = this.companion.blockPosition();
            BlockPos var3 = var8.relative(this.staircaseDir).below();
            BlockPos var4 = var3.above(2);
            BlockPos var5 = var3.above(1);
            BlockState var6 = var1.getBlockState(var4);
            BlockState var7 = var1.getBlockState(var5);
            if (var6.is(Blocks.LAVA) || var7.is(Blocks.LAVA) || var1.getBlockState(var3).is(Blocks.LAVA)) {
               this.staircaseDir = this.staircaseDir.getClockWise();
               this.descentCooldown = 5;
            } else if (!var6.isAir() && var6.blocksMotion()) {
               if (TerrainPolicy.mayBreakImplicitly(var1, var4, var6, this.companion.getTemporaryBlocks())) {
                  ToolSelectionPolicy.ensureToolForBlock(this.companion, var6);
                  BlockBreakHelper.finishBreaking(this.companion, var1, var4);
                  this.descentCooldown = 4;
               } else {
                  this.staircaseDir = this.staircaseDir.getClockWise();
               }
            } else if (var7.isAir() || !var7.blocksMotion()) {
               this.companion.getNavManager().moveTo(var3.above(), MovementPolicy.walk());
               this.descentCooldown = 12;
            } else if (TerrainPolicy.mayBreakImplicitly(var1, var5, var7, this.companion.getTemporaryBlocks())) {
               ToolSelectionPolicy.ensureToolForBlock(this.companion, var7);
               BlockBreakHelper.finishBreaking(this.companion, var1, var5);
               this.descentCooldown = 4;
            } else {
               this.staircaseDir = this.staircaseDir.getClockWise();
            }
         }
      }
   }

   @Override
   public void onEmergencyFlee(FleeTask fleeTask) {
      this.consecutiveFlees++;
      long gameTime = this.companion.level().getGameTime();
      AICompanionMod.LOGGER.warn("[MineTask] Interrupción de emergencia por huida (huida #{} consecutiva). Descartando y penalizando objetivo actual y zona de peligro.", this.consecutiveFlees);

      if (fleeTask != null && fleeTask.getThreatCenter() != null) {
         this.searchManager.blacklistArea(fleeTask.getThreatCenter(), 32.0, gameTime, 2400L);
      }

      if (this.currentTargetBlock != null) {
         this.searchManager.blacklistBlock(this.currentTargetBlock);
         this.searchManager.blacklistArea(this.currentTargetBlock, 28.0, gameTime, 2400L);
         if (this.companion.level() instanceof ServerLevel sl) {
            BlockBreakHelper.cancelBreaking(this.companion, sl, this.currentTargetBlock);
         }
         this.currentTargetBlock = null;
      }

      if (this.companion.getMemory() != null) {
         ShortTermMemory stm = this.companion.getMemory().getShortTermMemory();
         if (stm != null) {
            if (stm.getActiveClusterOrigin() != null) {
               this.searchManager.blacklistBlock(stm.getActiveClusterOrigin());
               this.searchManager.blacklistArea(stm.getActiveClusterOrigin(), 24.0, gameTime, 2400L);
            }
            for (BlockPos p : stm.getLockedCluster()) {
               this.searchManager.blacklistBlock(p);
            }
            stm.clearLockedCluster();
         }
      }
      this.searchManager.setInhibited(false);
      this.searchCooldown = 20;
      this.navStuckTicks = 0;
      this.navTargetPos = null;
      this.lastNavDistSq = Double.MAX_VALUE;
   }

   @Override
   public void pause() {
      super.pause();
      if (this.currentTargetBlock != null && this.companion.level() instanceof ServerLevel var1) {
         BlockBreakHelper.cancelBreaking(this.companion, var1, this.currentTargetBlock);
      }
      this.companion.getNavManager().stop();
      logEvent("PAUSA_TRABAJO civil=MINE target=" + (this.currentTargetBlock != null ? this.currentTargetBlock.toShortString() : "none"));
   }

   @Override
   public void resume() {
      super.resume();
      this.navRepathCooldown = 0;
      this.miningTicks = 0;
      this.dropPhaseTicks = 0;
      this.navStuckTicks = 0;
      this.obstructionTicks = 0;
      this.obstructionPos = null;
      this.navTargetPos = null;
      this.lastNavDistSq = Double.MAX_VALUE;

      if (!(this.companion.level() instanceof ServerLevel sl)) {
         return;
      }

      // 1. Tool check: Ensure appropriate tool is equipped for the target block
      if (this.miningTarget != null) {
         BlockState targetState = this.miningTarget.getPrimaryBlock().defaultBlockState();
         ToolSelectionPolicy.ensureToolForBlock(this.companion, targetState);
      }

      // 2. Resources check: Are more resources still needed?
      if (this.miningTarget != null && ("Piedra Luminosa".equalsIgnoreCase(this.miningTarget.getName()) || "glowstone".equalsIgnoreCase(this.miningTarget.getQuery()))) {
         while (InventoryRequirementTracker.countItem(this.companion, Items.GLOWSTONE_DUST) >= 4
                 && InventoryRequirementTracker.countMiningTarget(this.companion, this.miningTarget) < this.amountNeeded) {
            boolean crafted = com.aicompanion.util.EmergencyCrafting.craftOnce(this.companion, Items.GLOWSTONE);
            if (!crafted) break;
         }
      }
      int currentCount = InventoryRequirementTracker.countMiningTarget(this.companion, this.miningTarget);
      if (this.startInventoryCount < 0) {
         this.startInventoryCount = Math.max(0, currentCount - this.obtainedCount);
      }
      int progress = Math.max(this.obtainedCount, Math.max(0, currentCount - this.startInventoryCount));
      if (progress >= this.amountNeeded) {
         AICompanionMod.LOGGER.info("[ESCLAVO-RESUME] Misión de minería ya alcanzada ({}/{}). Marcando completada.", progress, this.amountNeeded);
         this.complete = true;
         this.stage = "COMPLETE";
         this.substate = "Completado (" + progress + "/" + this.amountNeeded + ")";
         return;
      }

      // If we had consecutive flees, force clear target and if repeated, switch to safe descent digging away from danger
      if (this.consecutiveFlees > 0) {
         this.currentTargetBlock = null;
         if (this.companion.getMemory() != null) {
            this.companion.getMemory().getShortTermMemory().clearLockedCluster();
         }
         this.searchManager.setInhibited(false);
         if (this.consecutiveFlees >= 2 && !this.descendingMode) {
            AICompanionMod.LOGGER.warn("[ESCLAVO-RESUME] Múltiples huidas por peligro ({}) en la zona. Cambiando a descenso protegido/escalera minera lejos del peligro.", this.consecutiveFlees);
            this.initiateDescent(sl);
            this.markProgress();
            return;
         }
      }

      // 3. Target Block existence and validity check
      boolean needNewTarget = false;
      if (this.currentTargetBlock == null) {
         needNewTarget = true;
      } else if (!sl.isLoaded(this.currentTargetBlock)) {
         AICompanionMod.LOGGER.info("[ESCLAVO-RESUME] Bloque objetivo {} no está en chunk cargado. Rebuscando.", this.currentTargetBlock);
         needNewTarget = true;
      } else {
         BlockState current = sl.getBlockState(this.currentTargetBlock);
         boolean isDanger = this.companion.getCompanionBrain() != null && 
             (this.companion.getCompanionBrain().isNearDangerZone(this.currentTargetBlock, 20.0, 1) ||
              this.companion.getCompanionBrain().isPathNearDangerZone(this.companion.blockPosition(), this.currentTargetBlock, 16.0, 1));
         if (isDanger || current.isAir() || !this.miningTarget.matches(current)) {
            if (isDanger) {
               this.searchManager.blacklistBlock(this.currentTargetBlock);
               this.searchManager.blacklistArea(this.currentTargetBlock, 24.0, sl.getGameTime(), 2400L);
               if (this.companion.getMemory() != null) {
                  this.companion.getMemory().getShortTermMemory().clearLockedCluster();
               }
               this.searchManager.setInhibited(false);
            }
            AICompanionMod.LOGGER.info("[ESCLAVO-RESUME] Bloque objetivo {} ya no es válido (estado={}, peligro={}). Rebuscando.", this.currentTargetBlock, current, isDanger);
            needNewTarget = true;
         }
      }

      if (needNewTarget) {
         this.currentTargetBlock = null;
         this.stage = "SEARCHING";
         this.substate = "Reanudando búsqueda de " + this.miningTarget.getName();
         this.companion.setCurrentState(CompanionState.SEARCHING);
      } else {
         // Target block still exists! Reconstruct navigation path
         this.stage = "NAVIGATING";
         this.substate = "Reanudando aproximación a " + this.miningTarget.getName();
         this.companion.setCurrentState(CompanionState.NAVIGATING);
         this.companion.getNavManager().navigateTo(new GoalInteractBlock(this.currentTargetBlock, 2.0), MovementPolicy.sprint());
         AICompanionMod.LOGGER.info("[ESCLAVO-NAV] Reconstruyendo navegación hacia {}", this.currentTargetBlock);
      }

      this.markProgress();
      logEvent("REANUDAR_TRABAJO civil=MINE target=" + (this.currentTargetBlock != null ? this.currentTargetBlock.toShortString() : "none"));
   }

   @Override
   public void stop() {
      if (this.companion.getMemory() != null) {
         this.companion.getMemory().getShortTermMemory().clearLockedCluster();
      }

      this.searchManager.setInhibited(false);
      if (this.currentTargetBlock != null && this.companion.level() instanceof ServerLevel var1) {
         BlockBreakHelper.cancelBreaking(this.companion, var1, this.currentTargetBlock);
      }

      this.companion.getNavManager().stop();
      com.aicompanion.ai.coordination.CompanionCoordinator.releaseAllBlocks(this.companion.getUUID());
   }

   @Override
   public String getDescription() {
      return "Minar " + this.amountNeeded + " de " + this.miningTarget.getName();
   }

   @Override
   public String getProgress() {
      int var1 = InventoryRequirementTracker.countMiningTarget(this.companion, this.miningTarget);
      int var2 = this.startInventoryCount >= 0 ? Math.max(this.obtainedCount, Math.max(0, var1 - this.startInventoryCount)) : this.obtainedCount;
      return var2 + "/" + this.amountNeeded + " (" + this.stage + ")";
   }

   @Override
   public String getTaskType() {
      return "MINE";
   }

   public MiningTarget getMiningTarget() {
      return this.miningTarget;
   }

   @Override
   public CompoundTag save() {
      CompoundTag var1 = new CompoundTag();
      var1.putString("Target", this.miningTarget.getQuery() != null ? this.miningTarget.getQuery() : this.miningTarget.getName());
      var1.putInt("AmountNeeded", this.amountNeeded);
      var1.putInt("StartInventoryCount", this.startInventoryCount);
      var1.putInt("ObtainedCount", this.obtainedCount);
      var1.putInt("ConsecutiveFlees", this.consecutiveFlees);
      var1.putBoolean("DescendingMode", this.descendingMode);
      var1.putInt("TargetDescentY", this.targetDescentY);
      var1.putString("StaircaseDir", this.staircaseDir.getName());
      if (this.currentTargetBlock != null) {
         var1.put("TargetBlock", NbtUtils.writeBlockPos(this.currentTargetBlock));
      }

      return var1;
   }

   @Override
   public void load(CompoundTag var1) {
      if (var1.contains("Target")) {
         this.miningTarget = MiningTarget.of(var1.getString("Target"));
      }

      this.amountNeeded = var1.getInt("AmountNeeded");
      if (var1.contains("StartInventoryCount")) {
         this.startInventoryCount = var1.getInt("StartInventoryCount");
      }
      if (var1.contains("ObtainedCount")) {
         this.obtainedCount = var1.getInt("ObtainedCount");
      }
      if (var1.contains("ConsecutiveFlees")) {
         this.consecutiveFlees = var1.getInt("ConsecutiveFlees");
      }

      if (var1.contains("DescendingMode")) {
         this.descendingMode = var1.getBoolean("DescendingMode");
      }

      if (var1.contains("TargetDescentY")) {
         this.targetDescentY = var1.getInt("TargetDescentY");
      }

      if (var1.contains("StaircaseDir")) {
         this.staircaseDir = Direction.byName(var1.getString("StaircaseDir"));
      }

      if (this.staircaseDir == null) {
         this.staircaseDir = Direction.NORTH;
      }

      if (var1.contains("TargetBlock")) {
         this.currentTargetBlock = NbtUtils.readBlockPos(var1.getCompound("TargetBlock"));
      }
   }
}
