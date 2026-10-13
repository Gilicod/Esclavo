package com.aicompanion.ai.nav;

import com.aicompanion.AICompanionConfig;
import com.aicompanion.debug.EsclavoTrace;
import com.aicompanion.entity.CompanionEntity;
import com.aicompanion.internal.baritone.goals.Goal;
import com.aicompanion.internal.baritone.goals.GoalEntity;
import com.aicompanion.internal.baritone.path.BaritonePathAdapter;
import com.aicompanion.internal.baritone.path.Path;
import com.aicompanion.internal.baritone.path.PathNode;
import com.aicompanion.internal.baritone.path.PathResult;
import com.aicompanion.internal.baritone.planner.InternalBaritonePlanner;
import com.aicompanion.internal.baritone.v120.NpcPathSearch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class InternalBaritoneNavigationProvider implements NavigationProvider {
   private static final int MIN_NODES = 4000;
   private static final long MIN_TIME_MS = 25L;
   private static final int EXTEND_WHEN_ACTIONS_LEFT = 8;
   private static final int AVOID_DURATION_TICKS = 600;
   private static final int MAX_FAILURES = 4;
   private final CompanionEntity companion;
   private final CompanionActionExecutor executor;
   private Goal currentGoal = null;
   private Path currentPath = null;
   private double currentSpeed = 1.0;
   private NavigationRequestResult status = NavigationRequestResult.STARTED;
   private int replanCooldown = 0;
   private int consecutiveFailures = 0;
   private BlockPos lastEntityPosWhenPlanned = null;
   private boolean currentPathPartial = false;
   private boolean extensionTried = false;
   private final Map<Long, Integer> avoidUntil = new HashMap<>();
   private final Map<Long, Integer> avoidHits = new HashMap<>();
   private boolean stuckHandled = false;

   public InternalBaritoneNavigationProvider(CompanionEntity var1) {
      this.companion = var1;
      this.executor = new CompanionActionExecutor(var1);
   }

   @Override
   public boolean navigateTo(Goal var1, double var2) {
      if (var1 != null && this.companion != null) {
         this.currentGoal = var1;
         this.currentSpeed = var2;
         this.consecutiveFailures = 0;
         BlockPos var4 = this.companion.blockPosition();
         if (var1.isInGoal(var4.getX(), var4.getY(), var4.getZ())) {
            this.status = NavigationRequestResult.ALREADY_REACHED;
            this.executor.clear();
            return true;
         } else {
            return this.planAndExecute("nueva meta");
         }
      } else {
         this.status = NavigationRequestResult.INVALID_TARGET;
         return false;
      }
   }

   private int now() {
      return this.companion.tickCount;
   }

   private void pruneAvoid() {
      if (!this.avoidUntil.isEmpty()) {
         int var1 = this.now();
         Iterator var2 = this.avoidUntil.entrySet().iterator();

         while (var2.hasNext()) {
            Entry var3 = (Entry)var2.next();
            if ((Integer)var3.getValue() < var1 || (Integer)var3.getValue() - var1 > 1200) {
               this.avoidHits.remove(var3.getKey());
               var2.remove();
            }
         }
      }
   }

   private void avoid(BlockPos var1) {
      if (var1 != null) {
         int var2 = this.now() + 600;

         for (int var3 = -1; var3 <= 1; var3++) {
            long var4 = NpcPathSearch.key(var1.getX(), var1.getY() + var3, var1.getZ());
            this.avoidUntil.put(var4, var2);
            this.avoidHits.merge(var4, 1, Integer::sum);
         }
      }
   }

   private double avoidCostFor(Map<Long, Integer> var1, long var2) {
      Integer var4 = (Integer)var1.get(var2);
      if (var4 == null) {
         return 0.0;
      } else {
         return var4 >= 3 ? 1000000.0 : 30.0 * var4.intValue();
      }
   }

   private InternalBaritonePlanner createPlanner(ServerLevel var1) {
      boolean var2 = true;
      boolean var3 = true;
      int var4 = 1500;
      long var5 = 15L;

      try {
         if (AICompanionConfig.COMMON != null) {
            if (AICompanionConfig.COMMON.allowBridging != null) {
               var2 = (Boolean)AICompanionConfig.COMMON.allowBridging.get();
            }

            if (AICompanionConfig.COMMON.allowTunneling != null) {
               var3 = (Boolean)AICompanionConfig.COMMON.allowTunneling.get();
            }

            if (AICompanionConfig.COMMON.maxPathNodesPerTick != null) {
               var4 = (Integer)AICompanionConfig.COMMON.maxPathNodesPerTick.get();
            }

            if (AICompanionConfig.COMMON.maxPlanningMillisecondsPerTick != null) {
               var5 = ((Integer)AICompanionConfig.COMMON.maxPlanningMillisecondsPerTick.get()).intValue();
            }
         }
      } catch (Exception var10) {
      }

      var4 = Math.max(var4, 6000);
      var5 = Math.max(var5, 35L);
      this.pruneAvoid();

      InternalBaritonePlanner var7 = new InternalBaritonePlanner(var1, var2, var3, var4, var5, null);
      if (!this.avoidHits.isEmpty()) {
         HashMap var8 = new HashMap<>(this.avoidHits);
         var7.setAvoidCost(var2x -> this.avoidCostFor(var8, var2x));
      }

      return var7;
   }

   private String goalText() {
      try {
         return this.currentGoal != null ? this.currentGoal.getDescription() : "ninguna";
      } catch (Throwable var2) {
         return "?";
      }
   }

   private static String pathSummary(Path var0) {
      if (var0 != null && !var0.isEmpty()) {
         java.util.TreeMap<String, Integer> var1 = new java.util.TreeMap<>();

         for (PathNode var3 : var0.getNodes()) {
            if (var3.movementType != null) {
               var1.merge(var3.movementType.name(), 1, Integer::sum);
            }
         }

         PathNode var4 = var0.getNode(var0.length() - 1);
         return "fin=" + var4.x + "," + var4.y + "," + var4.z + " movs=" + var1;
      } else {
         return "";
      }
   }

   private boolean planAndExecute(String var1) {
      if (this.companion.level() instanceof ServerLevel var2) {
         InternalBaritonePlanner var9 = this.createPlanner(var2);
         BlockPos var4 = this.companion.blockPosition();
         PathResult var5 = var9.plan(var4, this.currentGoal);
         EsclavoTrace.log(
            "RUTA",
            this.companion,
            "motivo='"
               + var1
               + "' meta='"
               + this.goalText()
               + "' resultado="
               + var5.getStatus()
               + " nodos="
               + var5.getNodesExplored()
               + " ms="
               + var5.getExecutionTimeMs()
               + " largo="
               + (var5.hasPath() ? var5.getPath().length() : 0)
               + " evitando="
               + this.avoidUntil.size()
               + " fallosSeguidos="
               + this.consecutiveFailures
               + (var5.hasPath() ? " " + pathSummary(var5.getPath()) : "")
         );
         if (var5.getStatus() == PathResult.Status.FOUND && var5.hasPath() && var5.getPath().length() <= 1) {
            this.currentPath = var5.getPath();
            this.currentPathPartial = false;
            this.executor.clear();
            this.status = NavigationRequestResult.ALREADY_REACHED;
            this.consecutiveFailures = 0;
            return true;
         }

         if (var5.hasPath() && var5.getPath().length() > 1) {
            this.currentPath = var5.getPath();
            this.currentPathPartial = var5.getStatus() != PathResult.Status.FOUND;
            this.extensionTried = false;
            Queue var6 = BaritonePathAdapter.adaptToActions(this.currentPath, this.currentSpeed);
            this.executor.setActions(var6);
            this.stuckHandled = false;
            this.status = NavigationRequestResult.STARTED;
            this.consecutiveFailures = 0;
            if (this.currentGoal instanceof GoalEntity var7 && var7.getTarget() != null) {
               this.lastEntityPosWhenPlanned = var7.getTarget().blockPosition();
            }

            return true;
         } else {
            this.executor.clear();
            this.status = NavigationRequestResult.NO_PATH;
            this.consecutiveFailures++;
            if (this.consecutiveFailures == 4) {
               EsclavoTrace.log("RENDIDO", this.companion, "Sin ruta 4 veces seguidas hacia '" + this.goalText() + "'. Se pasa al navegador de respaldo.");
            }

            return false;
         }
      } else {
         this.status = NavigationRequestResult.FAILED;
         return false;
      }
   }

   private void tryExtendPath() {
      if (this.currentPathPartial && !this.extensionTried && this.currentPath != null && !this.currentPath.isEmpty()) {
         this.extensionTried = true;
         if (this.companion.level() instanceof ServerLevel var1) {
            PathNode var14 = this.currentPath.getNode(this.currentPath.length() - 1);
            if (!this.currentGoal.isInGoal(var14.x, var14.y, var14.z)) {
               InternalBaritonePlanner var3 = this.createPlanner(var1);
               PathResult var4 = var3.plan(var14.getPos(), this.currentGoal);
               if (var4.hasPath() && var4.getPath().length() > 1) {
                  PathNode var5 = var4.getPath().getNode(0);
                  if (var5.x == var14.x && var5.y == var14.y && var5.z == var14.z) {
                     double var6 = this.currentGoal.heuristic(var14.x, var14.y, var14.z);
                     PathNode var8 = var4.getPath().getNode(var4.getPath().length() - 1);
                     double var9 = this.currentGoal.heuristic(var8.x, var8.y, var8.z);
                     boolean var11 = var4.getStatus() == PathResult.Status.FOUND;
                     if (var11 || var9 < var6 - 0.5) {
                        this.executor.appendActions(BaritonePathAdapter.adaptToActions(var4.getPath(), this.currentSpeed));
                        ArrayList var12 = new ArrayList<>(this.currentPath.getNodes());

                        for (int var13 = 1; var13 < var4.getPath().length(); var13++) {
                           var12.add(var4.getPath().getNode(var13));
                        }

                        this.currentPath = new Path(var12);
                        this.currentPathPartial = !var11;
                        this.extensionTried = false;
                        EsclavoTrace.log(
                           "RUTA+",
                           this.companion,
                           "tramo siguiente pre-calculado desde "
                              + var14.x
                              + ","
                              + var14.y
                              + ","
                              + var14.z
                              + " resultado="
                              + var4.getStatus()
                              + " +"
                              + (var4.getPath().length() - 1)
                              + " nodos ms="
                              + var4.getExecutionTimeMs()
                              + " "
                              + pathSummary(var4.getPath())
                        );
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public void tick() {
      if (this.currentGoal != null && this.companion != null) {
         BlockPos var1 = this.companion.blockPosition();
         if (this.currentGoal.isInGoal(var1.getX(), var1.getY(), var1.getZ())) {
            if (this.status != NavigationRequestResult.ALREADY_REACHED) {
               EsclavoTrace.log("LLEGO", this.companion, "meta='" + this.goalText() + "'");
            }

            this.status = NavigationRequestResult.ALREADY_REACHED;
            this.executor.clear();
         } else {
            if (this.replanCooldown > 0) {
               this.replanCooldown--;
            }

            if (this.currentGoal instanceof GoalEntity var2 && var2.getTarget() != null) {
               BlockPos var5 = var2.getTarget().blockPosition();
               if (this.lastEntityPosWhenPlanned != null && var5.distSqr(this.lastEntityPosWhenPlanned) > 9.0 && this.replanCooldown <= 0) {
                  this.planAndExecute("el objetivo se movio");
                  this.replanCooldown = 10;
               }
            }

            boolean var4 = this.executor.tick();
            if (this.executor.isStuck()) {
               this.status = NavigationRequestResult.BLOCKED;
               if (!this.stuckHandled) {
                  this.stuckHandled = true;
                  BlockPos var6 = this.executor.consumeStuckTarget();
                  EsclavoTrace.log(
                     "ATASCO",
                     this.companion,
                     "causa='"
                        + this.executor.consumeStuckReason()
                        + "' nodo="
                        + EsclavoTrace.fmt(var6)
                        + " meta='"
                        + this.goalText()
                        + "' -> se marca para evitar 30 s y se recalcula"
                  );
                  this.avoid(var6);
                  if (this.companion.getStuckDetector() != null) {
                     this.companion.getStuckDetector().notifyNavigationStuck();
                  }
               }

               if (this.replanCooldown <= 0) {
                  this.planAndExecute("atasco");
                  this.replanCooldown = 5;
               }
            } else if (var4 || this.executor.hasActions()) {
               if (this.executor.getRemainingActionsCount() <= 8) {
                  this.tryExtendPath();
               }
            } else if (this.replanCooldown <= 0) {
               boolean var7 = this.planAndExecute(this.currentPathPartial ? "fin de tramo parcial" : "se acabaron las acciones");
               this.replanCooldown = var7 ? (this.currentPathPartial ? 0 : 5) : 5;
            }
         }
      }
   }

   @Override
   public void stop() {
      if (this.currentGoal != null && this.executor.isMakingNoProgress()) {
         NavigationAction var1 = this.executor.getCurrentAction();
         BlockPos var2 = var1 != null
            ? (var1.getTargetBlock() != null ? var1.getTargetBlock() : (var1.getTargetVec() != null ? BlockPos.containing(var1.getTargetVec()) : null))
            : null;
         if (var2 != null) {
            this.avoid(var2);
            EsclavoTrace.log(
               "ATASCO",
               this.companion,
               "detenido desde fuera (anti-atasco/tarea) mientras no avanzaba hacia "
                  + EsclavoTrace.fmt(var2)
                  + " accion="
                  + var1
                  + " -> se marca para evitar 30 s"
            );
         }
      }

      this.currentGoal = null;
      this.currentPath = null;
      this.currentPathPartial = false;
      this.executor.clear();
      this.status = NavigationRequestResult.ALREADY_REACHED;
   }

   @Override
   public boolean isDone() {
      if (this.currentGoal == null) {
         return true;
      }

      BlockPos var1 = this.companion.blockPosition();
      return this.currentGoal.isInGoal(var1.getX(), var1.getY(), var1.getZ())
         || !this.executor.hasActions() && this.status == NavigationRequestResult.ALREADY_REACHED;
   }

   @Override
   public boolean isStuck() {
      return this.consecutiveFailures >= 4;
   }

   @Override
   public boolean isMakingProgress() {
      return this.executor != null && this.executor.isMakingProgress();
   }

   @Override
   public NavigationRequestResult getStatus() {
      return this.status;
   }

   @Override
   public Path getCurrentPath() {
      return this.currentPath;
   }

   @Override
   public String getName() {
      return "Internal Baritone (3D A*)";
   }

   public CompanionActionExecutor getExecutor() {
      return this.executor;
   }
}
