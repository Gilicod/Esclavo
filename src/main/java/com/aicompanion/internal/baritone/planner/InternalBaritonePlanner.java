package com.aicompanion.internal.baritone.planner;

import com.aicompanion.AICompanionConfig;
import com.aicompanion.internal.baritone.costs.HazardCostEvaluator;
import com.aicompanion.internal.baritone.goals.Goal;
import com.aicompanion.internal.baritone.movements.MovementType;
import com.aicompanion.internal.baritone.path.Path;
import com.aicompanion.internal.baritone.path.PathNode;
import com.aicompanion.internal.baritone.path.PathResult;
import com.aicompanion.internal.baritone.v120.NpcPathSearch;
import com.aicompanion.util.TerrainPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.LongPredicate;
import java.util.function.LongToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;

public class InternalBaritonePlanner {
   private final ServerLevel level;
   private final boolean allowBridging;
   private final boolean allowTunneling;
   private final int maxNodes;
   private final long maxTimeMs;
   private final LongPredicate avoid;
   private static final int[][] CARDINALS = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
   private static final int[][] DIAGONALS = new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
   private static final double AVOID_PENALTY = 30.0;
   private static final double DOOR_PENALTY = 1.5;
   private final boolean allowBreaking;
   private double bridgeExtraCost = 0.0;
   private LongToDoubleFunction avoidCost = null;
   private final HashMap<Long, Boolean> breakableCache = new HashMap<>();

   public InternalBaritonePlanner setAvoidCost(LongToDoubleFunction var1) {
      this.avoidCost = var1;
      return this;
   }

   public InternalBaritonePlanner setBridgeExtraCost(double var1) {
      this.bridgeExtraCost = Math.max(0.0, var1);
      return this;
   }

   public InternalBaritonePlanner(ServerLevel var1, boolean var2, boolean var3, int var4, long var5) {
      this(var1, var2, var3, var4, var5, null);
   }

   public InternalBaritonePlanner(ServerLevel var1, boolean var2, boolean var3, int var4, long var5, LongPredicate var7) {
      this.level = var1;
      this.allowBridging = var2;
      this.allowTunneling = var3;
      this.maxNodes = Math.max(200, var4);
      this.maxTimeMs = Math.max(2L, var5);
      this.avoid = var7;
      boolean var8 = true;

      try {
         if (AICompanionConfig.COMMON != null && AICompanionConfig.COMMON.allowBlockBreaking != null) {
            var8 = (Boolean)AICompanionConfig.COMMON.allowBlockBreaking.get();
         }
      } catch (Exception var10) {
      }

      this.allowBreaking = var8;
   }

   public BlockPos normalizeStart(BlockPos var1) {
      if (this.level != null && var1 != null) {
         MutableBlockPos var2 = new MutableBlockPos();
         int var3 = var1.getX();
         int var4 = var1.getY();
         int var5 = var1.getZ();
         if (!this.isPassable(var3, var4, var5, var2) && this.isPassable(var3, var4 + 1, var5, var2)) {
            return new BlockPos(var3, var4 + 1, var5);
         }

         int var6 = 0;

         while (var6 < 4) {
            int var7 = var4 - var6;
            if (this.isPassable(var3, var7, var5, var2)) {
               if (!this.canStandOn(var3, var7 - 1, var5, var2) && !this.isInWater(var3, var7, var5, var2)) {
                  var6++;
                  continue;
               }

               return new BlockPos(var3, var7, var5);
            }
            break;
         }

         return var1;
      } else {
         return var1;
      }
   }

   public PathResult plan(BlockPos var1, Goal var2) {
      if (this.level != null && var1 != null && var2 != null) {
         MutableBlockPos var3 = new MutableBlockPos();
         BlockPos var4 = this.normalizeStart(var1);
         NpcPathSearch.Result var5 = NpcPathSearch.search(
            var4.getX(),
            var4.getY(),
            var4.getZ(),
            var2,
            (var2x, var3x, var4x, var5x) -> {
               PathNode var6x = new PathNode(var2x, var3x, var4x, null, MovementType.TRAVERSE, null, 0.0, 0.0);

               for (int[] var10x : CARDINALS) {
                  this.expandMovements(var6x, var2x + var10x[0], var3x, var4x + var10x[1], var5x, var3, false);
                  this.expandTunnelDown(var6x, var2x + var10x[0], var3x, var4x + var10x[1], var5x, var3);
               }

               this.expandDigDown(var6x, var2x, var3x, var4x, var5x, var3);

               for (int[] var14 : DIAGONALS) {
                  if (this.isClearStrict(var2x + var14[0], var3x, var4x, var3)
                     && this.isClearStrict(var2x + var14[0], var3x + 1, var4x, var3)
                     && this.isClearStrict(var2x, var3x, var4x + var14[1], var3)
                     && this.isClearStrict(var2x, var3x + 1, var4x + var14[1], var3)) {
                     this.expandMovements(var6x, var2x + var14[0], var3x, var4x + var14[1], var5x, var3, true);
                  }
               }
            },
            this.maxNodes,
            this.maxTimeMs
         );
         if (var5.steps().isEmpty()) {
            return PathResult.impossible(var5.nodes(), var5.milliseconds());
         }

         ArrayList var6 = new ArrayList();
         PathNode var7 = null;

         for (NpcPathSearch.Step var9 : var5.steps()) {
            InternalBaritonePlanner.MovementData var10 = var9.movement() instanceof InternalBaritonePlanner.MovementData var11
               ? var11
               : new InternalBaritonePlanner.MovementData(MovementType.TRAVERSE, null);
            PathNode var13 = new PathNode(
               var9.x(), var9.y(), var9.z(), var7, var10.type(), var10.action(), var9.cost(), var2.heuristic(var9.x(), var9.y(), var9.z())
            );
            var6.add(var13);
            var7 = var13;
         }

         return var5.reached()
            ? PathResult.found(new Path(var6), var5.nodes(), var5.milliseconds())
            : PathResult.partial(new Path(var6), var5.nodes(), var5.milliseconds());
      } else {
         return PathResult.impossible(0, 0L);
      }
   }

   private void expandMovements(PathNode var1, int var2, int var3, int var4, NpcPathSearch.Sink var5, MutableBlockPos var6, boolean var7) {
      double var8 = var7 ? 1.4142 : 1.0;
      boolean var10 = this.isPassable(var2, var3, var4, var6);
      boolean var11 = this.isPassable(var2, var3 + 1, var4, var6);
      if (var10 && var11) {
         double var12 = this.doorPenalty(var2, var3, var4, var6) + this.doorPenalty(var2, var3 + 1, var4, var6);
         if (var7 && var12 > 0.0) {
            return;
         }

         if (this.canStandOn(var2, var3 - 1, var4, var6)) {
            this.tryAddNode(var1, var2, var3, var4, MovementType.TRAVERSE, null, var8 + var12 + this.getHazardCost(var2, var3, var4, var6, 0), var5);
         } else if (this.isInWater(var2, var3, var4, var6)) {
            this.tryAddNode(var1, var2, var3, var4, MovementType.SWIM, null, var8 * 1.6 + this.getHazardCost(var2, var3, var4, var6, 0), var5);
         } else if (this.allowBridging && !var7 && var12 == 0.0) {
            var6.set(var2, var3 - 1, var4);
            if (this.canPlaceBlockAt(var6)) {
               this.tryAddNode(
                  var1,
                  var2,
                  var3,
                  var4,
                  MovementType.BRIDGE,
                  var6.immutable(),
                  var8 + 3.0 + this.bridgeExtraCost + this.getHazardCost(var2, var3, var4, var6, 0),
                  var5
               );
            }
         }
      }

      if (!var7) {
         int var20 = var3 + 1;
         if (this.canStandOn(var2, var20 - 1, var4, var6)) {
            boolean var13 = this.isClearStrict(var1.x, var1.y + 2, var1.z, var6);
            boolean var14 = this.isClearStrict(var2, var20, var4, var6);
            boolean var15 = this.isClearStrict(var2, var20 + 1, var4, var6);
            if (var13 && var14 && var15) {
               this.tryAddNode(var1, var2, var20, var4, MovementType.ASCEND, null, var8 + 0.45 + this.getHazardCost(var2, var20, var4, var6, 0), var5);
            } else if (this.allowTunneling) {
               double var16 = 4.5;
               boolean var18 = true;
               var6.set(var1.x, var1.y + 2, var1.z);
               if (!var13 && this.level.isLoaded(var6)) {
                  BlockState var19 = this.level.getBlockState(var6);
                  if (this.isBreakable(var19, var6)) {
                     var16 += Math.min(6.0, var19.getDestroySpeed(this.level, var6) * 1.5);
                  } else {
                     var18 = false;
                  }
               }

               var6.set(var2, var20, var4);
               if (var18 && !var14 && this.level.isLoaded(var6)) {
                  BlockState var30 = this.level.getBlockState(var6);
                  if (this.isBreakable(var30, var6)) {
                     var16 += Math.min(6.0, var30.getDestroySpeed(this.level, var6) * 1.5);
                  } else {
                     var18 = false;
                  }
               }

               var6.set(var2, var20 + 1, var4);
               if (var18 && !var15 && this.level.isLoaded(var6)) {
                  BlockState var31 = this.level.getBlockState(var6);
                  if (this.isBreakable(var31, var6)) {
                     var16 += Math.min(6.0, var31.getDestroySpeed(this.level, var6) * 1.5);
                  } else {
                     var18 = false;
                  }
               }

               if (var18) {
                  this.tryAddNode(
                     var1,
                     var2,
                     var20,
                     var4,
                     MovementType.TUNNEL_UP,
                     new BlockPos(var2, var20, var4),
                     var8 + var16 + this.getHazardCost(var2, var20, var4, var6, 0),
                     var5
                  );
               }
            }
         }
      }

      int var21;
      if (this.isClearStrict(var2, var3, var4, var6) && this.isClearStrict(var2, var3 + 1, var4, var6) && !this.canStandOn(var2, var3 - 1, var4, var6)) {
         for (int var22 = 1; var22 <= 5 && this.isPassable(var2, var21 = var3 - var22, var4, var6); var22++) {
            if (this.canStandOn(var2, var21 - 1, var4, var6)) {
               double var24 = var8 + var22 * 0.25 + this.getHazardCost(var2, var21, var4, var6, var22);
               MovementType var27 = var22 == 1 ? MovementType.DESCEND : MovementType.FALL;
               this.tryAddNode(var1, var2, var21, var4, var27, null, var24, var5);
               break;
            }

            if (this.isInWater(var2, var21, var4, var6)) {
               this.tryAddNode(var1, var2, var21, var4, MovementType.SWIM, null, var8 * 1.5 + this.getHazardCost(var2, var21, var4, var6, var22), var5);
               break;
            }
         }
      }

      if (this.allowTunneling && !var7 && this.level.isLoaded(new BlockPos(var2, var3, var4)) && this.level.isLoaded(new BlockPos(var2, var3 + 1, var4))) {
         var6.set(var2, var3, var4);
         BlockPos var23 = var6.above();
         var6.set(var2, var3 - 1, var4);
         if (this.canStandOn(var2, var3 - 1, var4, var6)) {
            var6.set(var2, var3, var4);
            BlockState var25 = this.level.getBlockState(var6);
            BlockState var26 = this.level.getBlockState(var23);
            boolean var28 = !var25.getCollisionShape(this.level, var6).isEmpty();
            boolean var17 = !var26.getCollisionShape(this.level, var23).isEmpty();
            if ((var28 || var17) && this.isBreakable(var25, var6) && this.isBreakable(var26, var23)) {
               double var29 = 3.5;
               if (var28) {
                  var29 += Math.min(6.0, var25.getDestroySpeed(this.level, var6) * 1.5);
               }

               if (var17) {
                  var29 += Math.min(6.0, var26.getDestroySpeed(this.level, var23) * 1.5);
               }

               this.tryAddNode(var1, var2, var3, var4, MovementType.TUNNEL, var6.immutable(), var8 + var29 + this.getHazardCost(var2, var3, var4, var6, 0), var5);
            }
         }
      }
   }

   private void expandDigDown(PathNode var1, int var2, int var3, int var4, NpcPathSearch.Sink var5, MutableBlockPos var6) {
      if (this.allowTunneling && this.allowBreaking) {
         var6.set(var2, var3 - 1, var4);
         if (this.level.isLoaded(var6)) {
            BlockState var7 = this.level.getBlockState(var6);
            if (!var7.isAir() && this.isBreakable(var7, var6)) {
               BlockPos var8 = var6.immutable();
               if (HazardCostEvaluator.isSafeToExcavate(this.level, var8)) {
                  boolean var9 = this.canStandOn(var2, var3 - 2, var4, var6) || this.isInWater(var2, var3 - 2, var4, var6);
                  if (var9) {
                     var6.set(var2, var3 - 1, var4);
                     double var10 = var7.getDestroySpeed(this.level, var6);
                     double var12 = 2.0 + Math.min(6.0, var10 * 1.5) + this.getHazardCost(var2, var3 - 1, var4, var6, 1);
                     // 1.13.12: cavar junto a agua inunda el pozo -> muy caro (Baritone directamente lo evita)
                     for (Direction wd : Direction.Plane.HORIZONTAL) {
                        if (this.isInWater(var2 + wd.getStepX(), var3 - 1, var4 + wd.getStepZ(), this.waterProbe)) {
                           var12 += 8.0;
                           break;
                        }
                     }
                     this.tryAddNode(var1, var2, var3 - 1, var4, MovementType.DIG_DOWN, var8, var12, var5);
                  }
               }
            }
         }
      }
   }

   private void expandTunnelDown(PathNode var1, int var2, int var3, int var4, NpcPathSearch.Sink var5, MutableBlockPos var6) {
      if (!this.allowTunneling || !this.allowBreaking) {
         return;
      }
      int downY = var3 - 1;
      if (downY < this.level.getMinBuildHeight() + 1) {
         return;
      }

      if (!this.canStandOn(var2, downY - 1, var4, var6)) {
         return;
      }

      var6.set(var2, downY, var4);
      if (!this.level.isLoaded(var6)) return;
      BlockState s1 = this.level.getBlockState(var6);

      var6.set(var2, downY + 1, var4);
      if (!this.level.isLoaded(var6)) return;
      BlockState s2 = this.level.getBlockState(var6);

      var6.set(var2, downY + 2, var4);
      if (!this.level.isLoaded(var6)) return;
      BlockState s3 = this.level.getBlockState(var6);

      boolean b1Solid = !s1.getCollisionShape(this.level, var6.set(var2, downY, var4)).isEmpty();
      boolean b2Solid = !s2.getCollisionShape(this.level, var6.set(var2, downY + 1, var4)).isEmpty();
      boolean b3Solid = !s3.getCollisionShape(this.level, var6.set(var2, downY + 2, var4)).isEmpty();

      if (!b1Solid && !b2Solid && !b3Solid) {
         return;
      }

      var6.set(var2, downY, var4);
      if (b1Solid && (!this.isBreakable(s1, var6) || !HazardCostEvaluator.isSafeToExcavate(this.level, var6))) {
         return;
      }

      var6.set(var2, downY + 1, var4);
      if (b2Solid && (!this.isBreakable(s2, var6) || !HazardCostEvaluator.isSafeToExcavate(this.level, var6))) {
         return;
      }

      var6.set(var2, downY + 2, var4);
      if (b3Solid && (!this.isBreakable(s3, var6) || !HazardCostEvaluator.isSafeToExcavate(this.level, var6))) {
         return;
      }

      double cost = 1.0 + 0.35 + this.getHazardCost(var2, downY, var4, var6, 1);
      if (b1Solid) cost += 1.5 + Math.min(6.0, s1.getDestroySpeed(this.level, var6.set(var2, downY, var4)) * 1.5);
      if (b2Solid) cost += 1.5 + Math.min(6.0, s2.getDestroySpeed(this.level, var6.set(var2, downY + 1, var4)) * 1.5);
      if (b3Solid) cost += 1.5 + Math.min(6.0, s3.getDestroySpeed(this.level, var6.set(var2, downY + 2, var4)) * 1.5);

      this.tryAddNode(var1, var2, downY, var4, MovementType.TUNNEL_DOWN, new BlockPos(var2, downY, var4), cost, var5);
   }

   private final MutableBlockPos waterProbe = new MutableBlockPos();

   private void tryAddNode(PathNode var1, int var2, int var3, int var4, MovementType var5, BlockPos var6, double var7, NpcPathSearch.Sink var9) {
      BlockPos var10 = new BlockPos(var2, var3, var4);
      if (var3 >= this.level.getMinBuildHeight() && var3 + 1 < this.level.getMaxBuildHeight() && this.level.getWorldBorder().isWithinBounds(var10)) {
         long var11 = NpcPathSearch.key(var2, var3, var4);
         if (this.avoidCost != null) {
            var7 += this.avoidCost.applyAsDouble(var11);
         } else if (this.avoid != null && this.avoid.test(var11)) {
            var7 += 30.0;
         }

         // 1.13.12: el NPC nada mal y bajar por pozas lo dejaba flotando/atascado. El agua cuesta más que cavar
         // en seco (como Baritone): +3 con los pies en agua, +6 extra si también la cabeza está sumergida.
         if (this.isInWater(var2, var3, var4, this.waterProbe)) {
            var7 += 3.0;
            if (this.isInWater(var2, var3 + 1, var4, this.waterProbe)) {
               var7 += 6.0;
            }
         }

         if (var7 >= 1000000.0) {
            return;
         }

         var9.offer(var2, var3, var4, var7, new InternalBaritonePlanner.MovementData(var5, var6));
      }
   }

   private boolean isPassable(int var1, int var2, int var3, MutableBlockPos var4) {
      var4.set(var1, var2, var3);
      if (!this.level.isLoaded(var4)) {
         return false;
      } else {
         BlockState var5 = this.level.getBlockState(var4);
         if (var5.isAir()) {
            return true;
         } else {
            return var5.getCollisionShape(this.level, var4).isEmpty() ? true : isOpenableDoorway(var5);
         }
      }
   }

   private boolean isClearStrict(int var1, int var2, int var3, MutableBlockPos var4) {
      var4.set(var1, var2, var3);
      if (!this.level.isLoaded(var4)) {
         return false;
      }

      BlockState var5 = this.level.getBlockState(var4);
      return var5.isAir() ? true : var5.getCollisionShape(this.level, var4).isEmpty();
   }

   private double doorPenalty(int var1, int var2, int var3, MutableBlockPos var4) {
      var4.set(var1, var2, var3);
      if (!this.level.isLoaded(var4)) {
         return 0.0;
      }

      BlockState var5 = this.level.getBlockState(var4);
      return !var5.isAir() && !var5.getCollisionShape(this.level, var4).isEmpty() && isOpenableDoorway(var5) ? 1.5 : 0.0;
   }

   public static boolean isOpenableDoorway(BlockState var0) {
      if (var0 == null) {
         return false;
      }

      Block var1 = var0.getBlock();
      return var1 instanceof FenceGateBlock ? true : var1 instanceof DoorBlock && DoorBlock.isWoodenDoor(var0);
   }

   public static boolean isTallCollision(BlockState var0, ServerLevel var1, BlockPos var2) {
      Block var3 = var0.getBlock();
      if (!(var3 instanceof FenceBlock) && !(var3 instanceof WallBlock) && !(var3 instanceof FenceGateBlock)) {
         try {
            VoxelShape var4 = var0.getCollisionShape(var1, var2);
            return !var4.isEmpty() && var4.max(Axis.Y) > 1.05;
         } catch (Exception var5) {
            return false;
         }
      } else {
         return true;
      }
   }

   private boolean canStandOn(int var1, int var2, int var3, MutableBlockPos var4) {
      var4.set(var1, var2, var3);
      if (!this.level.isLoaded(var4)) {
         return false;
      } else {
         BlockState var5 = this.level.getBlockState(var4);
         if (var5.isAir()) {
            return false;
         } else if (isTallCollision(var5, this.level, var4)) {
            return false;
         } else {
            return var5.getBlock() instanceof DoorBlock ? false : var5.isFaceSturdy(this.level, var4, Direction.UP) || var5.isSolid();
         }
      }
   }

   private boolean isInWater(int var1, int var2, int var3, MutableBlockPos var4) {
      var4.set(var1, var2, var3);
      return !this.level.isLoaded(var4) ? false : this.level.getFluidState(var4).is(Fluids.WATER);
   }

   private boolean canPlaceBlockAt(MutableBlockPos var1) {
      if (!this.level.isLoaded(var1)) {
         return false;
      }

      BlockState var2 = this.level.getBlockState(var1);
      return var2.isAir() || var2.canBeReplaced();
   }

   private boolean isBreakable(BlockState var1, BlockPos var2) {
      if (var1.liquid() || !var1.getFluidState().isEmpty()) {
         return false;
      }

      if (var1.isAir()) {
         return true;
      }

      if (var1.is(Blocks.BEDROCK) || var1.is(Blocks.BARRIER)) {
         return false;
      }

      if (var1.getBlock() instanceof DoorBlock || var1.getBlock() instanceof FenceGateBlock) {
         return false;
      }

      if (!this.allowBreaking) {
         return false;
      }

      long var3 = NpcPathSearch.key(var2.getX(), var2.getY(), var2.getZ());
      Boolean var5 = this.breakableCache.get(var3);
      if (var5 == null) {
         var5 = TerrainPolicy.mayBreakImplicitly(this.level, var2, var1, null);
         this.breakableCache.put(var3, var5);
      }

      return var5;
   }

   private double getHazardCost(int var1, int var2, int var3, MutableBlockPos var4, int var5) {
      var4.set(var1, var2, var3);
      return !this.level.isLoaded(var4) ? 1000000.0 : HazardCostEvaluator.getHazardPenalty(this.level, var4, var5);
   }

   private record MovementData(MovementType type, BlockPos action) {
   }
}
